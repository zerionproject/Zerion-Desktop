package chat.zerion.desktop.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import chat.zerion.desktop.ZerionDesktopComponent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

import org.zerionproject.app.api.messaging.VoiceSignal
import org.zerionproject.app.api.messaging.VoiceSignalHeader
import org.zerionproject.app.api.messaging.VoiceSignalType
import org.zerionproject.app.conversation.voice.VoiceCallConnectionHandler
import org.zerionproject.app.conversation.voice.VoiceCallConnectionManager
import org.zerionproject.app.conversation.voice.VoiceCallCrypto
import org.zerionproject.core.api.contact.ContactId
import org.zerionproject.core.api.crypto.SecretKey
import org.zerionproject.core.api.plugin.duplex.DuplexTransportConnection
import org.zerionproject.core.api.sync.GroupId

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

/**
 * Desktop voice-call engine. This is the JVM re-implementation of Android's
 * VoiceCallService orchestration: it drives the call state machine, captures and
 * plays audio through [javax.sound.sampled], and moves 20 ms / 640-byte PCM
 * frames over the media socket.
 *
 * Everything on the wire is byte-identical to Android: the same signalling
 * records (built by the shared [VoiceCallConnectionManager]/factory), the same
 * ephemeral key derivation and AES-256-GCM framing (the shared
 * [VoiceCallCrypto]), and the same media-stream framing (sync marker, per-frame
 * length/seq/timestamp/CRC header). So a desktop user can call an Android user
 * and back.
 *
 * Signalling rides the normal encrypted messaging connection; media rides a
 * dedicated Tor v3 stream on onion port 80 established by the connection
 * manager. Keys are zeroized and lines closed on teardown.
 */
class VoiceCallEngine(private val component: ZerionDesktopComponent) {

	enum class Phase { IDLE, OUTGOING, INCOMING, CONNECTING, CONNECTED, ENDED }

	var phase by mutableStateOf(Phase.IDLE)
		private set
	var peerName by mutableStateOf("")
		private set
	var muted by mutableStateOf(false)
		private set
	var durationSeconds by mutableStateOf(0)
		private set
	var statusText by mutableStateOf<String?>(null)
		private set
	var minimized by mutableStateOf(false)
		private set

	fun applyMinimized(value: Boolean) {
		minimized = value
	}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
	private var executor: ExecutorService? = null
	private val ringer = Ringer()

	private val micFrames = AtomicLong(0)
	private val framesSent = AtomicLong(0)
	private val framesRecv = AtomicLong(0)
	private val framesDecoded = AtomicLong(0)
	private val crcFails = AtomicLong(0)
	private val decryptFails = AtomicLong(0)
	private val speakerWrites = AtomicLong(0)

	private var callId: String? = null
	private var peerContactId: ContactId? = null
	private var alice = false
	private var voiceCallKey: SecretKey? = null
	private var localEphemeral: ByteArray? = null
	private var remoteEphemeral: ByteArray? = null
	@Volatile
	private var audioKeys: VoiceCallCrypto.AudioKeys? = null
	@Volatile
	private var connection: DuplexTransportConnection? = null
	@Volatile
	private var shuttingDown = false
	@Volatile
	private var recording = false
	private var callStartTime = 0L
	private var durationJob: Job? = null

	private val sendSequence = AtomicLong(0)
	private val mediaWriteLock = Any()

	private var micLine: TargetDataLine? = null
	private var speakerLine: SourceDataLine? = null
	private var micRate = AudioDevice.WIRE_RATE
	private var micChannels = 1
	private var spkRate = AudioDevice.WIRE_RATE
	private var spkChannels = 1
	private var capResampler: LinearResampler? = null
	private var playResampler: LinearResampler? = null

	private val jitterBuffer = ByteArray(JITTER_CAPACITY)
	private val jbLock = Any()
	private var jbReadPos = 0
	private var jbWritePos = 0
	private var jbBuffered = 0
	@Volatile
	private var playoutStarted = false

	val inCall: Boolean
		get() = phase != Phase.IDLE && phase != Phase.ENDED


	fun startCall(contactId: ContactId, name: String) {
		if (inCall) return
		val id = UUID.randomUUID().toString().replace("-", "")
		val key = component.voiceCallCrypto().generateVoiceCallKey()
		val eph = component.voiceCallCrypto().generateEphemeralSecret()
		resetState()
		callId = id
		peerContactId = contactId
		voiceCallKey = key
		localEphemeral = eph
		alice = true
		peerName = name
		muted = false
		minimized = false
		phase = Phase.OUTGOING
		statusText = "Calling…"
		ringer.startRingback()
		val keyHex = component.voiceCallCrypto().encodeVoiceCallKey(key)
		val payload = keyHex + "|" + eph.toHex()
		sendSignal(contactId, id, VoiceSignalType.CALL_OFFER, payload, null)
		scheduleSetupTimeout(35_000)
	}


	fun onSignal(header: VoiceSignalHeader, contactId: ContactId, name: String) {
		val type = header.signalType
		val incomingCallId = header.callId
		when (type) {
			VoiceSignalType.CALL_OFFER, VoiceSignalType.VIDEO_OFFER -> {
				if (inCall) {
					if (incomingCallId != callId) {
						sendSignal(contactId, incomingCallId,
								VoiceSignalType.CALL_BUSY, null, null)
					}
					return
				}
				val payload = header.payload ?: return
				val parts = payload.split("|")
				val keyHex = parts.getOrNull(0) ?: return
				val ephHex = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
				val remoteEph = if (ephHex != null) {
					runCatching { ephHex.hexToBytes() }.getOrElse {
						return
					}
				} else null
				val decodedKey = try {
					component.voiceCallCrypto().decodeVoiceCallKey(keyHex)
				} catch (e: Exception) {
					return
				}
				resetState()
				callId = incomingCallId
				peerContactId = contactId
				alice = false
				voiceCallKey = decodedKey
				remoteEphemeral = remoteEph
				peerName = name
				muted = false
				minimized = false
				phase = Phase.INCOMING
				statusText = "Incoming call"
				ringer.startRingtone()
			}
			VoiceSignalType.CALL_ANSWER -> {
				if (callId != incomingCallId || phase != Phase.OUTGOING) return
				onCallAnswer(header.payload)
			}
			VoiceSignalType.CALL_REJECT -> {
				if (callId != incomingCallId) return
				finish("Call declined")
			}
			VoiceSignalType.CALL_BUSY -> {
				if (callId != incomingCallId) return
				finish("Contact is busy")
			}
			VoiceSignalType.CALL_END -> {
				if (callId != incomingCallId) return
				finish("Call ended")
			}
			else -> {
			}
		}
	}

	fun acceptCall() {
		if (phase != Phase.INCOMING) return
		val id = callId ?: return
		val contactId = peerContactId ?: return
		val key = voiceCallKey ?: return
		ringer.stop()
		val eph = component.voiceCallCrypto().generateEphemeralSecret()
		localEphemeral = eph
		phase = Phase.CONNECTING
		statusText = "Connecting…"
		deriveKeys()
		scope.launch {
			val info = withContext(Dispatchers.IO) {
				try {
					component.voiceCallConnectionManager().createIncomingEndpoint(
							id, key, false, handler)
				} catch (e: Exception) {
					null
				}
			}
			if (info == null) {
				finish("Couldn't set up the call")
				return@launch
			}
			val payload = info.onionAddress + ":" + info.port + "|" + eph.toHex()
			sendSignal(contactId, id, VoiceSignalType.CALL_ANSWER, payload, null)
			scheduleSetupTimeout(45_000)
		}
	}

	fun declineCall() {
		val id = callId
		val contactId = peerContactId
		if (id != null && contactId != null) {
			sendSignal(contactId, id, VoiceSignalType.CALL_REJECT, null, null)
		}
		finish(null)
	}


	fun toggleMute() {
		if (phase == Phase.CONNECTED) muted = !muted
	}

	fun hangUp() {
		val id = callId
		val contactId = peerContactId
		if (phase == Phase.CONNECTED) sendMediaBye()
		if (id != null && contactId != null &&
				(phase == Phase.CONNECTED || phase == Phase.OUTGOING ||
						phase == Phase.CONNECTING)) {
			val duration = if (callStartTime > 0)
				System.currentTimeMillis() - callStartTime else null
			sendSignal(contactId, id, VoiceSignalType.CALL_END, null, duration)
		}
		finish(null)
	}

	fun shutdown() {
		finish(null, sync = true)
		ringer.stop()
		scope.cancel()
	}

	fun rejectIncomingOffer(callId: String, contactId: ContactId) {
		sendSignal(contactId, callId, VoiceSignalType.CALL_REJECT,
				"calls_disabled", null)
	}


	private fun onCallAnswer(payload: String?) {
		if (payload == null) {
			finish("Call failed")
			return
		}
		val connParts = payload.split("|")
		val connection = connParts.getOrNull(0) ?: return
		val ephHex = connParts.getOrNull(1)?.takeIf { it.isNotEmpty() }
		remoteEphemeral = if (ephHex != null) {
			runCatching { ephHex.hexToBytes() }.getOrElse {
				finish("Call setup failed")
				return
			}
		} else null
		val idx = connection.lastIndexOf(":")
		if (idx <= 0) {
			finish("Call failed")
			return
		}
		val onion = connection.substring(0, idx)
		cancelSetupTimeout()
		phase = Phase.CONNECTING
		statusText = "Connecting…"
		scheduleSetupTimeout(90_000)
		deriveKeys()
		val id = callId ?: return
		val key = voiceCallKey ?: return
		scope.launch {
			val conn = withContext(Dispatchers.IO) {
				try {
					component.voiceCallConnectionManager()
							.connectToRemote(id, onion, key, true)
				} catch (e: Exception) {
					null
				}
			}
			if (conn == null) {
				finish("Couldn't connect the call")
				return@launch
			}
			this@VoiceCallEngine.connection = conn
			onConnected()
		}
	}

	private val handler = VoiceCallConnectionHandler { conn ->
		scope.launch {
			if (phase != Phase.CONNECTING && phase != Phase.INCOMING) return@launch
			connection = conn
			onConnected()
		}
	}

	private fun deriveKeys() {
		val key = voiceCallKey ?: return
		val local = localEphemeral
		val remote = remoteEphemeral
		audioKeys = try {
			if (local != null && remote != null) {
				component.voiceCallCrypto().deriveEphemeralAudioKeys(
						key, local, remote, alice)
			} else {
				component.voiceCallCrypto().deriveAudioKeys(key, alice)
			}
		} catch (e: Exception) {
			null
		}
	}

	private fun onConnected() {
		cancelSetupTimeout()
		ringer.stop()
		if (audioKeys == null) {
			finish("Call setup failed")
			return
		}
		phase = Phase.CONNECTED
		statusText = null
		callStartTime = System.currentTimeMillis()
		durationSeconds = 0
		listOf(micFrames, framesSent, framesRecv, framesDecoded, crcFails,
				decryptFails, speakerWrites).forEach { it.set(0) }
		startMedia()
		durationJob = scope.launch {
			var lastRecv = 0L
			var stalledTicks = 0
			while (phase == Phase.CONNECTED) {
				durationSeconds =
						((System.currentTimeMillis() - callStartTime) / 1000).toInt()
				delay(1000)
				val recv = framesRecv.get()
				if (recv > lastRecv) { lastRecv = recv; stalledTicks = 0 }
				else stalledTicks++
				if (stalledTicks >= 30 && phase == Phase.CONNECTED) {
					onMediaError()
					break
				}
				if (phase == Phase.CONNECTED && durationSeconds % 2 == 0) {
				}
			}
		}
	}

	private fun startMedia() {
		val mic = AudioDevice.openMic()
		val spk = AudioDevice.openSpeaker()
		if (mic == null || spk == null) {
			try { mic?.line?.close() } catch (e: Exception) {}
			try { spk?.line?.close() } catch (e: Exception) {}
			finish("No microphone or speaker available")
			return
		}
		micLine = mic.line
		micRate = mic.rate
		micChannels = mic.channels
		speakerLine = spk.line
		spkRate = spk.rate
		spkChannels = spk.channels
		capResampler = LinearResampler(micRate, AudioDevice.WIRE_RATE)
		playResampler = LinearResampler(AudioDevice.WIRE_RATE, spkRate)

		recording = true
		shuttingDown = false
		playoutStarted = false
		synchronized(jbLock) {
			jbReadPos = 0; jbWritePos = 0; jbBuffered = 0
		}
		val exec = Executors.newCachedThreadPool { r ->
			Thread(r, "zerion-voice").apply { isDaemon = true }
		}
		executor = exec
		exec.execute { recordLoop() }
		exec.execute { receiveLoop() }
		exec.execute { playoutLoop() }
		exec.execute { heartbeatLoop() }
	}

	private fun recordLoop() {
		val conn = connection ?: return
		val mic = micLine ?: return
		val resampler = capResampler ?: return
		val crypto = component.voiceCallCrypto()
		try {
			val out = DataOutputStream(BufferedOutputStream(
					conn.getWriter().getOutputStream(), FRAME_SIZE * 2))
			out.writeInt(SYNC_MARKER)
			out.flush()
			out.writeInt(READY_MARKER)
			out.flush()

			val chunkBytes = micRate / 50 * micChannels * 2
			val readBuffer = ByteArray(chunkBytes)
			var pending = ShortArray(2048)
			var pendingLen = 0
			var readOffset = 0
			while (!shuttingDown && recording && connection != null) {
				val read = mic.read(readBuffer, readOffset, chunkBytes - readOffset)
				if (read <= 0) continue
				readOffset += read
				if (readOffset < chunkBytes) continue
				readOffset = 0

				val mono = AudioDevice.bytesToMono(readBuffer, chunkBytes,
						micChannels)
				val res = resampler.process(mono)
				if (pendingLen + res.size > pending.size) {
					pending = pending.copyOf(
							maxOf(pending.size * 2, pendingLen + res.size))
				}
				System.arraycopy(res, 0, pending, pendingLen, res.size)
				pendingLen += res.size

				while (pendingLen >= WIRE_SAMPLES) {
					val frame = pending.copyOfRange(0, WIRE_SAMPLES)
					System.arraycopy(pending, WIRE_SAMPLES, pending, 0,
							pendingLen - WIRE_SAMPLES)
					pendingLen -= WIRE_SAMPLES
					micFrames.incrementAndGet()

					val keys = audioKeys
					if (keys?.txKey == null) continue
					val pcm = if (muted) ByteArray(FRAME_SIZE)
							else AudioDevice.shortsToBytesLE(frame)
					val encrypted = crypto.encryptAudioFrame(pcm, keys.txKey)
					val crc = CRC32()
					crc.update(encrypted, 0, encrypted.size)
					synchronized(mediaWriteLock) {
						out.writeInt(encrypted.size)
						out.writeLong(sendSequence.getAndIncrement())
						out.writeLong(0L)
						out.writeLong(crc.value)
						out.write(encrypted)
						out.flush()
					}
					framesSent.incrementAndGet()
				}
			}
		} catch (e: Exception) {
			if (!shuttingDown) onMediaError()
		}
	}

	private fun receiveLoop() {
		val conn = connection ?: return
		val crypto = component.voiceCallCrypto()
		try {
			val input = DataInputStream(BufferedInputStream(
					conn.getReader().getInputStream(), MAX_FRAME * 3))
			input.readInt()

			var lastSequence = -1L
			var lastReceive = System.currentTimeMillis()
			while (!shuttingDown && recording && connection != null) {
				if (System.currentTimeMillis() - lastReceive > READ_TIMEOUT_MS) {
					if (!shuttingDown && phase == Phase.CONNECTED) onMediaError()
					break
				}
				val encSize = input.readInt()
				when {
					encSize == READY_MARKER || encSize == HEARTBEAT_MARKER -> {
						lastReceive = System.currentTimeMillis()
						continue
					}
					encSize == BYE_MARKER -> {
						scope.launch { finish("Call ended") }
						return
					}
					encSize < 0 -> continue
				}
				val sequence = input.readLong()
				input.readLong()
				val checksum = input.readLong()
				lastReceive = System.currentTimeMillis()

				if (encSize > MAX_FRAME) {
					if (!shuttingDown && phase == Phase.CONNECTED) onMediaError()
					return
				}

				val frame = ByteArray(encSize)
				input.readFully(frame)
				framesRecv.incrementAndGet()

				val crc = CRC32()
				crc.update(frame, 0, encSize)
				if (crc.value != checksum) {
					crcFails.incrementAndGet()
					continue
				}

				val keys = audioKeys
				if (keys?.rxKey == null) continue
				val pcm = try {
					crypto.decryptAudioFrame(frame, keys.rxKey)
				} catch (e: RuntimeException) {
					decryptFails.incrementAndGet()
					continue
				}
				if (lastSequence >= 0 && sequence <= lastSequence) continue
				lastSequence = sequence
				if (pcm.isEmpty()) continue
				framesDecoded.incrementAndGet()

				synchronized(jbLock) {
					writeToJitter(pcm)
					if (!playoutStarted && jbBuffered >= PREBUFFER_FLOOR) {
						playoutStarted = true
					}
				}
			}
		} catch (e: Exception) {
			if (!shuttingDown && phase == Phase.CONNECTED) onMediaError()
		}
	}

	private fun playoutLoop() {
		val playBuffer = ByteArray(FRAME_SIZE)
		val resampler = playResampler
		while (!shuttingDown && recording) {
			if (!playoutStarted) {
				try { Thread.sleep(5) } catch (e: InterruptedException) { return }
				continue
			}
			var hasFrame: Boolean
			synchronized(jbLock) {
				hasFrame = jbBuffered >= FRAME_SIZE
				if (hasFrame) {
					for (i in 0 until FRAME_SIZE) {
						playBuffer[i] = jitterBuffer[jbReadPos]
						jbReadPos = (jbReadPos + 1) % JITTER_CAPACITY
					}
					jbBuffered -= FRAME_SIZE
				}
			}
			if (hasFrame) {
				try {
					val mono = AudioDevice.bytesToShortsLE(playBuffer, FRAME_SIZE)
					val res = resampler?.process(mono) ?: mono
					val bytes = AudioDevice.monoToDevice(res, spkChannels)
					speakerLine?.write(bytes, 0, bytes.size)
					speakerWrites.incrementAndGet()
				} catch (e: Exception) {
					break
				}
			} else {
				try { Thread.sleep(5) } catch (e: InterruptedException) { return }
			}
		}
	}

	private fun heartbeatLoop() {
		try {
			while (!shuttingDown && recording && connection != null) {
				Thread.sleep(10_000)
				val conn = connection ?: break
				try {
					synchronized(mediaWriteLock) {
						val out = DataOutputStream(conn.getWriter().getOutputStream())
						out.writeInt(HEARTBEAT_MARKER)
						out.flush()
					}
				} catch (e: Exception) {
				}
			}
		} catch (e: InterruptedException) {
		}
	}

	private fun writeToJitter(data: ByteArray) {
		for (b in data) {
			jitterBuffer[jbWritePos] = b
			jbWritePos = (jbWritePos + 1) % JITTER_CAPACITY
		}
		val newBuffered = jbBuffered + data.size
		if (newBuffered > JITTER_CAPACITY) {
			jbReadPos = (jbReadPos + (newBuffered - JITTER_CAPACITY)) %
					JITTER_CAPACITY
			jbBuffered = JITTER_CAPACITY
		} else {
			jbBuffered = newBuffered
		}
	}

	private fun sendMediaBye() {
		val conn = connection ?: return
		try {
			synchronized(mediaWriteLock) {
				val out = DataOutputStream(conn.getWriter().getOutputStream())
				out.writeInt(BYE_MARKER)
				out.flush()
			}
		} catch (e: Exception) {
		}
	}

	private fun onMediaError() {
		scope.launch { finish("Connection lost") }
	}

	private fun sendSignal(contactId: ContactId, callId: String,
			type: VoiceSignalType, payload: String?, durationMs: Long?) {
		scope.launch {
			withContext(Dispatchers.IO) {
				try {
					val mm = component.messagingManager()
					val group = component.db().transactionWithResult<
							GroupId, Exception>(false) { txn ->
						mm.getConversationId(txn, contactId)
					}
					val ts = System.currentTimeMillis()
					val factory = component.voiceSignalFactory()
					val signal: VoiceSignal = when (type) {
						VoiceSignalType.CALL_OFFER ->
							factory.createCallOffer(group, ts, callId, payload!!)
						VoiceSignalType.CALL_ANSWER ->
							factory.createCallAnswer(group, ts, callId, payload!!)
						VoiceSignalType.CALL_REJECT ->
							factory.createCallReject(group, ts, callId, payload)
						VoiceSignalType.CALL_END ->
							factory.createCallEnd(group, ts, callId, durationMs)
						VoiceSignalType.CALL_BUSY ->
							factory.createCallBusy(group, ts, callId)
						else -> return@withContext
					}
					mm.addLocalVoiceSignal(signal)
				} catch (e: Exception) {
				}
			}
		}
	}

	private var setupTimeoutJob: Job? = null

	private fun scheduleSetupTimeout(timeoutMs: Long) {
		cancelSetupTimeout()
		setupTimeoutJob = scope.launch {
			delay(timeoutMs)
			if (phase == Phase.OUTGOING || phase == Phase.CONNECTING ||
					phase == Phase.INCOMING) {
				finish("No answer")
			}
		}
	}

	private fun cancelSetupTimeout() {
		setupTimeoutJob?.cancel()
		setupTimeoutJob = null
	}

	private fun finish(reason: String?, sync: Boolean = false) {
		cancelSetupTimeout()
		ringer.stop()
		val wasActive = inCall
		shuttingDown = true
		recording = false
		val id = callId
		val hadError = reason != null && phase != Phase.INCOMING

		durationJob?.cancel(); durationJob = null

		val exec = executor; executor = null
		val mic = micLine; micLine = null
		val spk = speakerLine; speakerLine = null
		val conn = connection; connection = null
		val keys = audioKeys; audioKeys = null
		val vk = voiceCallKey
		val localEph = localEphemeral
		val remoteEph = remoteEphemeral
		capResampler = null
		playResampler = null

		val release = Runnable {
			exec?.shutdownNow()
			try {
				exec?.awaitTermination(600, java.util.concurrent.TimeUnit.MILLISECONDS)
			} catch (e: Exception) {}
			try { mic?.stop(); mic?.close() } catch (e: Exception) {}
			try { spk?.stop(); spk?.close() } catch (e: Exception) {}
			try {
				if (id != null) {
					component.voiceCallConnectionManager().closeEndpoint(id)
					component.voiceCallConnectionManager()
							.closeEndpoint("$id-video")
				}
			} catch (e: Exception) {}
			if (conn != null) {
				try { conn.getReader().dispose(true, true) } catch (e: Exception) {}
				try { conn.getWriter().dispose(true) } catch (e: Exception) {}
			}
			keys?.destroy()
			vk?.clear()
			localEph?.fill(0)
			remoteEph?.fill(0)
			synchronized(jbLock) { jitterBuffer.fill(0) }
		}
		if (sync) {
			release.run()
		} else {
			Thread(release).apply {
				isDaemon = true; name = "zerion-voice-teardown"; start()
			}
		}

		if (wasActive || hadError) {
			statusText = reason
			phase = Phase.ENDED
			scope.launch {
				delay(if (reason != null) 1500 else 300)
				if (phase == Phase.ENDED) {
					phase = Phase.IDLE
					statusText = null
					peerName = ""
				}
			}
		} else {
			phase = Phase.IDLE
			statusText = null
			peerName = ""
		}
		clearCallRefs()
	}

	private fun clearCallRefs() {
		callId = null
		peerContactId = null
		voiceCallKey = null
		localEphemeral = null
		remoteEphemeral = null
		callStartTime = 0
		sendSequence.set(0)
		playoutStarted = false
		minimized = false
	}

	private fun resetState() {
		shuttingDown = false
		recording = false
		muted = false
		durationSeconds = 0
		sendSequence.set(0)
		callStartTime = 0
	}

	private fun ByteArray.toHex(): String =
			joinToString("") { "%02x".format(it) }

	private fun String.hexToBytes(): ByteArray {
		require(length % 2 == 0) { "bad hex" }
		return ByteArray(length / 2) {
			((Character.digit(this[it * 2], 16) shl 4) +
					Character.digit(this[it * 2 + 1], 16)).toByte()
		}
	}

	private companion object {
		const val SAMPLE_RATE = 16000
		const val FRAME_MS = 20
		const val FRAME_SIZE = (SAMPLE_RATE / 1000) * FRAME_MS * 2
		const val WIRE_SAMPLES = FRAME_SIZE / 2
		const val JITTER_CAPACITY = SAMPLE_RATE * 2 * 2
		const val PREBUFFER_FLOOR = ((SAMPLE_RATE * 2) / 1000) * 60
		const val MAX_FRAME = 8192 + 64
		const val READ_TIMEOUT_MS = 30_000L

		const val SYNC_MARKER = 0x5A455249
		const val READY_MARKER = -1
		const val HEARTBEAT_MARKER = -2
		const val BYE_MARKER = -3
	}
}

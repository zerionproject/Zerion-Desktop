package chat.zerion.desktop.ui.voice

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

/*
 * Capture and playback for 1:1 voice memos. Capture is PCM signed 16-bit
 * 8 kHz mono (little-endian), matching the reference recorder before mu-law
 * compression. Playback decodes the container's G.711 mu-law bytes directly
 * through a mu-law audio line.
 */
class VoiceRecorder(sampleRate: Int = VoiceMemo.SAMPLE_RATE) {

	private var line: TargetDataLine? = null
	private var thread: Thread? = null
	@Volatile private var recording = false
	private val buffer = ByteArrayOutputStream()
	private var startMs = 0L

	private val captureFormat = AudioFormat(
			sampleRate.toFloat(), 16, 1, true, false)

	fun isRecording(): Boolean = recording

	fun start(): Boolean {
		if (recording) return true
		val info = DataLine.Info(TargetDataLine::class.java, captureFormat)
		if (!AudioSystem.isLineSupported(info)) return false
		return try {
			val l = AudioSystem.getLine(info) as TargetDataLine
			l.open(captureFormat)
			l.start()
			line = l
			buffer.reset()
			recording = true
			startMs = System.currentTimeMillis()
			val t = Thread {
				val buf = ByteArray(4096)
				while (recording) {
					val n = l.read(buf, 0, buf.size)
					if (n > 0) synchronized(buffer) { buffer.write(buf, 0, n) }
				}
			}
			t.isDaemon = true
			t.start()
			thread = t
			true
		} catch (e: Exception) {
			cleanup()
			false
		}
	}

	fun stop(nowMs: Long): Pair<ByteArray, Int>? {
		if (!recording) return null
		recording = false
		val durationMs = (nowMs - startMs).toInt().coerceIn(0, VoiceMemo.MAX_DURATION_MS)
		try {
			thread?.join(500)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
		}
		cleanup()
		val pcm = synchronized(buffer) { buffer.toByteArray() }
		if (pcm.isEmpty()) return null
		return pcm to durationMs
	}

	fun cancel() {
		recording = false
		try {
			thread?.join(500)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
		}
		cleanup()
		synchronized(buffer) { buffer.reset() }
	}

	private fun cleanup() {
		try {
			line?.stop(); line?.close()
		} catch (e: Exception) {
		}
		line = null
		thread = null
	}
}

class VoicePlayer {

	private var line: SourceDataLine? = null
	private var thread: Thread? = null
	@Volatile private var playing = false

	private val muLawFormat = AudioFormat(
			AudioFormat.Encoding.ULAW, VoiceMemo.SAMPLE_RATE.toFloat(),
			8, 1, 1, VoiceMemo.SAMPLE_RATE.toFloat(), false)

	fun isPlaying(): Boolean = playing

	fun play(muLaw: ByteArray, onDone: () -> Unit): Boolean {
		stop()
		val info = DataLine.Info(SourceDataLine::class.java, muLawFormat)
		if (!AudioSystem.isLineSupported(info)) return false
		return try {
			val l = AudioSystem.getLine(info) as SourceDataLine
			l.open(muLawFormat)
			l.start()
			line = l
			playing = true
			val t = Thread {
				try {
					var off = 0
					val block = 2048
					while (playing && off < muLaw.size) {
						val n = minOf(block, muLaw.size - off)
						l.write(muLaw, off, n)
						off += n
					}
					if (playing) l.drain()
				} catch (e: Exception) {
				} finally {
					stop()
					onDone()
				}
			}
			t.isDaemon = true
			t.start()
			thread = t
			true
		} catch (e: Exception) {
			cleanup()
			false
		}
	}

	fun playPcm(pcm16le: ByteArray, rate: Int, onDone: () -> Unit): Boolean {
		stop()
		val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
		val info = DataLine.Info(SourceDataLine::class.java, format)
		if (!AudioSystem.isLineSupported(info)) return false
		return try {
			val l = AudioSystem.getLine(info) as SourceDataLine
			l.open(format)
			l.start()
			line = l
			playing = true
			val t = Thread {
				try {
					var off = 0
					val block = 4096
					while (playing && off < pcm16le.size) {
						val n = minOf(block, pcm16le.size - off)
						l.write(pcm16le, off, n)
						off += n
					}
					if (playing) l.drain()
				} catch (e: Exception) {
				} finally {
					stop()
					onDone()
				}
			}
			t.isDaemon = true
			t.start()
			thread = t
			true
		} catch (e: Exception) {
			cleanup()
			false
		}
	}

	fun stop() {
		playing = false
		cleanup()
	}

	private fun cleanup() {
		try {
			line?.stop(); line?.flush(); line?.close()
		} catch (e: Exception) {
		}
		line = null
	}
}

package chat.zerion.desktop.ui.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Wire-compatible port of the Android 1:1 voice-memo format. A memo is a normal
 * private text message carrying a base64 binary container:
 *   [VOICE:<durationMs>:<base64>]                       single message
 *   [VMP:1:<memoId 16hex>:<seq>:<total>:<durationMs>:<slice>]   chunked
 * Container layout (version 1):
 *   byte version | byte[12] iv | byte[80] wrappedKey | int32 chunkCount |
 *   chunkCount * { int32 len | byte[len] ciphertext | byte[16] tag } |
 *   int32 durationMs | byte[16] globalMAC
 * wrappedKey = raw 32-byte wrap key ++ AES-GCM(sessionKey) under the wrap key
 * (the wrap key travels in the clear; memo confidentiality is provided by the
 * transport layer, matching the reference implementation). Audio is G.711
 * mu-law at 8 kHz mono. AAD for every chunk = version(1) ++ groupId(32).
 */
object VoiceMemo {

	const val CHUNK_THRESHOLD_CHARS = 24_000
	private const val SLICE_CHARS = 16_000
	private const val MAX_PARTS = 24
	private const val MEMO_ID_BYTES = 8

	private const val FORMAT_VERSION: Byte = 1
	private const val IV_LENGTH = 12
	private const val WRAPPED_KEY_LENGTH = 80
	private const val TAG_LENGTH = 16
	private const val GCM_TAG_BITS = 128
	private const val MAX_CHUNK_SIZE = 8_192
	private const val CHUNK_SIZE = 4096

	const val SAMPLE_RATE = 8000
	const val MAX_DURATION_MS = 60_000

	private val random = SecureRandom()
	private val VOICE_PATTERN =
			Pattern.compile("\\[VOICE:(\\d+):([A-Za-z0-9+/=]+)\\]")
	private val PART_PATTERN = Pattern.compile(
			"\\[VMP:1:([0-9a-f]{16}):(\\d+):(\\d+):(\\d+):([A-Za-z0-9+/=]*)\\]")

	fun isVoiceMessage(text: String?): Boolean =
			text != null && text.startsWith("[VOICE:") && text.endsWith("]")

	fun isPart(text: String?): Boolean =
			text != null && text.startsWith("[VMP:1:") && text.endsWith("]")

	data class Parsed(val durationMs: Int, val payload: ByteArray)

	fun parseVoice(text: String?): Parsed? {
		if (!isVoiceMessage(text)) return null
		val m = VOICE_PATTERN.matcher(text!!)
		if (!m.matches()) return null
		return try {
			val duration = m.group(1).toInt()
			val payload = java.util.Base64.getDecoder()
					.decode(padBase64(m.group(2)))
			Parsed(duration, payload)
		} catch (e: Exception) {
			null
		}
	}

	data class Part(val memoId: String, val seq: Int, val total: Int,
			val durationMs: Int, val slice: String)

	fun parsePart(text: String?): Part? {
		if (!isPart(text)) return null
		val m = PART_PATTERN.matcher(text!!)
		if (!m.matches()) return null
		return try {
			val seq = m.group(2).toInt()
			val total = m.group(3).toInt()
			if (total < 1 || total > MAX_PARTS || seq < 0 || seq >= total) return null
			val slice = m.group(5)
			if (slice.length > SLICE_CHARS) return null
			Part(m.group(1), seq, total, m.group(4).toInt(), slice)
		} catch (e: Exception) {
			null
		}
	}

	private fun padBase64(s: String): String {
		val rem = s.length % 4
		return if (rem == 0) s else s + "=".repeat(4 - rem)
	}

	private fun newMemoId(): String {
		val b = ByteArray(MEMO_ID_BYTES)
		random.nextBytes(b)
		val sb = StringBuilder(MEMO_ID_BYTES * 2)
		for (x in b) {
			sb.append(Character.forDigit((x.toInt() shr 4) and 0xF, 16))
			sb.append(Character.forDigit(x.toInt() and 0xF, 16))
		}
		return sb.toString()
	}

	private const val MAX_MULAW = 249_900

	fun buildMessages(pcm16le: ByteArray, durationMs: Int, groupId: ByteArray):
			List<String> {
		val maxPcm = MAX_MULAW * 2
		val pcm = if (pcm16le.size > maxPcm) pcm16le.copyOf(maxPcm) else pcm16le
		val duration = minOf(durationMs, (pcm.size / 16)).coerceIn(0, MAX_DURATION_MS)
		val payload = buildPayload(pcm, duration, groupId)
		val body = java.util.Base64.getEncoder().withoutPadding()
				.encodeToString(payload)
		val single = "[VOICE:$duration:$body]"
		if (single.length <= CHUNK_THRESHOLD_CHARS) return listOf(single)
		val total = ((body.length + SLICE_CHARS - 1) / SLICE_CHARS).coerceAtLeast(1)
		require(total <= MAX_PARTS) { "Voice message has too many parts" }
		val memoId = newMemoId()
		val parts = ArrayList<String>(total)
		for (seq in 0 until total) {
			val start = seq * SLICE_CHARS
			val end = minOf(start + SLICE_CHARS, body.length)
			parts.add("[VMP:1:$memoId:$seq:$total:$duration:" +
					body.substring(start, end) + "]")
		}
		return parts
	}

	fun reassemble(durationMs: Int, orderedSlices: List<String>): String {
		val body = StringBuilder()
		for (s in orderedSlices) body.append(s)
		return "[VOICE:$durationMs:$body]"
	}

	private fun buildPayload(pcm16le: ByteArray, durationMs: Int,
			groupId: ByteArray): ByteArray {
		require(groupId.size == 32) { "groupId must be 32 bytes" }
		val muLaw = pcmToMuLaw(pcm16le)

		val keyGen = KeyGenerator.getInstance("AES")
		keyGen.init(256, random)
		val sessionKey = keyGen.generateKey()
		val sessionRaw = sessionKey.encoded
		val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
		val aad = aad(groupId)

		val wrapRaw = ByteArray(32).also { random.nextBytes(it) }
		val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
		wrapCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrapRaw, "AES"),
				GCMParameterSpec(GCM_TAG_BITS, iv))
		val wrappedSession = wrapCipher.doFinal(sessionRaw)
		val wrappedKey = ByteArray(WRAPPED_KEY_LENGTH)
		System.arraycopy(wrapRaw, 0, wrappedKey, 0, 32)
		System.arraycopy(wrappedSession, 0, wrappedKey, 32, wrappedSession.size)

		val chunks = ArrayList<ByteArray>()
		val tags = ArrayList<ByteArray>()
		var seq = 0
		var offset = 0
		while (offset < muLaw.size) {
			val len = minOf(CHUNK_SIZE, muLaw.size - offset)
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionRaw, "AES"),
					GCMParameterSpec(GCM_TAG_BITS, counterIv(iv, seq)))
			cipher.updateAAD(aad)
			val out = cipher.doFinal(muLaw, offset, len)
			val ct = out.copyOf(out.size - TAG_LENGTH)
			val tag = out.copyOfRange(out.size - TAG_LENGTH, out.size)
			require(ct.size in 1..MAX_CHUNK_SIZE)
			chunks.add(ct)
			tags.add(tag)
			seq++
			offset += len
		}
		val globalMac = globalMac(sessionRaw, iv, aad, seq, chunks.size, durationMs)

		val baos = ByteArrayOutputStream()
		baos.write(FORMAT_VERSION.toInt())
		baos.write(iv)
		baos.write(wrappedKey)
		baos.write(intBytes(chunks.size))
		for (i in chunks.indices) {
			baos.write(intBytes(chunks[i].size))
			baos.write(chunks[i])
			baos.write(tags[i])
		}
		baos.write(intBytes(durationMs))
		baos.write(globalMac)

		java.util.Arrays.fill(sessionRaw, 0)
		java.util.Arrays.fill(wrapRaw, 0)
		return baos.toByteArray()
	}

	fun decodeToMuLaw(payload: ByteArray, groupId: ByteArray): ByteArray {
		require(groupId.size == 32) { "groupId must be 32 bytes" }
		val minLen = 1 + IV_LENGTH + WRAPPED_KEY_LENGTH + 4 + 4 + TAG_LENGTH
		require(payload.size >= minLen) { "payload too short" }
		var off = 0
		require(payload[off++] == FORMAT_VERSION) { "bad version" }
		val iv = payload.copyOfRange(off, off + IV_LENGTH); off += IV_LENGTH
		val wrappedKey = payload.copyOfRange(off, off + WRAPPED_KEY_LENGTH)
		off += WRAPPED_KEY_LENGTH
		val chunkCount = intAt(payload, off); off += 4
		require(chunkCount in 1..4096) { "bad chunk count" }

		val sessionRaw = unwrapSession(wrappedKey, iv)
		val aad = aad(groupId)
		val plain = ByteArrayOutputStream()
		var seq = 0
		for (i in 0 until chunkCount) {
			val len = intAt(payload, off); off += 4
			require(len in 1..MAX_CHUNK_SIZE) { "bad chunk len" }
			val ct = payload.copyOfRange(off, off + len); off += len
			val tag = payload.copyOfRange(off, off + TAG_LENGTH); off += TAG_LENGTH
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionRaw, "AES"),
					GCMParameterSpec(GCM_TAG_BITS, counterIv(iv, seq)))
			cipher.updateAAD(aad)
			plain.write(cipher.doFinal(ct + tag))
			seq++
		}
		val durationMs = intAt(payload, off); off += 4
		val globalMac = payload.copyOfRange(off, off + TAG_LENGTH)
		verifyGlobalMac(sessionRaw, iv, aad, seq, chunkCount, durationMs, globalMac)
		java.util.Arrays.fill(sessionRaw, 0)
		return plain.toByteArray()
	}

	private fun unwrapSession(wrappedKey: ByteArray, iv: ByteArray): ByteArray {
		require(wrappedKey.size == WRAPPED_KEY_LENGTH) { "bad wrapped key" }
		val wrapRaw = wrappedKey.copyOfRange(0, 32)
		val wrappedSession = wrappedKey.copyOfRange(32, WRAPPED_KEY_LENGTH)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrapRaw, "AES"),
				GCMParameterSpec(GCM_TAG_BITS, iv))
		return cipher.doFinal(wrappedSession)
	}

	private fun globalMac(sessionRaw: ByteArray, iv: ByteArray, aad: ByteArray,
			counter: Int, chunkCount: Int, durationMs: Int): ByteArray {
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionRaw, "AES"),
				GCMParameterSpec(GCM_TAG_BITS, counterIv(iv, counter)))
		cipher.updateAAD(aad)
		val meta = ByteBuffer.allocate(8)
		meta.putInt(chunkCount); meta.putInt(durationMs)
		cipher.updateAAD(meta.array())
		val out = cipher.doFinal(ByteArray(0))
		return out.copyOf(TAG_LENGTH)
	}

	private fun verifyGlobalMac(sessionRaw: ByteArray, iv: ByteArray,
			aad: ByteArray, counter: Int, chunkCount: Int, durationMs: Int,
			mac: ByteArray) {
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionRaw, "AES"),
				GCMParameterSpec(GCM_TAG_BITS, counterIv(iv, counter)))
		cipher.updateAAD(aad)
		val meta = ByteBuffer.allocate(8)
		meta.putInt(chunkCount); meta.putInt(durationMs)
		cipher.updateAAD(meta.array())
		cipher.doFinal(mac)
	}

	private fun aad(groupId: ByteArray): ByteArray {
		val b = ByteArray(1 + 32)
		b[0] = FORMAT_VERSION
		System.arraycopy(groupId, 0, b, 1, 32)
		return b
	}

	private fun counterIv(iv: ByteArray, counter: Int): ByteArray {
		val out = iv.copyOf(IV_LENGTH)
		out[IV_LENGTH - 4] = (iv[IV_LENGTH - 4].toInt() xor (counter ushr 24)).toByte()
		out[IV_LENGTH - 3] = (iv[IV_LENGTH - 3].toInt() xor (counter ushr 16)).toByte()
		out[IV_LENGTH - 2] = (iv[IV_LENGTH - 2].toInt() xor (counter ushr 8)).toByte()
		out[IV_LENGTH - 1] = (iv[IV_LENGTH - 1].toInt() xor counter).toByte()
		return out
	}

	private fun intBytes(v: Int): ByteArray =
			ByteBuffer.allocate(4).putInt(v).array()

	private fun intAt(b: ByteArray, off: Int): Int =
			ByteBuffer.wrap(b, off, 4).int

	private fun pcmToMuLaw(pcm: ByteArray): ByteArray {
		val n = pcm.size / 2
		val out = ByteArray(n)
		for (i in 0 until n) {
			val sample = (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)
			out[i] = linearToMuLaw(sample.toShort())
		}
		return out
	}

	private fun linearToMuLaw(input: Short): Byte {
		val mulawMax = 0x1FFF
		val bias = 33
		var sample = input.toInt()
		val sign = (sample shr 8) and 0x80
		if (sign != 0) sample = -sample
		if (sample > mulawMax) sample = mulawMax
		sample += bias
		var exponent = 7
		var expMask = 0x4000
		while ((sample and expMask) == 0 && exponent > 0) {
			exponent--; expMask = expMask shr 1
		}
		val mantissa = (sample shr (exponent + 3)) and 0x0F
		return (sign or (exponent shl 4) or mantissa).inv().toByte()
	}
}

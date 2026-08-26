package chat.zerion.desktop.ui.voice

import org.concentus.OpusApplication
import org.concentus.OpusDecoder
import org.concentus.OpusEncoder
import org.gagravarr.ogg.OggFile
import org.gagravarr.opus.OpusAudioData
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusInfo
import org.gagravarr.opus.OpusTags
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/*
 * Group voice memos use the same wire representation as the mobile client:
 * an Ogg-encapsulated Opus stream (16 kHz mono, ~24 kbps) carried in a GroupTr
 * body of type VOICE. The Opus codec is the vendored pure-Java Concentus; the
 * Ogg container is gagravarr's vorbis-java. No native code is involved.
 */
object GroupVoice {

	const val SAMPLE_RATE = 16000
	private const val CHANNELS = 1
	private const val FRAME_SIZE = 320
	private const val BITRATE = 24000
	private const val DECODE_RATE = 16000
	private const val MAX_DECODE_SAMPLES = 1920
	const val MAX_DURATION_MS = 5 * 60 * 1000
	private const val MAX_OGG_BYTES = 4 * 1024 * 1024

	fun encode(pcm16le: ByteArray): ByteArray {
		val enc = OpusEncoder(SAMPLE_RATE, CHANNELS,
				OpusApplication.OPUS_APPLICATION_VOIP)
		enc.setBitrate(BITRATE)
		enc.setComplexity(5)
		enc.setUseVBR(true)

		val info = OpusInfo()
		info.setNumChannels(CHANNELS)
		info.setSampleRate(SAMPLE_RATE.toLong())
		info.setPreSkip(enc.getLookahead() * (48000 / SAMPLE_RATE))
		val bos = ByteArrayOutputStream()
		val out = OpusFile(bos, info, OpusTags())

		val totalSamples = pcm16le.size / 2
		val pcm = ShortArray(totalSamples)
		for (i in 0 until totalSamples) {
			pcm[i] = ((pcm16le[i * 2].toInt() and 0xFF) or
					(pcm16le[i * 2 + 1].toInt() shl 8)).toShort()
		}
		val buf = ByteArray(4000)
		var off = 0
		while (off + FRAME_SIZE <= totalSamples) {
			val len = enc.encode(pcm, off, FRAME_SIZE, buf, 0, buf.size)
			out.writeAudioData(OpusAudioData(buf.copyOf(len)))
			off += FRAME_SIZE
		}
		if (off < totalSamples) {
			val frame = ShortArray(FRAME_SIZE)
			System.arraycopy(pcm, off, frame, 0, totalSamples - off)
			val len = enc.encode(frame, 0, FRAME_SIZE, buf, 0, buf.size)
			out.writeAudioData(OpusAudioData(buf.copyOf(len)))
		}
		out.close()
		return bos.toByteArray()
	}

	fun decode(oggOpus: ByteArray): ByteArray {
		require(oggOpus.size <= MAX_OGG_BYTES) { "voice message too large" }
		val file = OpusFile(OggFile(ByteArrayInputStream(oggOpus)))
		val dec = OpusDecoder(DECODE_RATE, CHANNELS)
		val bos = ByteArrayOutputStream()
		val pcm = ShortArray(MAX_DECODE_SAMPLES)
		val maxSamples = (MAX_DURATION_MS.toLong() * DECODE_RATE / 1000).toInt()
		var written = 0
		var packet: OpusAudioData? = file.getNextAudioPacket()
		while (packet != null) {
			val data = packet.getData()
			if (data != null && data.isNotEmpty()) {
				val n = dec.decode(data, 0, data.size, pcm, 0,
						MAX_DECODE_SAMPLES, false)
				for (i in 0 until n) {
					if (written >= maxSamples) return bos.toByteArray()
					val s = pcm[i].toInt()
					bos.write(s and 0xFF)
					bos.write((s shr 8) and 0xFF)
					written++
				}
			}
			packet = file.getNextAudioPacket()
		}
		return bos.toByteArray()
	}

	fun durationMsForPcm(pcm16le: ByteArray): Int =
			((pcm16le.size / 2).toLong() * 1000 / SAMPLE_RATE)
					.toInt().coerceIn(0, MAX_DURATION_MS)
}

package chat.zerion.desktop.ui.voice

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

/**
 * Opens capture/playback lines at a format the sound hardware actually
 * supports, and converts between that device format and the call's fixed
 * 16 kHz mono s16le wire format. Many devices (especially on Windows) refuse a
 * 16 kHz mono line outright, so we negotiate a real rate (48 kHz / 44.1 kHz,
 * mono or stereo) and resample in software - the bytes on the Tor stream stay
 * exactly what Android expects.
 */
internal object AudioDevice {

	const val WIRE_RATE = 16000

	private val RATES = intArrayOf(16000, 48000, 44100, 32000, 22050, 8000)
	private val CHANNELS = intArrayOf(1, 2)

	@Volatile var preferredInput: String? = null
	@Volatile var preferredOutput: String? = null

	class Opened<L>(val line: L, val rate: Int, val channels: Int)

	fun inputDevices(): List<String> = devicesFor(TargetDataLine::class.java)
	fun outputDevices(): List<String> = devicesFor(SourceDataLine::class.java)

	private fun devicesFor(lineClass: Class<*>): List<String> {
		val out = LinkedHashSet<String>()
		for (info in AudioSystem.getMixerInfo()) {
			try {
				val mixer = AudioSystem.getMixer(info)
				val supports = mixer.targetLineInfo.any {
					(it as? DataLine.Info)?.lineClass == lineClass
				} || mixer.sourceLineInfo.any {
					(it as? DataLine.Info)?.lineClass == lineClass
				}
				if (supports) out.add(info.name)
			} catch (e: Exception) {
			}
		}
		return out.toList()
	}

	private fun mixerNamed(name: String?): javax.sound.sampled.Mixer.Info? {
		if (name == null) return null
		return AudioSystem.getMixerInfo().firstOrNull { it.name == name }
	}

	fun openMic(): Opened<TargetDataLine>? {
		val preferred = mixerNamed(preferredInput)
		for (mixer in (if (preferred != null) listOf(preferred, null) else listOf<javax.sound.sampled.Mixer.Info?>(null))) {
			for (ch in CHANNELS) for (rate in RATES) {
				val fmt = AudioFormat(rate.toFloat(), 16, ch, true, false)
				try {
					val info = DataLine.Info(TargetDataLine::class.java, fmt)
					val line = (if (mixer != null)
						AudioSystem.getMixer(mixer).getLine(info)
					else AudioSystem.getLine(info)) as TargetDataLine
					line.open(fmt, rate / 50 * ch * 2 * 8)
					line.start()
					return Opened(line, rate, ch)
				} catch (e: Exception) {
				}
			}
		}
		return null
	}

	fun openSpeaker(): Opened<SourceDataLine>? {
		val preferred = mixerNamed(preferredOutput)
		for (mixer in (if (preferred != null) listOf(preferred, null) else listOf<javax.sound.sampled.Mixer.Info?>(null))) {
			for (ch in CHANNELS) for (rate in RATES) {
				val fmt = AudioFormat(rate.toFloat(), 16, ch, true, false)
				try {
					val info = DataLine.Info(SourceDataLine::class.java, fmt)
					val line = (if (mixer != null)
						AudioSystem.getMixer(mixer).getLine(info)
					else AudioSystem.getLine(info)) as SourceDataLine
					line.open(fmt, rate / 50 * ch * 2 * 16)
					line.start()
					return Opened(line, rate, ch)
				} catch (e: Exception) {
				}
			}
		}
		return null
	}

	/** Parses interleaved 16-bit LE PCM into mono samples (averaging channels). */
	fun bytesToMono(bytes: ByteArray, len: Int, channels: Int): ShortArray {
		val frames = len / 2 / channels
		val out = ShortArray(frames)
		var b = 0
		for (i in 0 until frames) {
			var sum = 0
			for (c in 0 until channels) {
				val lo = bytes[b].toInt() and 0xff
				val hi = bytes[b + 1].toInt()
				sum += (hi shl 8) or lo
				b += 2
			}
			out[i] = (sum / channels).toShort()
		}
		return out
	}

	/** Packs mono samples into interleaved 16-bit LE PCM for the device. */
	fun monoToDevice(mono: ShortArray, channels: Int): ByteArray {
		val out = ByteArray(mono.size * channels * 2)
		var b = 0
		for (s in mono) {
			val lo = (s.toInt() and 0xff).toByte()
			val hi = ((s.toInt() shr 8) and 0xff).toByte()
			for (c in 0 until channels) {
				out[b] = lo; out[b + 1] = hi; b += 2
			}
		}
		return out
	}

	fun shortsToBytesLE(s: ShortArray): ByteArray {
		val out = ByteArray(s.size * 2)
		var b = 0
		for (v in s) {
			out[b] = (v.toInt() and 0xff).toByte()
			out[b + 1] = ((v.toInt() shr 8) and 0xff).toByte()
			b += 2
		}
		return out
	}

	fun bytesToShortsLE(bytes: ByteArray, len: Int): ShortArray {
		val out = ShortArray(len / 2)
		var b = 0
		for (i in out.indices) {
			val lo = bytes[b].toInt() and 0xff
			val hi = bytes[b + 1].toInt()
			out[i] = ((hi shl 8) or lo).toShort()
			b += 2
		}
		return out
	}
}

/**
 * Streaming linear-interpolation resampler between two sample rates. Keeps
 * fractional phase across calls so consecutive buffers join seamlessly. If the
 * rates are equal it is a pass-through.
 */
internal class LinearResampler(private val inRate: Int, private val outRate: Int) {

	private val step = inRate.toDouble() / outRate
	private var frac = 0.0
	private var last = 0.0
	private var primed = false

	fun process(input: ShortArray): ShortArray {
		if (inRate == outRate) return input
		if (input.isEmpty()) return ShortArray(0)
		val out = ShortArray(((input.size.toLong() * outRate / inRate) + 4).toInt())
		var n = 0
		for (i in input.indices) {
			val cur = input[i].toDouble()
			if (!primed) {
				last = cur; primed = true; continue
			}
			while (frac < 1.0 && n < out.size) {
				val v = last + (cur - last) * frac
				out[n++] = v.toInt().coerceIn(-32768, 32767).toShort()
				frac += step
			}
			frac -= 1.0
			last = cur
		}
		return if (n == out.size) out else out.copyOf(n)
	}
}

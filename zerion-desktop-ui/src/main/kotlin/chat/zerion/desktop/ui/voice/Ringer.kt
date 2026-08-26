package chat.zerion.desktop.ui.voice

import java.io.ByteArrayOutputStream

import javax.sound.sampled.SourceDataLine

/**
 * Generates and plays call ring tones on the default output device. Tones are
 * synthesised in memory (no bundled audio files): a slow single-tone ringback
 * for outgoing calls and a warbling ring for incoming calls, each looped until
 * [stop]. The output line is negotiated through [AudioDevice] (many devices
 * refuse a 16 kHz line), and the tone is generated at whatever rate/channel
 * count the device actually opened. Always stopped before the call's own audio
 * device is opened, so the two never contend for the line.
 */
internal class Ringer {

	@Volatile
	private var line: SourceDataLine? = null
	@Volatile
	private var thread: Thread? = null

	fun startRingback() = start { rate -> ringbackCycle(rate) }

	fun startRingtone() = start { rate -> ringtoneCycle(rate) }

	@Synchronized
	fun stop() {
		thread?.interrupt()
		thread = null
		val l = line
		line = null
		if (l != null) {
			try { l.stop() } catch (e: Exception) {}
			try { l.flush() } catch (e: Exception) {}
			try { l.close() } catch (e: Exception) {}
		}
	}

	@Synchronized
	private fun start(cycleFor: (Int) -> ShortArray) {
		stop()
		val opened = AudioDevice.openSpeaker() ?: return
		val l = opened.line
		line = l
		val cycle = AudioDevice.monoToDevice(cycleFor(opened.rate),
				opened.channels)
		thread = Thread {
			try {
				while (!Thread.currentThread().isInterrupted && line === l) {
					var off = 0
					while (off < cycle.size && line === l) {
						val n = l.write(cycle, off, cycle.size - off)
						if (n <= 0) break
						off += n
					}
				}
			} catch (e: Exception) {
			}
		}.apply { isDaemon = true; name = "zerion-ringer"; start() }
	}

	private fun ringbackCycle(rate: Int): ShortArray {
		val out = ByteArrayOutputStream()
		append(out, tone(425.0, 1000, rate))
		append(out, silence(3000, rate))
		return toShorts(out.toByteArray())
	}

	private fun ringtoneCycle(rate: Int): ShortArray {
		val out = ByteArrayOutputStream()
		append(out, tone(480.0, 380, rate))
		append(out, silence(90, rate))
		append(out, tone(440.0, 380, rate))
		append(out, silence(1650, rate))
		return toShorts(out.toByteArray())
	}

	private fun append(out: ByteArrayOutputStream, s: ShortArray) {
		out.write(AudioDevice.shortsToBytesLE(s))
	}

	private fun toShorts(bytes: ByteArray): ShortArray =
			AudioDevice.bytesToShortsLE(bytes, bytes.size)

	private fun tone(freqHz: Double, ms: Int, rate: Int,
			amp: Double = 0.22): ShortArray {
		val n = rate * ms / 1000
		val out = ShortArray(n)
		val ramp = minOf(rate / 200, n / 2).coerceAtLeast(1)
		for (i in 0 until n) {
			var v = Math.sin(2.0 * Math.PI * freqHz * i / rate) * amp
			if (i < ramp) v *= i.toDouble() / ramp
			else if (i >= n - ramp) v *= (n - i).toDouble() / ramp
			out[i] = (v * Short.MAX_VALUE).toInt().toShort()
		}
		return out
	}

	private fun silence(ms: Int, rate: Int): ShortArray =
			ShortArray(rate * ms / 1000)
}

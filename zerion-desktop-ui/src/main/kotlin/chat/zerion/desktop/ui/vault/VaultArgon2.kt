package chat.zerion.desktop.ui.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Argon2id password KDF for the vault, using BouncyCastle with the same
 * parameter presets as the Android Vault (256 MB / 3 / 1 by default; a 128 MB /
 * 2 / 1 low-memory profile on constrained heaps). The chosen parameters are
 * stored in the vault header so unlock always uses the parameters the vault was
 * created with, not the runtime picker.
 */
internal object VaultArgon2 {

	data class Params(val memoryKb: Int, val iterations: Int, val parallelism: Int)

	val DEFAULT = Params(256 * 1024, 3, 1)
	val LOW_MEMORY = Params(128 * 1024, 2, 1)

	fun choose(): Params {
		val maxHeap = Runtime.getRuntime().maxMemory()
		return if (maxHeap > 512L * 1024 * 1024) DEFAULT else LOW_MEMORY
	}

	fun deriveKey(password: CharArray, salt: ByteArray, params: Params,
			length: Int = VaultCrypto.KEY_LEN): ByteArray {
		val pwBytes = toUtf8(password)
		try {
			val p = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
					.withMemoryAsKB(params.memoryKb)
					.withIterations(params.iterations)
					.withParallelism(params.parallelism)
					.withSalt(salt)
					.build()
			val generator = Argon2BytesGenerator()
			generator.init(p)
			val out = ByteArray(length)
			generator.generateBytes(pwBytes, out)
			return out
		} finally {
			Arrays.fill(pwBytes, 0)
		}
	}

	private fun toUtf8(chars: CharArray): ByteArray {
		val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
		val out = ByteArray(buffer.remaining())
		buffer.get(out)
		if (buffer.hasArray()) Arrays.fill(buffer.array(), 0)
		return out
	}
}

package chat.zerion.desktop.ui

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Per-chat lock verifier. Stores only a random salt + a PBKDF2 hash of the
 * chat password (never the password itself, never plaintext). Verification
 * re-derives and compares in constant time. The stored value lives in the
 * app's encrypted settings, so a chat lock adds an access gate on top of the
 * already-encrypted database - it does not weaken it.
 */
internal object ChatLock {

	private const val ITERATIONS = 120_000
	private const val KEY_BITS = 256
	private const val SALT_BYTES = 16

	fun derive(password: CharArray): String {
		val salt = ByteArray(SALT_BYTES)
		SecureRandom().nextBytes(salt)
		val hash = pbkdf2(password, salt)
		val enc = Base64.getEncoder()
		return enc.encodeToString(salt) + ":" + enc.encodeToString(hash)
	}

	fun verify(password: CharArray, stored: String): Boolean {
		val parts = stored.split(":")
		if (parts.size != 2) return false
		return try {
			val dec = Base64.getDecoder()
			val salt = dec.decode(parts[0])
			val expected = dec.decode(parts[1])
			val actual = pbkdf2(password, salt)
			MessageDigest.isEqual(actual, expected)
		} catch (e: Exception) {
			false
		}
	}

	private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
		val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
		try {
			val factory =
					SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
			return factory.generateSecret(spec).encoded
		} finally {
			spec.clearPassword()
		}
	}
}

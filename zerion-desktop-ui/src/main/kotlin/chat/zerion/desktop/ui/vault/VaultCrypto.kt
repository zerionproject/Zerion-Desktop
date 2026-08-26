package chat.zerion.desktop.ui.vault

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vault symmetric crypto, matching the Android Vault's scheme so the on-disk
 * format is the same shape: AES-256-GCM with a random 96-bit nonce prepended to
 * every ciphertext, HKDF-SHA256 for the master-key schedule, a constant-time
 * HMAC password check, and envelope encryption (per-item keys wrapped by the
 * vault master key). All bulk content is encrypted with a fresh per-item key,
 * never directly with the master key.
 */
internal object VaultCrypto {

	const val KEY_LEN = 32
	const val NONCE_LEN = 12
	private const val TAG_BITS = 128
	private const val VERIFICATION_LABEL = "VAULT_PASSWORD_VERIFICATION"
	private const val MASTER_INFO = "vault master"

	private val random = SecureRandom()

	/** nonce(12) || ciphertext+tag, serialized as int nonceLen | nonce | ct. */
	class EncryptedData(val nonce: ByteArray, val ciphertext: ByteArray) {
		fun toBytes(): ByteArray {
			val out = ByteArray(4 + nonce.size + ciphertext.size)
			val bb = ByteBuffer.wrap(out)
			bb.putInt(nonce.size); bb.put(nonce); bb.put(ciphertext)
			return out
		}

		companion object {
			fun fromBytes(data: ByteArray): EncryptedData {
				val bb = ByteBuffer.wrap(data)
				val nl = bb.int
				require(nl == NONCE_LEN) { "bad nonce length" }
				val nonce = ByteArray(nl); bb.get(nonce)
				val ct = ByteArray(bb.remaining()); bb.get(ct)
				return EncryptedData(nonce, ct)
			}
		}
	}

	fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

	fun generateKey(): ByteArray = randomBytes(KEY_LEN)

	fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray): EncryptedData {
		require(key.size == KEY_LEN) { "key must be 32 bytes" }
		val nonce = randomBytes(NONCE_LEN)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"),
				GCMParameterSpec(TAG_BITS, nonce))
		if (aad.isNotEmpty()) cipher.updateAAD(aad)
		return EncryptedData(nonce, cipher.doFinal(plaintext))
	}

	fun decrypt(data: EncryptedData, key: ByteArray, aad: ByteArray): ByteArray {
		require(key.size == KEY_LEN) { "key must be 32 bytes" }
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
				GCMParameterSpec(TAG_BITS, data.nonce))
		if (aad.isNotEmpty()) cipher.updateAAD(aad)
		return cipher.doFinal(data.ciphertext)
	}

	fun xor(a: ByteArray, b: ByteArray): ByteArray {
		require(a.size == b.size) { "xor length mismatch" }
		return ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }
	}

	/** RFC 5869 HKDF-SHA256 (extract + expand). */
	fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
		val mac = Mac.getInstance("HmacSHA256")
		val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
		mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
		val prk = mac.doFinal(ikm)
		mac.init(SecretKeySpec(prk, "HmacSHA256"))
		val infoBytes = info.toByteArray(Charsets.UTF_8)
		val out = ByteArray(len)
		var t = ByteArray(0)
		var pos = 0
		var counter = 1
		while (pos < len) {
			mac.update(t)
			mac.update(infoBytes)
			mac.update(counter.toByte())
			t = mac.doFinal()
			val n = minOf(t.size, len - pos)
			System.arraycopy(t, 0, out, pos, n)
			pos += n
			counter++
		}
		java.util.Arrays.fill(prk, 0)
		return out
	}

	/** master = HKDF(passwordKey XOR randomSecret, salt, "vault master"). */
	fun deriveMasterKey(passwordKey: ByteArray, randomSecret: ByteArray,
			salt: ByteArray): ByteArray {
		val combined = xor(passwordKey, randomSecret)
		try {
			return hkdfSha256(combined, salt, MASTER_INFO, KEY_LEN)
		} finally {
			java.util.Arrays.fill(combined, 0)
		}
	}

	fun deriveNoteKey(secret: CharArray, salt: ByteArray): ByteArray {
		val spec = javax.crypto.spec.PBEKeySpec(secret, salt, 210_000, 256)
		try {
			return javax.crypto.SecretKeyFactory
					.getInstance("PBKDF2WithHmacSHA256")
					.generateSecret(spec).encoded
		} finally {
			spec.clearPassword()
		}
	}

	fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(key, "HmacSHA256"))
		return mac.doFinal(data)
	}

	fun passwordVerificationMac(masterKey: ByteArray): ByteArray {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
		return mac.doFinal(VERIFICATION_LABEL.toByteArray(Charsets.UTF_8))
	}

	fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
			MessageDigest.isEqual(a, b)
}

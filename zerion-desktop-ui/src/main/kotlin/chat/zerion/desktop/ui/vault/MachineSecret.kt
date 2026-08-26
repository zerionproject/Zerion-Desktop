package chat.zerion.desktop.ui.vault

/**
 * Machine-binding for the vault's random secret, so the encrypted vault is
 * useless if the files are copied to another machine even with the password —
 * the desktop equivalent of Android's hardware-Keystore-wrapped secret.
 *
 * On Windows this uses DPAPI (`CryptProtectData` in CurrentUser scope, via
 * jna-platform's Crypt32Util), which ties the wrapped blob to the logged-in
 * Windows user's credentials. On other platforms it is currently unavailable
 * (macOS Keychain / Linux secret service are follow-ups); the vault manager
 * falls back to a password-only key schedule there and records that no
 * machine-binding was applied.
 */
internal object MachineSecret {

	private val isWindows =
			System.getProperty("os.name", "").lowercase().contains("win")

	fun isAvailable(): Boolean {
		if (!isWindows) return false
		return try {
			val probe = byteArrayOf(0x5A, 0x56)
			val back = unwrap(wrap(probe))
			back.contentEquals(probe)
		} catch (e: Throwable) {
			false
		}
	}

	/** DPAPI-wrap (CurrentUser). Throws if unavailable — callers guard with [isAvailable]. */
	fun wrap(secret: ByteArray): ByteArray =
			com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(secret)

	fun unwrap(blob: ByteArray): ByteArray =
			com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(blob)
}

package chat.zerion.desktop.ui.wallet

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.crypto.MnemonicUtils

import java.security.SecureRandom

/**
 * BIP39 mnemonic + BIP32/44 hierarchical-deterministic key derivation for the
 * wallet, built on web3j's audited crypto (not hand-rolled). The mnemonic is
 * the single root secret for every chain and lives only inside the encrypted
 * Vault; keys are derived on demand and never persisted separately.
 *
 * Ethereum uses the standard path m/44'/60'/0'/0/index — index 0 is the fixed
 * primary account, and higher indices give a fresh receive address per
 * transaction.
 */
internal object WalletKeys {

	/** 256-bit entropy => 24-word mnemonic (strongest standard BIP39 length). */
	fun generateMnemonic(): String {
		val entropy = ByteArray(32)
		SecureRandom().nextBytes(entropy)
		return MnemonicUtils.generateMnemonic(entropy)
	}

	fun isValidMnemonic(mnemonic: String): Boolean =
			MnemonicUtils.validateMnemonic(mnemonic.trim())

	fun isValidEthAddress(address: String): Boolean {
		val a = address.trim()
		if (!a.matches(Regex("^0x[0-9a-fA-F]{40}$"))) return false
		val hex = a.substring(2)
		val mixedCase = hex.any { it in 'a'..'f' } && hex.any { it in 'A'..'F' }
		return if (mixedCase) {
			runCatching { Keys.toChecksumAddress(a) == a }.getOrDefault(false)
		} else true
	}

	private fun master(mnemonic: String): Bip32ECKeyPair {
		val seed = MnemonicUtils.generateSeed(mnemonic.trim(), null)
		return Bip32ECKeyPair.generateKeyPair(seed)
	}

	fun ethCredentials(mnemonic: String, account: Int, index: Int): Credentials {
		val path = intArrayOf(
				44 or Bip32ECKeyPair.HARDENED_BIT,
				60 or Bip32ECKeyPair.HARDENED_BIT,
				account or Bip32ECKeyPair.HARDENED_BIT,
				0,
				index)
		val derived = Bip32ECKeyPair.deriveKeyPair(master(mnemonic), path)
		return Credentials.create(derived)
	}

	fun ethAddress(mnemonic: String, account: Int, index: Int): String =
			ethCredentials(mnemonic, account, index).address
}

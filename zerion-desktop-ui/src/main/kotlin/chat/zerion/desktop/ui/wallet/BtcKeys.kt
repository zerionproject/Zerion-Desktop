package chat.zerion.desktop.ui.wallet

import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder

/**
 * Bitcoin HD derivation from the shared BIP39 seed, using bitcoinj's audited
 * crypto. Native SegWit (BIP84, bc1… addresses): m/84'/0'/account'/change/index.
 * Index 0 of the external chain is the fixed primary address; higher indices are
 * fresh receive addresses. Provides the Electrum scripthash for each address.
 */
internal object BtcKeys {

	val params: NetworkParameters = MainNetParams.get()

	private fun key(mnemonic: String, account: Int, change: Int,
			index: Int): DeterministicKey {
		val seed = MnemonicCode.toSeed(mnemonic.trim().split(Regex("\\s+")), "")
		var k = HDKeyDerivation.createMasterPrivateKey(seed)
		val path = listOf(
				ChildNumber(84, true),
				ChildNumber(0, true),
				ChildNumber(account, true),
				ChildNumber(change, false),
				ChildNumber(index, false))
		for (n in path) k = HDKeyDerivation.deriveChildKey(k, n)
		return k
	}

	fun receiveKey(mnemonic: String, account: Int, index: Int): DeterministicKey =
			key(mnemonic, account, 0, index)

	fun changeKey(mnemonic: String, account: Int, index: Int): DeterministicKey =
			key(mnemonic, account, 1, index)

	fun address(mnemonic: String, account: Int, index: Int): String =
			SegwitAddress.fromKey(params, receiveKey(mnemonic, account, index))
					.toString()

	fun changeAddress(mnemonic: String, account: Int, index: Int): String =
			SegwitAddress.fromKey(params, changeKey(mnemonic, account, index))
					.toString()

	/** Electrum scripthash = reverse(SHA256(scriptPubKey)) as hex. */
	fun scriptHash(mnemonic: String, account: Int, index: Int): String =
			scriptHashOf(SegwitAddress.fromKey(params, receiveKey(mnemonic, account, index)))

	fun changeScriptHash(mnemonic: String, account: Int, index: Int): String =
			scriptHashOf(SegwitAddress.fromKey(params, changeKey(mnemonic, account, index)))

	private fun scriptHashOf(addr: SegwitAddress): String {
		val program = ScriptBuilder.createOutputScript(addr).program
		return Sha256Hash.hash(program).reversedArray()
				.joinToString("") { "%02x".format(it) }
	}

	fun isValidBtcAddress(address: String): Boolean = try {
		org.bitcoinj.core.Address.fromString(params, address.trim())
		true
	} catch (e: Exception) {
		false
	}

	private fun silentKey(mnemonic: String, account: Int, branch: Int): DeterministicKey {
		val seed = MnemonicCode.toSeed(mnemonic.trim().split(Regex("\\s+")), "")
		var k = HDKeyDerivation.createMasterPrivateKey(seed)
		val path = listOf(
				ChildNumber(352, true),
				ChildNumber(0, true),
				ChildNumber(account, true),
				ChildNumber(branch, true),
				ChildNumber(0, false))
		for (n in path) k = HDKeyDerivation.deriveChildKey(k, n)
		return k
	}

	/** This wallet's reusable silent-payment address (sp1…) for [account]. */
	fun silentPaymentAddress(mnemonic: String, account: Int): String {
		val scanPub = silentKey(mnemonic, account, 1).pubKey
		val spendPub = silentKey(mnemonic, account, 0).pubKey
		return SilentPayment.encodeAddress(scanPub, spendPub, true)
	}

	fun silentScanPriv(mnemonic: String, account: Int): ByteArray =
			silentKey(mnemonic, account, 1).privKeyBytes
	fun silentSpendPub(mnemonic: String, account: Int): ByteArray =
			silentKey(mnemonic, account, 0).pubKey
	fun silentSpendPriv(mnemonic: String, account: Int): java.math.BigInteger =
			silentKey(mnemonic, account, 0).privKey
}

package chat.zerion.desktop.ui.wallet

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.TransactionWitness
import org.bitcoinj.core.Utils
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes

/**
 * Builds and signs a native-SegWit (P2WPKH) Bitcoin transaction with bitcoinj's
 * BIP143 signing. Pure/offline: given the selected inputs (each with its key and
 * value) and the outputs, it returns the raw signed transaction hex for
 * broadcast. Input selection, fees and change are decided by the caller. An
 * optional OP_RETURN payload may be attached by the caller.
 */
internal object BtcTx {

	data class Input(val txHash: String, val txPos: Int, val valueSat: Long,
			val key: ECKey)
	data class Output(val address: String?, val valueSat: Long,
			val script: ByteArray? = null)

	fun estimateVBytes(numInputs: Int, numOutputs: Int): Int =
			11 + numInputs * 68 + numOutputs * 31

	fun buildAndSign(inputs: List<Input>, outputs: List<Output>,
			opReturn: ByteArray? = null): String {
		val params = BtcKeys.params
		val tx = Transaction(params)
		for (o in outputs) {
			if (o.script != null) {
				tx.addOutput(org.bitcoinj.core.TransactionOutput(params, tx,
						Coin.valueOf(o.valueSat), o.script))
			} else {
				tx.addOutput(Coin.valueOf(o.valueSat), Address.fromString(params, o.address))
			}
		}
		if (opReturn != null) {
			val script = ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(opReturn).build()
			tx.addOutput(Coin.ZERO, script)
		}
		for (inp in inputs) {
			val outPoint = TransactionOutPoint(params, inp.txPos.toLong(),
					Sha256Hash.wrap(inp.txHash))
			val ti = TransactionInput(params, tx, ByteArray(0), outPoint,
					Coin.valueOf(inp.valueSat))
			ti.sequenceNumber = 0xfffffffdL
			tx.addInput(ti)
		}
		for ((i, inp) in inputs.withIndex()) {
			val key = inp.key
			val scriptCode = ScriptBuilder.createP2PKHOutputScript(key)
			val sig = tx.calculateWitnessSignature(i, key, scriptCode,
					Coin.valueOf(inp.valueSat), Transaction.SigHash.ALL, false)
			tx.getInput(i.toLong()).witness = TransactionWitness.redeemP2WPKH(sig, key)
		}
		return Utils.HEX.encode(tx.bitcoinSerialize())
	}

	data class TaprootInput(val txHash: String, val txPos: Int, val valueSat: Long,
			val privKey: java.math.BigInteger, val scriptPubKey: ByteArray)

	/**
	 * Builds and signs a transaction spending taproot (P2TR) inputs by key path
	 * with BIP341/BIP340. Used to spend Silent Payments outputs. The signer is
	 * verified against the official BIP340/BIP341 vectors (TaprootSign.selfTest).
	 */
	fun buildAndSignTaproot(inputs: List<TaprootInput>, outputs: List<Output>): String {
		val params = BtcKeys.params
		val tx = Transaction(params)
		for (o in outputs) {
			if (o.script != null) tx.addOutput(org.bitcoinj.core.TransactionOutput(
					params, tx, Coin.valueOf(o.valueSat), o.script))
			else tx.addOutput(Coin.valueOf(o.valueSat), Address.fromString(params, o.address))
		}
		for (inp in inputs) {
			val outPoint = TransactionOutPoint(params, inp.txPos.toLong(),
					Sha256Hash.wrap(inp.txHash))
			val ti = TransactionInput(params, tx, ByteArray(0), outPoint,
					Coin.valueOf(inp.valueSat))
			ti.sequenceNumber = 0xfffffffdL
			tx.addInput(ti)
		}
		val prevouts = inputs.map { TaprootSign.Prevout(it.scriptPubKey, it.valueSat) }
		for ((i, inp) in inputs.withIndex()) {
			val sighash = TaprootSign.keyPathSigHash(tx, prevouts, i, 0)
			val sig = TaprootSign.schnorrSign(inp.privKey, sighash, ByteArray(32))
			val w = TransactionWitness(1); w.setPush(0, sig)
			tx.getInput(i.toLong()).witness = w
		}
		return Utils.HEX.encode(tx.bitcoinSerialize())
	}
}

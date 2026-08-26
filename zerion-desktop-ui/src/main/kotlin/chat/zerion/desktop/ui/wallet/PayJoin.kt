package chat.zerion.desktop.ui.wallet

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.TransactionWitness
import org.bitcoinj.core.Utils
import org.bitcoinj.core.VarInt
import org.bitcoinj.script.ScriptBuilder

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * PayJoin (BIP78) sender for native-SegWit Bitcoin, spoken over Tor.
 *
 * PayJoin is a two-party CoinJoin: the sender and the receiver each contribute
 * inputs to one transaction, so on-chain analysis can no longer assume every
 * input belongs to one owner (it breaks the common-input-ownership heuristic)
 * and the paid amount is obscured. Unlike coordinator CoinJoins it needs no
 * third party and no anonymity set.
 *
 * Safety: this only ever runs after the caller has already built and signed a
 * fully valid ordinary transaction. If the receiver is unreachable, returns a
 * malformed proposal, or tries to take more than the agreed fee, PayJoin is
 * abandoned and the caller broadcasts that original transaction instead. Funds
 * can never be lost to a failed PayJoin — worst case is an ordinary payment.
 *
 * The PSBT (BIP174) handling here is deliberately scoped to the P2WPKH inputs
 * this wallet produces.
 */
internal object PayJoin {

	private val PSBT_MAGIC = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte())

	data class InInfo(val valueSat: Long, val key: ECKey, val scriptPubKey: ByteArray)

	data class Endpoint(val address: String, val amountSat: Long, val url: String)

	/**
	 * Parses a BIP21 URI and returns its PayJoin endpoint, or null when the URI
	 * carries no `pj=` parameter (a plain address / non-PayJoin URI).
	 */
	fun parseUri(uri: String): Endpoint? {
		val s = uri.trim()
		if (!s.startsWith("bitcoin:", ignoreCase = true)) return null
		val body = s.substring("bitcoin:".length)
		val q = body.indexOf('?')
		val address = (if (q < 0) body else body.substring(0, q)).trim()
		if (q < 0) return null
		val params = body.substring(q + 1).split('&').mapNotNull {
			val kv = it.split('=', limit = 2)
			if (kv.size == 2) kv[0].lowercase() to urlDecode(kv[1]) else null
		}.toMap()
		val pj = params["pj"] ?: return null
		if (params["pjos"] == "0") { /* output substitution disabled: still fine */ }
		val amount = params["amount"]?.toBigDecimalOrNull()?.movePointRight(8)
				?.toBigInteger()?.toLong() ?: 0L
		return Endpoint(address, amount, pj)
	}

	private fun urlDecode(s: String): String =
			try { java.net.URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

	/**
	 * Runs the BIP78 sender exchange. [signedOriginal] is the fully-signed
	 * fallback transaction; [ourInputs] maps each of its outpoints to the value,
	 * key and scriptPubKey needed to re-sign after the receiver modifies the tx.
	 * Returns the final signed transaction hex to broadcast. Throws on any
	 * problem so the caller can fall back to broadcasting [signedOriginal].
	 */
	fun request(signedOriginal: Transaction, ourInputs: Map<String, InInfo>,
			changeOutputIndex: Int, feeRateSatPerVb: Double, endpoint: Endpoint,
			socksPort: Int): String {
		if (socksPort <= 0) throw IOException("Tor is not ready")
		val originalPsbt = encodeFinalized(signedOriginal, ourInputs)
		val maxFee = Math.ceil(68.0 * feeRateSatPerVb).toLong().coerceAtLeast(0)
		val minFee = Math.max(1, Math.floor(feeRateSatPerVb * 0.8).toInt())
		val query = StringBuilder("?v=1")
		if (changeOutputIndex >= 0) {
			query.append("&additionalfeeoutputindex=").append(changeOutputIndex)
			query.append("&maxadditionalfeecontribution=").append(maxFee)
		} else {
			query.append("&maxadditionalfeecontribution=0")
		}
		query.append("&minfeerate=").append(minFee)

		val proposalB64 = post(endpoint.url + query.toString(),
				Base64.getEncoder().encodeToString(originalPsbt), socksPort)
		val proposal = decode(Base64.getMimeDecoder().decode(proposalB64.trim()))

		validate(signedOriginal, proposal, ourInputs, endpoint, maxFee, changeOutputIndex)
		return finalize(proposal, ourInputs)
	}


	private fun validate(original: Transaction, proposal: Psbt,
			ourInputs: Map<String, InInfo>, endpoint: Endpoint, maxFee: Long,
			changeIndex: Int) {
		val tx = proposal.unsignedTx
		val proposalOutpoints = tx.inputs.map { outpointKey(it.outpoint) }.toSet()
		if (!proposalOutpoints.containsAll(ourInputs.keys)) {
			throw IOException("PayJoin proposal dropped one of our inputs")
		}
		if (tx.inputs.size <= original.inputs.size) {
			throw IOException("PayJoin proposal added no receiver input")
		}
		val payScript = ScriptBuilder.createOutputScript(
				org.bitcoinj.core.Address.fromString(BtcKeys.params, endpoint.address)).program
		val paid = tx.outputs.filter { it.scriptBytes.contentEquals(payScript) }
				.sumOf { it.value.value }
		if (endpoint.amountSat > 0 && paid < endpoint.amountSat) {
			throw IOException("PayJoin proposal underpays the receiver")
		}
		val ourInputSum = ourInputs.values.sumOf { it.valueSat }
		val ourChangeScripts = original.outputs.mapIndexedNotNull { i, o ->
			if (i == changeIndex) o.scriptBytes else null
		}
		val ourChangeInProposal = tx.outputs.filter { o ->
			ourChangeScripts.any { it.contentEquals(o.scriptBytes) }
		}.sumOf { it.value.value }
		val origChange = if (changeIndex >= 0)
			original.getOutput(changeIndex.toLong()).value.value else 0L
		val contributed = ourInputSum - ourChangeInProposal
		val origContributed = ourInputSum - origChange
		if (contributed > origContributed + maxFee) {
			throw IOException("PayJoin proposal takes more than the agreed fee")
		}
	}


	private fun finalize(proposal: Psbt, ourInputs: Map<String, InInfo>): String {
		val tx = proposal.unsignedTx
		for ((i, input) in tx.inputs.withIndex()) {
			val key = outpointKey(input.outpoint)
			val ours = ourInputs[key]
			if (ours != null) {
				val scriptCode = ScriptBuilder.createP2PKHOutputScript(ours.key)
				val sig = tx.calculateWitnessSignature(i, ours.key, scriptCode,
						Coin.valueOf(ours.valueSat), Transaction.SigHash.ALL, false)
				input.witness = TransactionWitness.redeemP2WPKH(sig, ours.key)
			} else {
				val w = proposal.finalWitness[i]
						?: throw IOException("Receiver input $i has no witness")
				input.witness = parseWitness(w)
			}
		}
		return Utils.HEX.encode(tx.bitcoinSerialize())
	}


	private data class Psbt(val unsignedTx: Transaction,
			val finalWitness: Map<Int, ByteArray>)

	private fun encodeFinalized(signed: Transaction, ourInputs: Map<String, InInfo>): ByteArray {
		val unsigned = Transaction(BtcKeys.params)
		unsigned.setVersion(signed.version.toInt())
		for (o in signed.outputs) unsigned.addOutput(o.value, o.scriptPubKey)
		for (inp in signed.inputs) {
			unsigned.addInput(TransactionInput(BtcKeys.params, unsigned, ByteArray(0),
					inp.outpoint))
		}
		unsigned.lockTime = signed.lockTime

		val out = ByteArrayOutputStream()
		out.write(PSBT_MAGIC)
		writeKeyValue(out, byteArrayOf(0x00), unsigned.bitcoinSerialize())
		out.write(0x00)
		for ((i, inp) in signed.inputs.withIndex()) {
			val info = ourInputs[outpointKey(inp.outpoint)]
					?: throw IOException("missing input info")
			val utxo = ByteArrayOutputStream()
			utxo.write(le64(info.valueSat))
			utxo.write(VarInt(info.scriptPubKey.size.toLong()).encode())
			utxo.write(info.scriptPubKey)
			writeKeyValue(out, byteArrayOf(0x01), utxo.toByteArray())
			writeKeyValue(out, byteArrayOf(0x08), serializeWitness(inp.witness))
			out.write(0x00)
		}
		for (o in signed.outputs) out.write(0x00)
		return out.toByteArray()
	}

	private fun decode(bytes: ByteArray): Psbt {
		var p = 0
		fun need(n: Int) { if (p + n > bytes.size) throw IOException("truncated PSBT") }
		need(5)
		for (b in PSBT_MAGIC) { if (bytes[p++] != b) throw IOException("not a PSBT") }

		var unsignedTx: Transaction? = null
		while (true) {
			val kv = readKeyValue(bytes, p) ?: run { p += 1; null } ?: break
			p = kv.next
			if (kv.key.isNotEmpty() && kv.key[0].toInt() == 0x00) {
				unsignedTx = Transaction(BtcKeys.params, kv.value)
			}
		}
		val tx = unsignedTx ?: throw IOException("PSBT has no unsigned tx")

		val finals = HashMap<Int, ByteArray>()
		for (i in tx.inputs.indices) {
			while (true) {
				val kv = readKeyValue(bytes, p) ?: run { p += 1; null } ?: break
				p = kv.next
				if (kv.key.isNotEmpty() && kv.key[0].toInt() == 0x08) finals[i] = kv.value
			}
		}
		return Psbt(tx, finals)
	}

	private class Kv(val key: ByteArray, val value: ByteArray, val next: Int)

	private fun readKeyValue(bytes: ByteArray, pos: Int): Kv? {
		if (pos >= bytes.size) return null
		val keyLen = VarInt(bytes, pos)
		if (keyLen.value == 0L) return null
		var p = pos + keyLen.originalSizeInBytes
		val key = bytes.copyOfRange(p, p + keyLen.value.toInt()); p += keyLen.value.toInt()
		val valLen = VarInt(bytes, p); p += valLen.originalSizeInBytes
		val value = bytes.copyOfRange(p, p + valLen.value.toInt()); p += valLen.value.toInt()
		return Kv(key, value, p)
	}

	private fun writeKeyValue(out: ByteArrayOutputStream, key: ByteArray, value: ByteArray) {
		out.write(VarInt(key.size.toLong()).encode())
		out.write(key)
		out.write(VarInt(value.size.toLong()).encode())
		out.write(value)
	}


	private fun serializeWitness(w: TransactionWitness): ByteArray {
		val out = ByteArrayOutputStream()
		out.write(VarInt(w.pushCount.toLong()).encode())
		for (i in 0 until w.pushCount) {
			val push = w.getPush(i)
			out.write(VarInt(push.size.toLong()).encode())
			out.write(push)
		}
		return out.toByteArray()
	}

	private fun parseWitness(bytes: ByteArray): TransactionWitness {
		var p = 0
		val count = VarInt(bytes, p); p += count.originalSizeInBytes
		val w = TransactionWitness(count.value.toInt())
		for (i in 0 until count.value.toInt()) {
			val len = VarInt(bytes, p); p += len.originalSizeInBytes
			w.setPush(i, bytes.copyOfRange(p, p + len.value.toInt())); p += len.value.toInt()
		}
		return w
	}


	private fun le64(v: Long): ByteArray {
		val b = ByteArray(8)
		var x = v
		for (i in 0 until 8) { b[i] = (x and 0xff).toByte(); x = x shr 8 }
		return b
	}

	private fun outpointKey(op: TransactionOutPoint): String =
			op.hash.toString() + ":" + op.index

	private fun post(url: String, body: String, socksPort: Int): String {
		val client = OkHttpClient.Builder()
				.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.callTimeout(90, TimeUnit.SECONDS)
				.build()
		val req = Request.Builder().url(url)
				.post(body.toRequestBody("text/plain".toMediaType()))
				.build()
		client.newCall(req).execute().use { resp ->
			val text = resp.body?.string() ?: ""
			if (!resp.isSuccessful) {
				throw IOException("PayJoin endpoint HTTP ${resp.code}")
			}
			return text
		}
	}
}

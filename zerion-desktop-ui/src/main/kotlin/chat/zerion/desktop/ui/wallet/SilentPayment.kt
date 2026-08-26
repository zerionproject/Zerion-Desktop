package chat.zerion.desktop.ui.wallet

import org.bitcoinj.core.ECKey

import org.bouncycastle.math.ec.ECPoint

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Silent Payments (BIP352) — sender side.
 *
 * A silent payment address (sp1…) is published once; the sender derives a
 * unique, unlinkable P2TR output for it from the transaction's own inputs via
 * ECDH, so payments to the same recipient share no on-chain link and never
 * reuse an address. This implements the address codec and the sender output
 * derivation for the wallet's native-SegWit (P2WPKH) inputs.
 *
 * secp256k1 point/scalar math is delegated to BouncyCastle (via bitcoinj's
 * curve); only the BIP352 construction is here. [selfTest] runs the official
 * BIP352 known-answer vector — callers MUST gate any real spend on it so a
 * mis-derived output can never receive funds.
 */
internal object SilentPayment {

	private val CURVE = ECKey.CURVE
	private val N: BigInteger = CURVE.n

	private fun decodePoint(bytes: ByteArray): ECPoint = CURVE.curve.decodePoint(bytes)
	private fun serP(p: ECPoint): ByteArray = p.getEncoded(true)
	private fun xOnly(p: ECPoint): ByteArray = p.normalize().affineXCoord.encoded
	private fun mulG(k: BigInteger): ECPoint = CURVE.g.multiply(k).normalize()

	private fun taggedHash(tag: String, msg: ByteArray): ByteArray {
		val sha = MessageDigest.getInstance("SHA-256")
		val t = sha.digest(tag.toByteArray(Charsets.US_ASCII))
		sha.reset()
		sha.update(t); sha.update(t); sha.update(msg)
		return sha.digest()
	}

	private fun ser32(k: Int): ByteArray =
			byteArrayOf((k ushr 24).toByte(), (k ushr 16).toByte(),
					(k ushr 8).toByte(), k.toByte())

	private fun unsignedCompare(a: ByteArray, b: ByteArray): Int {
		val n = minOf(a.size, b.size)
		for (i in 0 until n) {
			val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
			if (d != 0) return d
		}
		return a.size - b.size
	}

	/**
	 * Derives [count] silent-payment output x-only keys for a recipient
	 * (scan/spend pubkeys) from the spending transaction's inputs.
	 * [inputPrivKeys] are the 32-byte private keys of the P2WPKH inputs and
	 * [outpoints] their serialized outpoints (txid little-endian ‖ vout LE).
	 */
	fun deriveOutputs(inputPrivKeys: List<ByteArray>, outpoints: List<ByteArray>,
			scanPub: ByteArray, spendPub: ByteArray, count: Int): List<ByteArray> {
		require(inputPrivKeys.isNotEmpty() && outpoints.size == inputPrivKeys.size)
		var a = BigInteger.ZERO
		for (pk in inputPrivKeys) a = a.add(BigInteger(1, pk)).mod(N)
		require(a.signum() != 0) { "input key sum is zero" }
		val aG = mulG(a)
		val smallest = outpoints.minWithOrNull(::unsignedCompare)!!
		val inputHash = BigInteger(1,
				taggedHash("BIP0352/Inputs", smallest + serP(aG))).mod(N)
		val bScan = decodePoint(scanPub)
		val bSpend = decodePoint(spendPub)
		val ecdh = bScan.multiply(inputHash.multiply(a).mod(N)).normalize()
		val ecdhSer = serP(ecdh)
		val out = ArrayList<ByteArray>(count)
		for (k in 0 until count) {
			val tk = BigInteger(1,
					taggedHash("BIP0352/SharedSecret", ecdhSer + ser32(k))).mod(N)
			require(tk.signum() != 0 && tk < N)
			out.add(xOnly(bSpend.add(mulG(tk)).normalize()))
		}
		return out
	}

	private fun to32(v: BigInteger): ByteArray {
		val b = v.toByteArray()
		return when {
			b.size == 32 -> b
			b.size == 33 && b[0].toInt() == 0 -> b.copyOfRange(1, 33)
			b.size < 32 -> ByteArray(32 - b.size) + b
			else -> b.copyOfRange(b.size - 32, b.size)
		}
	}

	fun pubFromPriv(priv: ByteArray): ByteArray = serP(mulG(BigInteger(1, priv).mod(N)))

	data class Detected(val outputXOnly: ByteArray, val tweak: ByteArray)

	/**
	 * Receiver scan: given a transaction's summed input public key
	 * [inputPubKeySum] and [outpoints], the wallet's [scanPriv] and [spendPub],
	 * and the transaction's taproot output x-only keys, returns the outputs that
	 * belong to this wallet, each with its tweak (the spend private key is
	 * spendPriv + tweak mod n).
	 */
	fun scan(inputPubKeySum: ByteArray, outpoints: List<ByteArray>, scanPriv: ByteArray,
			spendPub: ByteArray, txOutputs: List<ByteArray>): List<Detected> {
		val a = decodePoint(inputPubKeySum)
		val smallest = outpoints.minWithOrNull(::unsignedCompare)!!
		val inputHash = BigInteger(1,
				taggedHash("BIP0352/Inputs", smallest + serP(a))).mod(N)
		val bScan = BigInteger(1, scanPriv).mod(N)
		val ecdh = a.multiply(inputHash.multiply(bScan).mod(N)).normalize()
		val ecdhSer = serP(ecdh)
		val bSpend = decodePoint(spendPub)
		val remaining = txOutputs.toMutableList()
		val found = ArrayList<Detected>()
		var k = 0
		while (true) {
			val tk = BigInteger(1,
					taggedHash("BIP0352/SharedSecret", ecdhSer + ser32(k))).mod(N)
			val xk = xOnly(bSpend.add(mulG(tk)).normalize())
			val idx = remaining.indexOfFirst { it.contentEquals(xk) }
			if (idx < 0) break
			found.add(Detected(xk, to32(tk)))
			remaining.removeAt(idx); k++
		}
		return found
	}

	/**
	 * Receiver scan using a precomputed per-transaction tweak point
	 * (input_hash · A), as served by a BIP352 light-client backend. Equivalent
	 * to [scan] but the backend has already done the input-hash and input-sum
	 * work, so the client only needs its own scan key.
	 */
	fun scanWithTweak(tweakPoint: ByteArray, scanPriv: ByteArray, spendPub: ByteArray,
			txOutputs: List<ByteArray>): List<Detected> {
		val ecdh = decodePoint(tweakPoint)
				.multiply(BigInteger(1, scanPriv).mod(N)).normalize()
		val ecdhSer = serP(ecdh)
		val bSpend = decodePoint(spendPub)
		val remaining = txOutputs.toMutableList()
		val found = ArrayList<Detected>()
		var k = 0
		while (true) {
			val tk = BigInteger(1,
					taggedHash("BIP0352/SharedSecret", ecdhSer + ser32(k))).mod(N)
			val xk = xOnly(bSpend.add(mulG(tk)).normalize())
			val idx = remaining.indexOfFirst { it.contentEquals(xk) }
			if (idx < 0) break
			found.add(Detected(xk, to32(tk)))
			remaining.removeAt(idx); k++
		}
		return found
	}

	fun tweakPoint(inputPubKeySum: ByteArray, outpoints: List<ByteArray>): ByteArray {
		val a = decodePoint(inputPubKeySum)
		val smallest = outpoints.minWithOrNull(::unsignedCompare)!!
		val inputHash = BigInteger(1,
				taggedHash("BIP0352/Inputs", smallest + serP(a))).mod(N)
		return serP(a.multiply(inputHash).normalize())
	}


	fun isSilentAddress(s: String): Boolean {
		val a = s.trim().lowercase()
		return a.startsWith("sp1") || a.startsWith("tsp1")
	}

	data class Address(val mainnet: Boolean, val scanPub: ByteArray, val spendPub: ByteArray)

	fun encodeAddress(scanPub: ByteArray, spendPub: ByteArray, mainnet: Boolean = true): String {
		require(scanPub.size == 33 && spendPub.size == 33)
		val hrp = if (mainnet) "sp" else "tsp"
		val prog = convertBits(toInts(scanPub + spendPub), 8, 5, true)!!
		return bech32mEncode(hrp, intArrayOf(0) + prog)
	}

	fun decodeAddress(addr: String): Address? {
		val a = addr.trim()
		val lower = a.lowercase()
		if (!(lower.startsWith("sp1") || lower.startsWith("tsp1"))) return null
		val (hrp, data) = bech32mDecode(lower) ?: return null
		if (data.isEmpty() || data[0] != 0) return null
		val payload = convertBits(data.copyOfRange(1, data.size), 5, 8, false) ?: return null
		if (payload.size != 66) return null
		val bytes = ByteArray(66) { payload[it].toByte() }
		return Address(hrp == "sp", bytes.copyOfRange(0, 33), bytes.copyOfRange(33, 66))
	}

	private fun toInts(b: ByteArray): IntArray = IntArray(b.size) { b[it].toInt() and 0xff }

	private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
	private const val BECH32M = 0x2bc830a3

	private fun polymod(values: IntArray): Int {
		val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
		var chk = 1
		for (v in values) {
			val b = chk ushr 25
			chk = ((chk and 0x1ffffff) shl 5) xor v
			for (i in 0..4) if (((b ushr i) and 1) != 0) chk = chk xor gen[i]
		}
		return chk
	}

	private fun hrpExpand(hrp: String): IntArray {
		val out = IntArray(hrp.length * 2 + 1)
		for (i in hrp.indices) out[i] = hrp[i].code ushr 5
		for (i in hrp.indices) out[hrp.length + 1 + i] = hrp[i].code and 31
		return out
	}

	private fun bech32mEncode(hrp: String, data: IntArray): String {
		val values = hrpExpand(hrp) + data
		val poly = polymod(values + IntArray(6)) xor BECH32M
		val checksum = IntArray(6) { (poly ushr (5 * (5 - it))) and 31 }
		val sb = StringBuilder(hrp).append('1')
		for (d in data) sb.append(CHARSET[d])
		for (d in checksum) sb.append(CHARSET[d])
		return sb.toString()
	}

	private fun bech32mDecode(s: String): Pair<String, IntArray>? {
		val pos = s.lastIndexOf('1')
		if (pos < 1 || pos + 7 > s.length) return null
		val hrp = s.substring(0, pos)
		val data = IntArray(s.length - pos - 1)
		for (i in data.indices) {
			val c = CHARSET.indexOf(s[pos + 1 + i]); if (c < 0) return null
			data[i] = c
		}
		if (polymod(hrpExpand(hrp) + data) != BECH32M) return null
		return hrp to data.copyOf(data.size - 6)
	}

	private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray? {
		var acc = 0; var bits = 0
		val out = ArrayList<Int>()
		val maxv = (1 shl to) - 1
		for (value in data) {
			if (value < 0 || (value ushr from) != 0) return null
			acc = (acc shl from) or value; bits += from
			while (bits >= to) { bits -= to; out.add((acc ushr bits) and maxv) }
		}
		if (pad) { if (bits > 0) out.add((acc shl (to - bits)) and maxv) }
		else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) return null
		return out.toIntArray()
	}


	private fun hex(s: String): ByteArray {
		val out = ByteArray(s.length / 2)
		for (i in out.indices) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
		return out
	}
	private fun toHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

	fun selfTest(): Boolean = try {
		val privs = listOf(
				hex("eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1"),
				hex("93f5ed907ad5b2bdbbdcb5d9116ebc0a4e1f92f910d5260237fa45a9408aad16"))
		val outpoints = listOf(
				hex("f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16")
						.reversedArray() + byteArrayOf(0, 0, 0, 0),
				hex("a1075db55d416d3ca199f55b6084e2115b9345e16c5cf302fc80e9d5fbf5d48d")
						.reversedArray() + byteArrayOf(0, 0, 0, 0))
		val scan = hex("0220bcfac5b99e04ad1a06ddfb016ee13582609d60b6291e98d01a9bc9a16c96d4")
		val spend = hex("025cc9856d6f8375350e123978daac200c260cb5b5ae83106cab90484dcd8fcf36")
		val out = deriveOutputs(privs, outpoints, scan, spend, 1)
		val expected = "3e9fce73d4e77a4809908e3c3a2e54ee147b9312dc5044a193d1fc85de46e3c1"
		val addr = "sp1qqgste7k9hx0qftg6qmwlkqtwuy6cycyavzmzj85c6qdfhjdpdjtdgqjuex" +
				"zk6murw56suy3e0rd2cgqvycxttddwsvgxe2usfpxumr70xc9pkqwv"
		val derivedOk = out.size == 1 && toHex(out[0]) == expected
		val codecOk = encodeAddress(scan, spend, true) == addr
		val roundTrip = decodeAddress(addr)?.let {
			toHex(it.scanPub) == toHex(scan) && toHex(it.spendPub) == toHex(spend)
		} ?: false
		val scanPriv = hex("0f694e068028a717f8af6b9411f9a133dd3565258714cc226594b34db90c1f2c")
		val spendPriv = hex("9d6ad855ce3417ef84e836892e5a56392bfba05fa5d97ccea30e266f540e08b3")
		val inputSum = hex("032562c1ab2d6bd45d7ca4d78f569999e5333dffd3ac5263924fd00d00dedc4bee")
		val detected = scan(inputSum, outpoints, scanPriv, pubFromPriv(spendPriv),
				listOf(hex(expected)))
		val recvOk = detected.size == 1 &&
				toHex(detected[0].outputXOnly) == expected &&
				toHex(detected[0].tweak) ==
						"f438b40179a3c4262de12986c0e6cce0634007cdc79c1dcd3e20b9ebc2e7eef6"
		val tw = tweakPoint(inputSum, outpoints)
		val detected2 = scanWithTweak(tw, scanPriv, pubFromPriv(spendPriv), listOf(hex(expected)))
		val tweakScanOk = detected2.size == 1 &&
				toHex(detected2[0].tweak) == toHex(detected[0].tweak)
		derivedOk && codecOk && roundTrip && recvOk && tweakScanOk
	} catch (e: Exception) { false }
}

package chat.zerion.desktop.ui.wallet

import org.bitcoinj.core.ECKey

import org.bouncycastle.math.ec.ECPoint

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Taproot (BIP341) key-path spending with BIP340 Schnorr signatures.
 *
 * Used to spend P2TR outputs the wallet controls, including Silent Payments
 * (BIP352) outputs, whose output key is used directly (no additional taproot
 * tweak) and whose private key is spendPriv + tweak. The secp256k1 math is
 * delegated to BouncyCastle. Everything here is verified against the official
 * BIP340 and BIP341 test vectors in [selfTest]; callers gate real spends on it.
 */
internal object TaprootSign {

	private val CURVE = ECKey.CURVE
	private val N: BigInteger = CURVE.n

	private fun xOnly(p: ECPoint): ByteArray = p.normalize().affineXCoord.encoded
	private fun hasEvenY(p: ECPoint): Boolean =
			!p.normalize().affineYCoord.toBigInteger().testBit(0)
	private fun mulG(k: BigInteger): ECPoint = CURVE.g.multiply(k).normalize()

	private fun tagged(tag: String, msg: ByteArray): ByteArray {
		val sha = MessageDigest.getInstance("SHA-256")
		val t = sha.digest(tag.toByteArray(Charsets.US_ASCII))
		sha.reset(); sha.update(t); sha.update(t); sha.update(msg)
		return sha.digest()
	}
	private fun sha256(vararg parts: ByteArray): ByteArray {
		val d = MessageDigest.getInstance("SHA-256")
		for (p in parts) d.update(p)
		return d.digest()
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
	private fun le32(v: Long): ByteArray = byteArrayOf(v.toByte(), (v ushr 8).toByte(),
			(v ushr 16).toByte(), (v ushr 24).toByte())
	private fun le64(v: Long): ByteArray {
		val b = ByteArray(8); var x = v
		for (i in 0 until 8) { b[i] = (x and 0xff).toByte(); x = x shr 8 }
		return b
	}


	fun schnorrSign(priv: BigInteger, msg: ByteArray, aux: ByteArray): ByteArray {
		require(priv.signum() != 0 && priv < N) { "bad private key" }
		val p = mulG(priv)
		val d = if (hasEvenY(p)) priv else N.subtract(priv)
		val t = to32(d).let { db ->
			val a = tagged("BIP0340/aux", aux)
			ByteArray(32) { (db[it].toInt() xor a[it].toInt()).toByte() }
		}
		val rand = tagged("BIP0340/nonce", t + xOnly(p) + msg)
		val k0 = BigInteger(1, rand).mod(N)
		require(k0.signum() != 0) { "nonce is zero" }
		val r = mulG(k0)
		val k = if (hasEvenY(r)) k0 else N.subtract(k0)
		val e = BigInteger(1, tagged("BIP0340/challenge", xOnly(r) + xOnly(p) + msg)).mod(N)
		return xOnly(r) + to32(k.add(e.multiply(d)).mod(N))
	}

	private fun schnorrVerify(pubXOnly: ByteArray, msg: ByteArray, sig: ByteArray): Boolean {
		if (sig.size != 64) return false
		return try {
			val p = liftX(pubXOnly)
			val rx = BigInteger(1, sig.copyOfRange(0, 32))
			val s = BigInteger(1, sig.copyOfRange(32, 64))
			if (s >= N) return false
			val e = BigInteger(1, tagged("BIP0340/challenge",
					sig.copyOfRange(0, 32) + pubXOnly + msg)).mod(N)
			val rPoint = mulG(s).add(p.multiply(N.subtract(e))).normalize()
			!rPoint.isInfinity && hasEvenY(rPoint) &&
					rPoint.affineXCoord.toBigInteger() == rx
		} catch (e: Exception) { false }
	}

	private fun liftX(x: ByteArray): ECPoint {
		val prefix = byteArrayOf(0x02)
		return CURVE.curve.decodePoint(prefix + x)
	}


	class Prevout(val scriptPubKey: ByteArray, val amountSat: Long)

	/**
	 * BIP341 signature-message hash for a key-path spend of input [inputIndex].
	 * [prevouts] are all spent outputs (value + scriptPubKey), ordered by input.
	 * [hashType] 0 = SIGHASH_DEFAULT (signs all).
	 */
	fun keyPathSigHash(tx: org.bitcoinj.core.Transaction, prevouts: List<Prevout>,
			inputIndex: Int, hashType: Int): ByteArray {
		val version = le32(tx.version)
		val locktime = le32(tx.lockTime)
		val shaPrevouts = sha256(tx.inputs.map { inp ->
			inp.outpoint.hash.reversedBytes + le32(inp.outpoint.index)
		}.reduceOrEmpty())
		val shaAmounts = sha256(prevouts.map { le64(it.amountSat) }.reduceOrEmpty())
		val shaScriptPubKeys = sha256(prevouts.map {
			varInt(it.scriptPubKey.size.toLong()) + it.scriptPubKey
		}.reduceOrEmpty())
		val shaSequences = sha256(tx.inputs.map { le32(it.sequenceNumber) }.reduceOrEmpty())
		val shaOutputs = sha256(tx.outputs.map {
			le64(it.value.value) + varInt(it.scriptBytes.size.toLong()) + it.scriptBytes
		}.reduceOrEmpty())

		val anyoneCanPay = (hashType and 0x80) != 0
		val outputType = hashType and 0x03
		val out = java.io.ByteArrayOutputStream()
		out.write(0x00)
		out.write(hashType)
		out.write(version); out.write(locktime)
		if (!anyoneCanPay) {
			out.write(shaPrevouts); out.write(shaAmounts)
			out.write(shaScriptPubKeys); out.write(shaSequences)
		}
		if (outputType == 0x00) out.write(shaOutputs)
		out.write(0x00)
		if (anyoneCanPay) {
			val inp = tx.inputs[inputIndex]
			out.write(inp.outpoint.hash.reversedBytes); out.write(le32(inp.outpoint.index))
			out.write(le64(prevouts[inputIndex].amountSat))
			out.write(varInt(prevouts[inputIndex].scriptPubKey.size.toLong()))
			out.write(prevouts[inputIndex].scriptPubKey)
			out.write(le32(inp.sequenceNumber))
		} else {
			out.write(le32(inputIndex.toLong()))
		}
		if (outputType == 0x03) {
			val o = tx.outputs[inputIndex]
			out.write(sha256(le64(o.value.value) +
					varInt(o.scriptBytes.size.toLong()) + o.scriptBytes))
		}
		return tagged("TapSighash", out.toByteArray())
	}

	private fun List<ByteArray>.reduceOrEmpty(): ByteArray {
		val out = java.io.ByteArrayOutputStream()
		for (b in this) out.write(b)
		return out.toByteArray()
	}
	private fun varInt(v: Long): ByteArray = when {
		v < 0xfd -> byteArrayOf(v.toByte())
		v <= 0xffff -> byteArrayOf(0xfd.toByte(), v.toByte(), (v ushr 8).toByte())
		v <= 0xffffffffL -> byteArrayOf(0xfe.toByte(), v.toByte(), (v ushr 8).toByte(),
				(v ushr 16).toByte(), (v ushr 24).toByte())
		else -> byteArrayOf(0xff.toByte()) + le64(v)
	}


	private fun hex(s: String): ByteArray {
		val out = ByteArray(s.length / 2)
		for (i in out.indices) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
		return out
	}
	private fun toHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

	fun selfTest(): Boolean = try {
		val d = BigInteger("B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF", 16)
		val msg = hex("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
		val aux = hex("0000000000000000000000000000000000000000000000000000000000000001")
		val sig = schnorrSign(d, msg, aux)
		val expSig = "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de3341" +
				"8906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a"
		val pub = hex("DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659")
		val signOk = toHex(sig) == expSig && schnorrVerify(pub, msg, sig)
		val rawTx = hex("02000000097de20cbff686da83a54981d2b9bab3586f4ca7e48f57f5b5596" +
				"3115f3b334e9c010000000000000000d7b7cab57b1393ace2d064f4d4a2cb8af6def6127" +
				"3e127517d44759b6dafdd990000000000fffffffff8e1f583384333689228c5d28eac133" +
				"66be082dc57441760d957275419a418420000000000fffffffff0689180aa63b30cb162a" +
				"73c6d2a38b7eeda2a83ece74310fda0843ad604853b0100000000feffffffaa5202bdf6d" +
				"8ccd2ee0f0202afbbb7461d9264a25e5bfd3c5a52ee1239e0ba6c0000000000feffffff95" +
				"6149bdc66faa968eb2be2d2faa29718acbfe3941215893a2a3446d32acd0500000000000" +
				"00000000e664b9773b88c09c32cb70a2a3e4da0ced63b7ba3b22f848531bbb1d5d5f4c940" +
				"10000000000000000e9aa6b8e6c9de67619e6a3924ae25696bb7b694bb677a632a74ef7e" +
				"adfd4eabf0000000000ffffffffa778eb6a263dc090464cd125c466b5a99667720b1c110" +
				"468831d058aa1b82af10100000000ffffffff0200ca9a3b000000001976a91406afd46bc" +
				"dfd22ef94ac122aa11f241244a37ecc88ac807840cb0000000020ac9a87f5594be208f85" +
				"32db38cff670c450ed2fea8fcdefcc9a663f78bab962b0065cd1d")
		val us = ("512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343,420000000;" +
				"5120147c9c57132f6e7ecddba9800bb0c4449251c92a1e60371ee77557b6620f3ea3,462000000;" +
				"76a914751e76e8199196d454941c45d1b3a323f1433bd688ac,294000000;" +
				"5120e4d810fd50586274face62b8a807eb9719cef49c04177cc6b76a9a4251d5450e,504000000;" +
				"512091b64d5324723a985170e4dc5a0f84c041804f2cd12660fa5dec09fc21783605,630000000;" +
				"00147dd65592d0ab2fe0d0257d571abf032cd9db93dc,378000000;" +
				"512075169f4001aa68f15bbed28b218df1d0a62cbbcf1188c6665110c293c907b831,672000000;" +
				"5120712447206d7a5238acc7ff53fbe94a3b64539ad291c7cdbc490b7577e4b17df5,546000000;" +
				"512077e30a5522dd9f894c3f8b8bd4c4b2cf82ca7da8a3ea6a239655c39c050ab220,588000000")
		val tx = org.bitcoinj.core.Transaction(BtcKeys.params, rawTx)
		val prevouts = us.split(";").map {
			val p = it.split(","); Prevout(hex(p[0]), p[1].toLong())
		}
		val sighashOk = toHex(keyPathSigHash(tx, prevouts, 0, 3)) ==
				"2514a6272f85cfa0f45eb907fcb0d121b808ed37c6ea160a5a9046ed5526d555"
		signOk && sighashOk
	} catch (e: Exception) { false }
}

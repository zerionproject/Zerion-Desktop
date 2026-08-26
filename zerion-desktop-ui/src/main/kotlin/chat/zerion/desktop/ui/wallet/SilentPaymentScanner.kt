package chat.zerion.desktop.ui.wallet

/**
 * Silent Payments (BIP352) receiving scanner against a light-client backend
 * (a BlindBit-style oracle) reached over Tor. The oracle indexes, per block, the
 * per-transaction tweak (input_hash · A) and the taproot outputs, so the client
 * only needs its own scan key: for each tweak it runs the tweak-based scan and
 * matches derived outputs against the block's taproot UTXOs.
 *
 * This is opt-in and off by default; it requires a reachable oracle (ideally one
 * you run yourself). The detection cryptography is verified in
 * [SilentPayment.selfTest]; the network responses are parsed defensively so a
 * malformed or unreachable oracle yields no results rather than an error.
 */
internal object SilentPaymentScanner {

	data class Found(val txid: String, val vout: Int, val valueSat: Long,
			val xonly: ByteArray, val tweak: ByteArray)

	fun tipHeight(base: String, socks: Int): Int? {
		val r = TorHttp.get(url(base, "block-height"), socks) ?: return null
		return numField(r, "block_height")?.toInt()
	}

	/** Scans a single block; returns the outputs at that height belonging to us. */
	fun scanBlock(base: String, height: Int, scanPriv: ByteArray, spendPub: ByteArray,
			socks: Int): List<Found> {
		val tweaksResp = TorHttp.get(url(base, "tweaks/$height"), socks) ?: return emptyList()
		val tweaks = Regex("[0-9a-fA-F]{66}").findAll(tweaksResp).map { it.value }.toList()
		if (tweaks.isEmpty()) return emptyList()
		val utxosResp = TorHttp.get(url(base, "utxos/$height"), socks) ?: return emptyList()

		data class U(val txid: String, val vout: Int, val value: Long, val xonly: ByteArray)
		val utxos = Regex("\\{[^{}]*}").findAll(utxosResp).mapNotNull { m ->
			val o = m.value
			val spk = strField(o, "scriptpubkey") ?: return@mapNotNull null
			if (!spk.startsWith("5120") || spk.length < 68) return@mapNotNull null
			val txid = strField(o, "txid") ?: return@mapNotNull null
			val vout = numField(o, "vout")?.toInt() ?: return@mapNotNull null
			val value = numField(o, "value") ?: 0L
			U(txid, vout, value, hexToBytes(spk.substring(4, 68)))
		}.toList()
		if (utxos.isEmpty()) return emptyList()

		val outXonlys = utxos.map { it.xonly }
		val found = ArrayList<Found>()
		for (twHex in tweaks) {
			val detected = try {
				SilentPayment.scanWithTweak(hexToBytes(twHex), scanPriv, spendPub, outXonlys)
			} catch (e: Exception) { emptyList() }
			for (d in detected) {
				val u = utxos.firstOrNull { it.xonly.contentEquals(d.outputXOnly) } ?: continue
				if (found.none { it.txid == u.txid && it.vout == u.vout })
					found.add(Found(u.txid, u.vout, u.value, u.xonly, d.tweak))
			}
		}
		return found
	}

	private fun url(base: String, path: String): String {
		val b = base.trim().removeSuffix("/")
		val full = if (b.startsWith("http://") || b.startsWith("https://")) b else "http://$b"
		return "$full/$path"
	}

	private fun hexToBytes(h: String): ByteArray {
		val out = ByteArray(h.length / 2)
		for (i in out.indices) out[i] = h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
		return out
	}

	private fun strField(json: String, key: String): String? {
		val m = "\"$key\":\""
		val i = json.indexOf(m); if (i < 0) return null
		val start = i + m.length; val end = json.indexOf('"', start)
		return if (end < 0) null else json.substring(start, end)
	}

	private fun numField(json: String, key: String): Long? {
		val m = "\"$key\":"
		val i = json.indexOf(m); if (i < 0) return null
		var s = i + m.length
		if (s < json.length && json[s] == '"') s++
		var e = s
		while (e < json.length && (json[e].isDigit() || json[e] == '-')) e++
		return json.substring(s, e).toLongOrNull()
	}
}

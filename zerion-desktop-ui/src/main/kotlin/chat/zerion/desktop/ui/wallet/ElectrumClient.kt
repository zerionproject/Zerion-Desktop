package chat.zerion.desktop.ui.wallet

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Minimal Electrum-protocol client for Bitcoin, spoken over the app's Tor SOCKS
 * proxy (remote DNS, so the server never learns the user's IP and .onion
 * servers work). One instance = one connection used for a batch of queries then
 * closed. Only the handful of methods the wallet needs are implemented; JSON is
 * newline-delimited so a small extractor avoids a JSON dependency.
 */
internal class ElectrumClient(host: String, port: Int, socksPort: Int) : Closeable {

	data class Utxo(val txHash: String, val txPos: Int, val height: Int, val value: Long)
	data class HistItem(val txHash: String, val height: Int)

	private val socket: Socket
	private val writer: OutputStream
	private val reader: BufferedReader
	private var id = 0

	init {
		if (socksPort <= 0) throw IOException("Tor is not ready")
		val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
		val s = Socket(proxy)
		try {
			s.connect(InetSocketAddress.createUnresolved(host, port), 30_000)
			s.soTimeout = 40_000
			socket = s
			writer = s.getOutputStream()
			reader = BufferedReader(InputStreamReader(s.getInputStream(),
					StandardCharsets.UTF_8))
			call("server.version", "[\"electrum\",\"1.4\"]")
		} catch (e: Throwable) {
			try { s.close() } catch (ignored: Exception) { }
			throw e
		}
	}

	private fun call(method: String, params: String): String {
		val reqId = ++id
		val line = "{\"id\":$reqId,\"method\":\"$method\",\"params\":$params}\n"
		writer.write(line.toByteArray(StandardCharsets.UTF_8)); writer.flush()
		while (true) {
			val resp = reader.readLine() ?: throw IOException("connection closed")
			if (!resp.contains("\"id\":$reqId")) continue
			if (resp.contains("\"error\":") && !resp.contains("\"error\":null")) {
				throw IOException(strField(resp, "message") ?: "Electrum error")
			}
			return resp
		}
	}

	/** Current chain tip height (for computing confirmations). */
	fun blockHeight(): Int {
		val r = call("blockchain.headers.subscribe", "[]")
		return (numField(r, "height") ?: 0L).toInt()
	}

	fun getBalanceSat(scriptHash: String): Long {
		val r = call("blockchain.scripthash.get_balance", "[\"$scriptHash\"]")
		return (numField(r, "confirmed") ?: 0L) + (numField(r, "unconfirmed") ?: 0L)
	}

	fun getHistory(scriptHash: String): List<HistItem> {
		val r = call("blockchain.scripthash.get_history", "[\"$scriptHash\"]")
		return objects(r).mapNotNull { o ->
			val h = strField(o, "tx_hash") ?: return@mapNotNull null
			HistItem(h, (numField(o, "height") ?: 0L).toInt())
		}
	}

	fun listUnspent(scriptHash: String): List<Utxo> {
		val r = call("blockchain.scripthash.listunspent", "[\"$scriptHash\"]")
		return objects(r).mapNotNull { o ->
			val h = strField(o, "tx_hash") ?: return@mapNotNull null
			Utxo(h, (numField(o, "tx_pos") ?: 0L).toInt(),
					(numField(o, "height") ?: 0L).toInt(), numField(o, "value") ?: 0L)
		}
	}

	fun getTransaction(txid: String): String {
		val r = call("blockchain.transaction.get", "[\"$txid\"]")
		return strField(r, "result") ?: throw IOException("no tx")
	}

	fun broadcast(rawHex: String): String {
		val r = call("blockchain.transaction.broadcast", "[\"$rawHex\"]")
		val result = strField(r, "result") ?: throw IOException("broadcast failed")
		if (!result.matches(Regex("^[0-9a-fA-F]{64}$")))
			throw IOException("Broadcast rejected: $result")
		return result
	}

	/** Estimated fee rate (BTC/kB) for the given confirmation target. */
	fun estimateFeeBtcPerKb(blocks: Int): Double {
		val r = call("blockchain.estimatefee", "[$blocks]")
		val v = r.substringAfter("\"result\":").takeWhile { it != ',' && it != '}' }
		return v.trim().toDoubleOrNull() ?: 0.0
	}

	override fun close() {
		try { socket.close() } catch (e: Exception) {}
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

	private fun objects(json: String): List<String> {
		val start = json.indexOf("\"result\":[")
		if (start < 0) return emptyList()
		return Regex("\\{[^{}]*}").findAll(json.substring(start)).map { it.value }.toList()
	}
}

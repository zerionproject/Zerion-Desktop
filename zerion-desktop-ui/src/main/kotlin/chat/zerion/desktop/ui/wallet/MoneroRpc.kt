package chat.zerion.desktop.ui.wallet


import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * Drives the bundled official `monero-wallet-rpc` binary as a subprocess and
 * talks to it over localhost JSON-RPC. The wallet's keys/scanning/signing are
 * handled by Monero's own audited code (never re-implemented); the binary
 * reaches the remote Monero node through the app's Tor SOCKS proxy so the user's
 * IP is never exposed. One instance manages one running wallet.
 */
internal class MoneroRpc(
		private val walletDir: File,
		private val daemon: String,
		private val socksPort: Int,
		private val trusted: Boolean = false,
) {
	private var process: Process? = null
	private var port = 0
	private val json = "application/json".toMediaType()
	private var rpcUser = "u"
	@Volatile private var rpcPass = ""
	private var loginConfig: File? = null
	private var shutdownHook: Thread? = null
	private val digestAuth = MoneroDigestAuthenticator({ rpcUser }, { rpcPass })
	private val client = OkHttpClient.Builder()
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(180, TimeUnit.SECONDS)
			.callTimeout(240, TimeUnit.SECONDS)
			.authenticator(digestAuth)
			.build()
	private val quickClient = OkHttpClient.Builder()
			.connectTimeout(12, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.callTimeout(25, TimeUnit.SECONDS)
			.authenticator(digestAuth)
			.build()

	val isRunning: Boolean get() = process?.isAlive == true

	fun start() {
		val bin = binary() ?: throw IOException("monero-wallet-rpc is not bundled")
		walletDir.mkdirs()
		port = freePort()
		rpcUser = randomToken()
		rpcPass = randomToken()
		val cfg = writeLoginConfig()
		loginConfig = cfg
		val args = mutableListOf(
				bin.absolutePath,
				"--config-file", cfg.absolutePath,
				"--wallet-dir", walletDir.absolutePath,
				"--rpc-bind-ip", "127.0.0.1",
				"--rpc-bind-port", port.toString(),
				"--log-file",
				if (System.getProperty("os.name").lowercase().contains("win"))
					"NUL" else "/dev/null",
				"--log-level", "0",
				"--non-interactive")
		if (daemon.isNotBlank()) {
			if (socksPort <= 0) throw IOException("Tor is not ready")
			args.add("--daemon-address"); args.add(daemon)
			if (trusted) args.add("--trusted-daemon") else args.add("--untrusted-daemon")
			args.add("--proxy"); args.add("127.0.0.1:$socksPort")
			args.add("--daemon-ssl-allow-any-cert")
		}
		val p = ProcessBuilder(args)
				.directory(walletDir)
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start()
		process = p
		val hook = Thread { try { p.destroyForcibly() } catch (e: Exception) { } }
		try {
			Runtime.getRuntime().addShutdownHook(hook)
			shutdownHook = hook
		} catch (e: Exception) {
		}
		try {
			waitReady()
		} finally {
			shredLoginConfig()
		}
	}

	private fun writeLoginConfig(): File {
		val f = File(walletDir, ".rpc-" + randomToken().take(8))
		f.writeText("rpc-login=$rpcUser:$rpcPass\n", Charsets.US_ASCII)
		try {
			java.nio.file.Files.setPosixFilePermissions(f.toPath(),
					java.util.EnumSet.of(
							java.nio.file.attribute.PosixFilePermission.OWNER_READ,
							java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
		} catch (e: Exception) {
			try {
				val view = java.nio.file.Files.getFileAttributeView(f.toPath(),
						java.nio.file.attribute.AclFileAttributeView::class.java)
				if (view != null) {
					val owner = java.nio.file.Files.getOwner(f.toPath())
					view.acl = listOf(java.nio.file.attribute.AclEntry.newBuilder()
							.setType(java.nio.file.attribute.AclEntryType.ALLOW)
							.setPrincipal(owner)
							.setPermissions(java.util.EnumSet.allOf(
									java.nio.file.attribute.AclEntryPermission::class.java))
							.build())
				}
			} catch (e2: Exception) {
			}
		}
		return f
	}

	private fun shredLoginConfig() {
		val f = loginConfig ?: return
		loginConfig = null
		try {
			val len = f.length()
			if (len > 0) java.io.RandomAccessFile(f, "rws").use { raf ->
				val buf = ByteArray(len.toInt())
				java.security.SecureRandom().nextBytes(buf)
				raf.seek(0); raf.write(buf); raf.fd.sync()
			}
		} catch (e: Exception) {
		}
		f.delete()
	}

	private fun waitReady() {
		val deadline = System.currentTimeMillis() + 45_000
		while (System.currentTimeMillis() < deadline) {
			if (process?.isAlive != true) throw IOException("wallet-rpc exited")
			try { call("get_version", "{}"); return } catch (e: Exception) {}
			try { Thread.sleep(500) } catch (e: InterruptedException) { throw IOException("interrupted") }
		}
		throw IOException("monero-wallet-rpc did not become ready")
	}

	fun stop() {
		try { call("close_wallet", "{}") } catch (e: Exception) {}
		try { process?.destroy() } catch (e: Exception) {}
		try {
			if (process?.waitFor(4, TimeUnit.SECONDS) == false) process?.destroyForcibly()
		} catch (e: Exception) {}
		shutdownHook?.let {
			try { Runtime.getRuntime().removeShutdownHook(it) } catch (e: Exception) {}
		}
		shutdownHook = null
		rpcPass = ""
		shredLoginConfig()
		process = null
	}

	private fun randomToken(): String {
		val b = ByteArray(24)
		java.security.SecureRandom().nextBytes(b)
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
	}

	fun call(method: String, paramsJson: String): String = exec(client, method, paramsJson)

	private fun callQuick(method: String, paramsJson: String): String =
			exec(quickClient, method, paramsJson)

	private fun exec(httpClient: OkHttpClient, method: String, paramsJson: String): String {
		val body = "{\"jsonrpc\":\"2.0\",\"id\":\"0\",\"method\":\"$method\"," +
				"\"params\":$paramsJson}"
		val req = Request.Builder().url("http://127.0.0.1:$port/json_rpc")
				.post(body.toRequestBody(json)).build()
		httpClient.newCall(req).execute().use { resp ->
			val text = resp.body?.string() ?: throw IOException("empty response")
			if (text.contains("\"error\":")) {
				throw IOException(strField(text, "message") ?: "monero-wallet-rpc error")
			}
			return text
		}
	}


	fun createWallet(filename: String, password: String): String =
			call("create_wallet", "{\"filename\":\"$filename\",\"password\":" +
					"\"$password\",\"language\":\"English\"}")

	fun openWallet(filename: String, password: String) =
			call("open_wallet", "{\"filename\":\"$filename\",\"password\":\"$password\"}")

	fun restoreWallet(filename: String, password: String, seed: String,
			restoreHeight: Long) =
			call("restore_deterministic_wallet", "{\"filename\":\"$filename\"," +
					"\"password\":\"$password\",\"seed\":\"$seed\",\"restore_height\":" +
					"$restoreHeight,\"language\":\"English\"}")

	fun mnemonic(): String {
		val r = call("query_key", "{\"key_type\":\"mnemonic\"}")
		return strField(r, "key") ?: throw IOException("no seed")
	}

	fun daemonHeight(): Long {
		val r = callQuick("get_height", "{}")
		return numField(r, "height") ?: 0L
	}

	fun walletHeight(): Long {
		val r = call("get_height", "{}")
		return numField(r, "height") ?: 0L
	}

	fun primaryAddress(accountIndex: Int = 0): String {
		val r = call("get_address", "{\"account_index\":$accountIndex}")
		return strField(r, "address") ?: ""
	}

	fun createSubaddress(accountIndex: Int = 0): String {
		val r = call("create_address", "{\"account_index\":$accountIndex}")
		return strField(r, "address") ?: ""
	}

	data class Balance(val total: BigInteger, val unlocked: BigInteger)

	fun balance(accountIndex: Int = 0): Balance {
		val r = call("get_balance", "{\"account_index\":$accountIndex}")
		return Balance(bigField(r, "balance"), bigField(r, "unlocked_balance"))
	}

	data class Account(val index: Int, val label: String, val baseAddress: String,
			val balance: BigInteger)

	fun accounts(): List<Account> {
		val r = call("get_accounts", "{}")
		val out = mutableListOf<Account>()
		val key = "\"subaddress_accounts\""
		val start = r.indexOf(key)
		if (start < 0) return listOf(Account(0, "Primary", primaryAddress(0), BigInteger.ZERO))
		val bracket = r.indexOf('[', start)
		val body = if (bracket < 0) "" else r.substring(bracket, r.indexOf(']', bracket)
				.coerceAtLeast(bracket))
		Regex("\\{[^{}]*}").findAll(body).forEach { m ->
			val o = m.value
			val idx = numField(o, "account_index")?.toInt() ?: return@forEach
			out.add(Account(idx, strField(o, "label")?.ifBlank { "Account ${idx + 1}" }
					?: "Account ${idx + 1}", strField(o, "base_address") ?: "",
					bigField(o, "balance")))
		}
		return if (out.isEmpty()) listOf(Account(0, "Primary", primaryAddress(0),
				BigInteger.ZERO)) else out.sortedBy { it.index }
	}

	fun createAccount(): Int {
		val r = call("create_account", "{}")
		return numField(r, "account_index")?.toInt() ?: 0
	}

	fun refresh() = call("refresh", "{}")

	private fun jsonEsc(s: String): String {
		val sb = StringBuilder(s.length + 2)
		for (c in s) when {
			c == '"' -> sb.append("\\\"")
			c == '\\' -> sb.append("\\\\")
			c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
			else -> sb.append(c)
		}
		return sb.toString()
	}

	fun validateAddress(address: String): Boolean {
		val r = call("validate_address",
				"{\"address\":\"${jsonEsc(address)}\"}")
		return Regex("\"valid\"\\s*:\\s*true").containsMatchIn(r)
	}

	data class PreparedTransfer(val txHash: String, val fee: BigInteger,
			val amount: BigInteger, val metadata: String)

	fun prepareTransfer(destinations: List<Pair<String, BigInteger>>,
			priority: Int, accountIndex: Int = 0): PreparedTransfer {
		val dests = destinations.joinToString(",") { (addr, amt) ->
			"{\"amount\":$amt,\"address\":\"${jsonEsc(addr)}\"}"
		}
		val r = call("transfer", "{\"destinations\":[$dests]," +
				"\"account_index\":$accountIndex,\"priority\":$priority," +
				"\"do_not_relay\":true,\"get_tx_metadata\":true," +
				"\"get_tx_key\":false}")
		val metadata = strField(r, "tx_metadata")
				?: throw IOException("no tx metadata")
		val hash = strField(r, "tx_hash") ?: ""
		return PreparedTransfer(hash, bigField(r, "fee"), bigField(r, "amount"),
				metadata)
	}

	fun relayTx(metadata: String): String {
		val r = call("relay_tx", "{\"hex\":\"$metadata\"}")
		return strField(r, "tx_hash") ?: throw IOException("relay failed")
	}

	data class Transfer(val hash: String, val amount: BigInteger, val incoming: Boolean,
			val timestamp: Long, val confirmed: Boolean)

	fun transfers(accountIndex: Int = 0): List<Transfer> {
		val r = call("get_transfers", "{\"in\":true,\"out\":true,\"pending\":true," +
				"\"pool\":true,\"account_index\":$accountIndex}")
		val out = mutableListOf<Transfer>()
		parseTransfers(r, "in", true, out)
		parseTransfers(r, "out", false, out)
		parseTransfers(r, "pending", false, out)
		parseTransfers(r, "pool", true, out)
		return out.distinctBy { it.hash }
	}

	private fun parseTransfers(json: String, category: String, incoming: Boolean,
			out: MutableList<Transfer>) {
		val keyPos = json.indexOf("\"$category\"")
		if (keyPos < 0) return
		val start = json.indexOf('[', keyPos)
		if (start < 0) return
		val body = json.substring(start, json.indexOf(']', start).coerceAtLeast(start))
		Regex("\\{[^{}]*}").findAll(body).forEach { m ->
			val o = m.value
			val hash = strField(o, "txid") ?: return@forEach
			val amount = numField(o, "amount")?.let { BigInteger.valueOf(it) } ?: BigInteger.ZERO
			val ts = (numField(o, "timestamp") ?: 0L) * 1000L
			val height = numField(o, "height") ?: 0L
			out.add(Transfer(hash, amount, incoming, ts, height > 0))
		}
	}


	private fun valueStart(json: String, key: String): Int {
		val k = "\"$key\""
		var i = json.indexOf(k); if (i < 0) return -1
		i += k.length
		while (i < json.length && json[i].isWhitespace()) i++
		if (i >= json.length || json[i] != ':') return -1
		i++
		while (i < json.length && json[i].isWhitespace()) i++
		return i
	}

	private fun strField(json: String, key: String): String? {
		val s = valueStart(json, key)
		if (s < 0 || s >= json.length || json[s] != '"') return null
		val start = s + 1
		val end = json.indexOf('"', start)
		return if (end < 0) null else json.substring(start, end)
	}

	private fun numField(json: String, key: String): Long? {
		val s = valueStart(json, key); if (s < 0) return null
		var e = s
		while (e < json.length && (json[e].isDigit() || json[e] == '-')) e++
		return json.substring(s, e).toLongOrNull()
	}

	private fun bigField(json: String, key: String): BigInteger {
		val s = valueStart(json, key); if (s < 0) return BigInteger.ZERO
		var e = s
		while (e < json.length && json[e].isDigit()) e++
		return json.substring(s, e).toBigIntegerOrNull() ?: BigInteger.ZERO
	}

	companion object {
		fun binary(): File? {
			val os = System.getProperty("os.name")?.lowercase() ?: ""
			val isWin = os.contains("win")
			val name = if (isWin) "monero-wallet-rpc.exe" else "monero-wallet-rpc"
			val res = System.getProperty("compose.application.resources.dir")
			val candidates = mutableListOf<File>()
			if (res != null) {
				candidates.add(File(res, "monero/$name"))
				candidates.add(File(res, name))
			}
			val plat = platformDir(os)
			candidates.add(File("appResources/$plat/monero/$name"))
			candidates.add(File("zerion-desktop-ui/appResources/$plat/monero/$name"))
			val bin = candidates.firstOrNull { it.isFile } ?: return null
			if (!isWin && !bin.canExecute()) {
				try { bin.setExecutable(true) } catch (e: Exception) {}
			}
			return bin
		}

		private fun platformDir(os: String): String {
			val arch = System.getProperty("os.arch")?.lowercase() ?: ""
			val arm = arch.contains("aarch64") || arch.contains("arm")
			return when {
				os.contains("win") -> "windows-x64"
				os.contains("mac") || os.contains("darwin") ->
					if (arm) "macos-arm64" else "macos-x64"
				else -> if (arm) "linux-arm64" else "linux-x64"
			}
		}

		fun freePort(): Int = ServerSocket(0).use { it.localPort }
	}
}

internal class MoneroDigestAuthenticator(
		private val user: () -> String,
		private val pass: () -> String,
) : Authenticator {

	override fun authenticate(route: Route?, response: Response): Request? {
		if (response.request.header("Authorization") != null) return null
		val challenge = response.header("WWW-Authenticate") ?: return null
		if (!challenge.startsWith("Digest", ignoreCase = true)) return null
		val p = parse(challenge)
		val realm = p["realm"] ?: return null
		val nonce = p["nonce"] ?: return null
		val qop = p["qop"]
		val opaque = p["opaque"]
		val uri = response.request.url.encodedPath
		val method = response.request.method
		val ha1 = md5("${user()}:$realm:${pass()}")
		val ha2 = md5("$method:$uri")
		val nc = "00000001"
		val cnonce = randomHex(8)
		val resp = if (qop != null)
			md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
		else md5("$ha1:$nonce:$ha2")
		val sb = StringBuilder("Digest username=\"${user()}\", realm=\"$realm\", ")
		sb.append("nonce=\"$nonce\", uri=\"$uri\", response=\"$resp\", algorithm=MD5")
		if (qop != null) sb.append(", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
		if (opaque != null) sb.append(", opaque=\"$opaque\"")
		return response.request.newBuilder()
				.header("Authorization", sb.toString()).build()
	}

	private fun parse(header: String): Map<String, String> {
		val out = HashMap<String, String>()
		val body = header.substringAfter("Digest").trim()
		val regex = Regex("""(\w+)=("([^"]*)"|([^,]*))""")
		for (m in regex.findAll(body)) {
			val key = m.groupValues[1]
			val value = if (m.groupValues[3].isNotEmpty() || m.value.contains("\"\""))
				m.groupValues[3] else m.groupValues[4].trim()
			out[key] = value
		}
		return out
	}

	private fun md5(s: String): String =
			java.security.MessageDigest.getInstance("MD5")
					.digest(s.toByteArray(Charsets.UTF_8))
					.joinToString("") { "%02x".format(it) }

	private fun randomHex(bytes: Int): String {
		val b = ByteArray(bytes)
		java.security.SecureRandom().nextBytes(b)
		return b.joinToString("") { "%02x".format(it) }
	}
}

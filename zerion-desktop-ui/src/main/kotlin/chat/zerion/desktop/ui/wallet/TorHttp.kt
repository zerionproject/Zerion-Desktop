package chat.zerion.desktop.ui.wallet

import okhttp3.OkHttpClient
import okhttp3.Request

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Best-effort HTTP GET over the app's Tor SOCKS proxy, used for optional
 * enrichment (tx history from a block explorer, fiat prices). Returns null on
 * any failure so these extras never break the wallet, and never falls back to a
 * direct (non-Tor) connection.
 */
internal object TorHttp {

	fun get(url: String, socksPort: Int): String? {
		if (socksPort <= 0) return null
		return try {
			val client = OkHttpClient.Builder()
					.proxy(Proxy(Proxy.Type.SOCKS,
							InetSocketAddress("127.0.0.1", socksPort)))
					.connectTimeout(30, TimeUnit.SECONDS)
					.readTimeout(45, TimeUnit.SECONDS)
					.callTimeout(60, TimeUnit.SECONDS)
					.build()
			val req = Request.Builder().url(url)
					.header("Accept", "application/json").build()
			client.newCall(req).execute().use { resp ->
				if (!resp.isSuccessful) null else resp.body?.string()
			}
		} catch (e: Exception) {
			null
		}
	}
}

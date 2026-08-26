package chat.zerion.desktop.ui.wallet

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-use authorization state machine for Monero sends. A prepared, not yet
 * relayed transaction is stored under a fingerprint that binds the exact tx
 * metadata, destinations, amount and fee. Confirmation requires prior successful
 * authentication, claims the prepared entry atomically so it can be relayed at
 * most once, and never reports a relay failure as sent. Preparing a new send or
 * cancelling clears any prior authorization, so changing the plan invalidates it
 * and a locked/restarted wallet holds nothing.
 */
internal class XmrSendAuthorizer {

	data class Prepared(val fingerprint: String, val metadata: String,
			val amountAtomic: BigInteger, val feeAtomic: BigInteger,
			val destinations: List<Pair<String, String>>)

	enum class Result { AUTH_FAILED, EXPIRED, RELAY_FAILED, SENT }

	private val prepared = ConcurrentHashMap<String, Prepared>()

	fun cancel() {
		prepared.clear()
	}

	fun store(p: Prepared) {
		prepared.clear()
		prepared[p.fingerprint] = p
	}

	fun pending(fingerprint: String): Boolean = prepared.containsKey(fingerprint)

	fun confirm(fingerprint: String, authenticated: Boolean,
			relay: (String) -> Unit): Result {
		if (!authenticated) return Result.AUTH_FAILED
		val p = prepared.remove(fingerprint) ?: return Result.EXPIRED
		return try {
			relay(p.metadata)
			Result.SENT
		} catch (e: Exception) {
			Result.RELAY_FAILED
		}
	}
}

package chat.zerion.desktop.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.math.BigInteger

class XmrSendAuthorizerTest {

	private fun prep(fp: String, meta: String = "meta-$fp") =
			XmrSendAuthorizer.Prepared(fp, meta, BigInteger.TEN, BigInteger.ONE,
					listOf("addr" to "1.0"))

	@Test
	fun confirmWithoutPreparationCannotSend() {
		val a = XmrSendAuthorizer()
		val relayed = mutableListOf<String>()
		val r = a.confirm("fp", true) { relayed.add(it) }
		assertEquals(XmrSendAuthorizer.Result.EXPIRED, r)
		assertTrue("nothing may be relayed without preparation", relayed.isEmpty())
	}

	@Test
	fun wrongCredentialSendsNothingAndDoesNotConsumeAuthorization() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp"))
		val relayed = mutableListOf<String>()
		val r = a.confirm("fp", false) { relayed.add(it) }
		assertEquals(XmrSendAuthorizer.Result.AUTH_FAILED, r)
		assertTrue(relayed.isEmpty())
		assertTrue("a wrong password must not burn the authorization",
				a.pending("fp"))
	}

	@Test
	fun authorizationIsSingleUse() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp"))
		val relayed = mutableListOf<String>()
		assertEquals(XmrSendAuthorizer.Result.SENT,
				a.confirm("fp", true) { relayed.add(it) })
		assertEquals(XmrSendAuthorizer.Result.EXPIRED,
				a.confirm("fp", true) { relayed.add(it) })
		assertEquals("relay must happen exactly once", 1, relayed.size)
	}

	@Test
	fun doubleSubmitRelaysOnlyOnce() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp"))
		val count = java.util.concurrent.atomic.AtomicInteger()
		val threads = (0 until 8).map {
			Thread { a.confirm("fp", true) { count.incrementAndGet() } }
		}
		threads.forEach { it.start() }
		threads.forEach { it.join() }
		assertEquals("concurrent confirms must relay once", 1, count.get())
	}

	@Test
	fun changingThePlanInvalidatesTheOldAuthorization() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp-old"))
		a.store(prep("fp-new"))
		val relayed = mutableListOf<String>()
		assertEquals(XmrSendAuthorizer.Result.EXPIRED,
				a.confirm("fp-old", true) { relayed.add(it) })
		assertTrue(relayed.isEmpty())
		assertEquals(XmrSendAuthorizer.Result.SENT,
				a.confirm("fp-new", true) { relayed.add(it) })
	}

	@Test
	fun cancelClearsAuthorization() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp"))
		a.cancel()
		val relayed = mutableListOf<String>()
		assertEquals(XmrSendAuthorizer.Result.EXPIRED,
				a.confirm("fp", true) { relayed.add(it) })
		assertTrue(relayed.isEmpty())
		assertFalse(a.pending("fp"))
	}

	@Test
	fun relayFailureIsNeverReportedAsSent() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp"))
		val r = a.confirm("fp", true) { throw java.io.IOException("timeout") }
		assertEquals(XmrSendAuthorizer.Result.RELAY_FAILED, r)
		assertFalse("a failed relay must still consume the single-use plan",
				a.pending("fp"))
	}

	@Test
	fun relayReceivesExactlyThePreparedMetadata() {
		val a = XmrSendAuthorizer()
		a.store(prep("fp", "the-exact-metadata"))
		var seen: String? = null
		a.confirm("fp", true) { seen = it }
		assertEquals("the-exact-metadata", seen)
	}
}

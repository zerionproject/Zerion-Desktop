package chat.zerion.desktop.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.File
import java.nio.file.Files

class MoneroResidueSweepTest {

	private val leakAddress =
			"43EcrKsYkGSFVi3vNooBgpjetZXkuMgW84EGM5vmFHGZdN8YHubca95Bce8Qbd89KP2Du3u9LNzom1WdSJaHqHojSDyixhG"

	private fun containsSentinel(root: File, sentinel: String): Boolean {
		val needle = sentinel.toByteArray(Charsets.US_ASCII)
		return root.walkTopDown().filter { it.isFile }.any { f ->
			val bytes = f.readBytes()
			bytes.indices.any { i ->
				i + needle.size <= bytes.size &&
						bytes.copyOfRange(i, i + needle.size).contentEquals(needle)
			}
		}
	}

	@Test
	fun sweepRemovesLogsAndTempWalletsButKeepsRealWallet() {
		val base = Files.createTempDirectory("monero-fixture").toFile()
		try {
			val realWallet = File(base, "50a4160b-169a-4cd1-9ad1-70ceb8b96b10")
			realWallet.mkdirs()
			File(realWallet, "w").writeText("real wallet cache")
			File(realWallet, "w.keys").writeText("real encrypted keystore")
			File(realWallet, "wallet-rpc.log").writeText(
					"log opened address $leakAddress payment received height 100")

			val tmpWallet = File(base, "tmp-2408461d-8fbe-44e4-80db-eb78d5f97aa0")
			tmpWallet.mkdirs()
			File(tmpWallet, "gen.keys").writeText("throwaway generation keystore")
			File(tmpWallet, "wallet-rpc.log").writeText("tmp log $leakAddress")

			val chkWallet = File(base, "chk-1a32d7e4-9a56-43f2-88d3-3f13d66f94c7")
			chkWallet.mkdirs()
			File(chkWallet, "chk.keys").writeText("validation keystore")

			assertTrue("fixture should contain the address before sweep",
					containsSentinel(base, leakAddress))

			MoneroResidue.sweep(base)

			assertFalse("tmp- working dir must be wiped", tmpWallet.exists())
			assertFalse("chk- working dir must be wiped", chkWallet.exists())
			assertFalse("real wallet log must be shredded",
					File(realWallet, "wallet-rpc.log").exists())
			assertTrue("real wallet cache must survive", File(realWallet, "w").exists())
			assertTrue("real keystore must survive", File(realWallet, "w.keys").exists())
			assertFalse("no monero address may remain anywhere under the base",
					containsSentinel(base, leakAddress))
		} finally {
			base.deleteRecursively()
		}
	}

	@Test
	fun sweepIsSafeOnMissingAndEmptyDirs() {
		val missing = File(Files.createTempDirectory("m").toFile(), "nope")
		MoneroResidue.sweep(missing)
		assertFalse(missing.exists())

		val empty = Files.createTempDirectory("m-empty").toFile()
		try {
			MoneroResidue.sweep(empty)
			assertTrue(empty.exists())
			assertEquals(0, empty.listFiles()?.size ?: 0)
		} finally {
			empty.deleteRecursively()
		}
	}
}

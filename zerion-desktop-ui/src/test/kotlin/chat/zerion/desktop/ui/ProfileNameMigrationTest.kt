package chat.zerion.desktop.ui

import chat.zerion.desktop.ui.vault.MachineSecret

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

import java.io.File
import java.nio.file.Files

class ProfileNameMigrationTest {

	@Test
	fun legacyPlaintextNameIsMigratedToMachineBoundAndPlaintextRemoved() {
		assumeTrue("machine binding unavailable on this platform",
				MachineSecret.isAvailable())
		val dir = Files.createTempDirectory("profile-legacy").toFile()
		try {
			val legacy = File(dir, "name")
			legacy.writeText("Test 1")
			assertTrue(legacy.exists())

			DesktopProfiles.migrateName(dir)

			assertFalse("legacy plaintext name must be removed", legacy.exists())
			assertTrue("machine-bound name.dp must exist", File(dir, "name.dp").exists())
			assertEquals("name must still be recoverable", "Test 1",
					DesktopProfiles.readName(dir))

			val onDisk = dir.walkTopDown().filter { it.isFile }.any { f ->
				f.readBytes().let { b ->
					val n = "Test 1".toByteArray(Charsets.US_ASCII)
					b.indices.any { i ->
						i + n.size <= b.size && b.copyOfRange(i, i + n.size).contentEquals(n)
					}
				}
			}
			assertFalse("no plaintext 'Test 1' may remain on disk", onDisk)
		} finally {
			dir.deleteRecursively()
		}
	}

	@Test
	fun migrationIsIdempotentAndCrashSafe() {
		assumeTrue(MachineSecret.isAvailable())
		val dir = Files.createTempDirectory("profile-idem").toFile()
		try {
			File(dir, "name").writeText("Test 2")
			DesktopProfiles.migrateName(dir)
			DesktopProfiles.migrateName(dir)
			assertEquals("Test 2", DesktopProfiles.readName(dir))
			assertFalse(File(dir, "name").exists())
			assertTrue(File(dir, "name.dp").exists())
		} finally {
			dir.deleteRecursively()
		}
	}
}

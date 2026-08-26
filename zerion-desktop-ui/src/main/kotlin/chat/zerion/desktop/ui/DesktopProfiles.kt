package chat.zerion.desktop.ui

import chat.zerion.desktop.DesktopBoot
import chat.zerion.desktop.ui.vault.MachineSecret

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Local profile registry for multiple identities. Each profile is a separate
 * account in its own directory under <dataDir>/profiles/<id>/, with its own
 * encrypted database, keys and Tor state, so a throwaway or secondary profile
 * shares nothing with the main one. A profile's display name is needed by the
 * picker before unlock; where machine-binding is available (DPAPI on Windows)
 * it is stored wrapped so the name never sits on disk as plaintext, and the
 * same logged-in user decrypts it transparently for the picker. Where no
 * machine-binding exists it falls back to a plaintext name file. Everything
 * else stays encrypted.
 */
internal object DesktopProfiles {

	data class Profile(val id: String, val name: String, val dataDir: File)

	private fun base(): File = DesktopBoot.defaultDataDir()

	private fun profilesRoot(): File = File(base(), "profiles")

	private fun lastActiveFile(): File = File(base(), "last_profile")

	fun list(): List<Profile> {
		val dirs = profilesRoot().listFiles { f -> f.isDirectory }
				?: return emptyList()
		return dirs.map { d ->
			val name = readName(d).ifEmpty { d.name }
			migrateName(d)
			Profile(d.name, name, d)
		}.sortedBy { it.name.lowercase() }
	}

	internal fun migrateName(dir: File) {
		val plain = File(dir, "name")
		if (!plain.exists() || !MachineSecret.isAvailable()) return
		try {
			val clean = plain.readText().trim()
			val blob = MachineSecret.wrap(clean.toByteArray(StandardCharsets.UTF_8))
			val wrapped = File(dir, "name.dp")
			wrapped.writeBytes(blob)
			val back = String(MachineSecret.unwrap(wrapped.readBytes()),
					StandardCharsets.UTF_8).trim()
			if (back != clean) {
				wrapped.delete()
				return
			}
			overwriteAndDelete(plain)
		} catch (ignored: Throwable) {
		}
	}

	private fun overwriteAndDelete(f: File) {
		try {
			val len = f.length()
			if (len > 0) {
				val rnd = java.security.SecureRandom()
				val buf = ByteArray(len.toInt())
				rnd.nextBytes(buf)
				f.writeBytes(buf)
			}
		} catch (ignored: Throwable) {
		}
		f.delete()
	}

	fun create(name: String): Profile {
		val clean = sanitizeName(name, 80)
		val id = UUID.randomUUID().toString().replace("-", "").take(12)
		val dir = File(profilesRoot(), id)
		dir.mkdirs()
		restrictToOwner(dir)
		writeName(dir, clean)
		return Profile(id, clean, dir)
	}

	internal fun readName(dir: File): String {
		val wrapped = File(dir, "name.dp")
		if (wrapped.exists()) {
			try {
				val bytes = MachineSecret.unwrap(wrapped.readBytes())
				return String(bytes, StandardCharsets.UTF_8).trim()
			} catch (ignored: Throwable) {
			}
		}
		val plain = File(dir, "name")
		return if (plain.exists()) plain.readText().trim() else ""
	}

	private fun writeName(dir: File, clean: String) {
		if (MachineSecret.isAvailable()) {
			try {
				val blob = MachineSecret.wrap(
						clean.toByteArray(StandardCharsets.UTF_8))
				File(dir, "name.dp").writeBytes(blob)
				File(dir, "name").delete()
				return
			} catch (ignored: Throwable) {
			}
		}
		File(dir, "name").writeText(clean)
	}

	fun delete(id: String) {
		secureWipe(File(profilesRoot(), id))
		if (lastActive() == id) lastActiveFile().delete()
	}


	private fun duressFile(dir: File): File = File(dir, ".dp")

	fun hasDuress(dir: File): Boolean = duressFile(dir).exists()

	fun setDuress(dir: File, password: CharArray) {
		try {
			duressFile(dir).writeText(ChatLock.derive(password))
		} finally {
			java.util.Arrays.fill(password, ' ')
		}
	}

	fun removeDuress(dir: File) {
		duressFile(dir).delete()
	}

	fun isDuress(dir: File, password: CharArray): Boolean {
		val f = duressFile(dir)
		if (!f.exists()) return false
		return try {
			ChatLock.verify(password, f.readText())
		} catch (e: Exception) {
			false
		}
	}

	fun secureWipe(dir: File) {
		if (!dir.exists()) return
		val random = java.security.SecureRandom()
		val buf = ByteArray(64 * 1024)
		dir.walkBottomUp().forEach { f ->
			if (f.isFile) {
				try {
					val len = f.length()
					java.io.RandomAccessFile(f, "rws").use { raf ->
						raf.seek(0)
						var written = 0L
						while (written < len) {
							random.nextBytes(buf)
							val n = minOf(buf.size.toLong(),
									len - written).toInt()
							raf.write(buf, 0, n)
							written += n
						}
						raf.fd.sync()
					}
				} catch (ignored: Exception) {
				}
				f.delete()
			}
		}
		dir.deleteRecursively()
	}

	fun lastActive(): String? {
		val f = lastActiveFile()
		return if (f.exists()) f.readText().trim().ifEmpty { null } else null
	}

	fun setLastActive(id: String) {
		try {
			lastActiveFile().writeText(id)
		} catch (ignored: Exception) {
		}
	}

	private fun restrictToOwner(dir: File) {
		try {
			java.nio.file.Files.setPosixFilePermissions(dir.toPath(),
					java.util.EnumSet.of(
							java.nio.file.attribute.PosixFilePermission.OWNER_READ,
							java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
							java.nio.file.attribute.PosixFilePermission
									.OWNER_EXECUTE))
		} catch (ignored: Exception) {
		}
	}
}

package chat.zerion.desktop.ui

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom

/*
 * Opens decrypted content (a received attachment, a vault document or media
 * item) with the operating system's default handler. The plaintext is written
 * to a per-session, owner-only cache directory with a random file name that
 * carries only the file extension, never the original name, so the temp-dir
 * listing leaks nothing. The cache is swept when the vault locks and on a clean
 * exit; a best-effort overwrite precedes each delete.
 */
object OpenCache {

	private val random = SecureRandom()

	fun purgeStale() {
		try {
			val tmp = File(System.getProperty("java.io.tmpdir"))
			tmp.listFiles { f -> f.isDirectory && f.name.startsWith("zerion-open-") }
					?.forEach { old -> overwriteAndDelete(old) }
		} catch (e: Exception) {
		}
	}

	private val dir: File by lazy {
		val tmp = File(System.getProperty("java.io.tmpdir"))
		purgeStale()
		val base = File(tmp, "zerion-open-" + randomHex(8))
		base.mkdirs()
		restrictToOwner(base)
		base.deleteOnExit()
		Runtime.getRuntime().addShutdownHook(Thread { sweep() })
		base
	}

	private fun restrictToOwner(f: File) {
		try {
			Files.setPosixFilePermissions(f.toPath(),
					PosixFilePermissions.fromString(
							if (f.isDirectory) "rwx------" else "rw-------"))
			return
		} catch (e: Exception) {
		}
		try {
			val path = f.toPath()
			val view = Files.getFileAttributeView(path,
					java.nio.file.attribute.AclFileAttributeView::class.java) ?: return
			val owner = Files.getOwner(path)
			val entry = java.nio.file.attribute.AclEntry.newBuilder()
					.setType(java.nio.file.attribute.AclEntryType.ALLOW)
					.setPrincipal(owner)
					.setPermissions(java.util.EnumSet.allOf(
							java.nio.file.attribute.AclEntryPermission::class.java))
					.build()
			view.acl = listOf(entry)
		} catch (e: Exception) {
		}
	}

	fun open(nameHint: String, bytes: ByteArray): Boolean {
		return try {
			val ext = nameHint.substringAfterLast('.', "")
					.filter { it.isLetterOrDigit() }.take(8)
			val name = randomHex(16) + if (ext.isNotEmpty()) ".$ext" else ""
			val f = File(dir, name)
			f.writeBytes(bytes)
			f.deleteOnExit()
			restrictToOwner(f)
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(f)
				true
			} else false
		} catch (e: Exception) {
			false
		}
	}

	fun sweep() {
		try {
			dir.listFiles()?.forEach { overwriteAndDelete(it) }
		} catch (e: Exception) {
		}
	}

	private fun overwriteAndDelete(f: File) {
		try {
			if (f.isDirectory) {
				f.listFiles()?.forEach { overwriteAndDelete(it) }
				f.delete()
				return
			}
			val len = f.length()
			if (len > 0) {
				val buf = ByteArray(minOf(len, 1L shl 16).toInt())
				random.nextBytes(buf)
				f.outputStream().use { out ->
					var written = 0L
					while (written < len) {
						val n = minOf(buf.size.toLong(), len - written).toInt()
						out.write(buf, 0, n)
						written += n
					}
				}
			}
		} catch (e: Exception) {
		}
		f.delete()
	}

	private fun randomHex(bytes: Int): String {
		val b = ByteArray(bytes)
		random.nextBytes(b)
		return b.joinToString("") { "%02x".format(it) }
	}
}

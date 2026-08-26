package chat.zerion.desktop.ui.wallet

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

internal object MoneroResidue {

	fun sweep(base: File) {
		if (!base.exists()) return
		base.listFiles()?.forEach { k ->
			if (k.isDirectory && (k.name.startsWith("tmp-") || k.name.startsWith("chk-")))
				wipeTree(k)
		}
		base.walkTopDown().forEach { f ->
			if (f.isFile && (f.name == "wallet-rpc.log" ||
							f.name.startsWith(".rpc-"))) shred(f)
		}
	}

	fun wipeTree(dir: File) {
		if (!dir.exists()) return
		dir.walkBottomUp().forEach { f -> if (f.isFile) shred(f) }
		dir.deleteRecursively()
	}

	fun shred(f: File) {
		try {
			val len = f.length()
			if (len > 0) RandomAccessFile(f, "rws").use { raf ->
				val rnd = SecureRandom()
				val buf = ByteArray(minOf(len, 1L shl 16).toInt())
				var w = 0L
				while (w < len) {
					rnd.nextBytes(buf)
					val n = minOf(buf.size.toLong(), len - w).toInt()
					raf.write(buf, 0, n); w += n
				}
				raf.fd.sync()
			}
		} catch (e: Exception) {
		}
		f.delete()
	}
}

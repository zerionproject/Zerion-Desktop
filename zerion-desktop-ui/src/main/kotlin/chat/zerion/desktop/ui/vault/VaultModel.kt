package chat.zerion.desktop.ui.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

import java.io.File
import java.util.Arrays

private const val HIDDEN_NOTE_NAME = "\u0000zhn"

/**
 * UI-facing state holder for the vault. Wraps [VaultManager]; every manager call
 * (crypto/file I/O) runs on [Dispatchers.IO] and state is updated back on the
 * Swing thread. A slow poll reflects the engine's 30-minute auto-lock into the
 * UI phase.
 */
class VaultModel(vaultRoot: File, socksPort: Int) {

	enum class Phase { NOT_CREATED, LOCKED, UNLOCKED }

	data class Entry(val id: String, val title: String, val subtitle: String,
			val timestamp: Long, val size: Long = 0L)

	private val manager = VaultManager(vaultRoot)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

	val wallet = chat.zerion.desktop.ui.wallet.WalletModel(manager, socksPort,
			File(vaultRoot.parentFile ?: vaultRoot, "monero"))

	var phase by mutableStateOf(
			if (manager.vaultExists()) Phase.LOCKED else Phase.NOT_CREATED)
		private set
	var passwords by mutableStateOf<List<Entry>>(emptyList())
		private set
	var notes by mutableStateOf<List<Entry>>(emptyList())
		private set
	var documents by mutableStateOf<List<Entry>>(emptyList())
		private set
	var media by mutableStateOf<List<Entry>>(emptyList())
		private set
	var busy by mutableStateOf(false)
		private set
	var error by mutableStateOf<String?>(null)
		private set
	var lockoutSeconds by mutableStateOf(0L)
		private set

	init {
		scope.launch {
			while (true) {
				delay(15_000)
				if (phase == Phase.UNLOCKED && !manager.isUnlocked) lockToUi()
			}
		}
	}

	fun createVault(password: CharArray, onDone: (Boolean) -> Unit) {
		busy = true; error = null
		scope.launch {
			val ok = io { manager.createVault(password); true } ?: false
			Arrays.fill(password, ' ')
			busy = false
			if (ok) { phase = Phase.UNLOCKED; refresh() }
			else error = "Couldn't create the vault."
			onDone(ok)
		}
	}

	fun unlock(password: CharArray, onDone: (Boolean) -> Unit) {
		busy = true; error = null
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try { manager.unlockVault(password) } catch (e: Exception) { false }
				finally { Arrays.fill(password, ' ') }
			}
			busy = false
			if (ok) { phase = Phase.UNLOCKED; error = null; refresh() }
			else {
				lockoutSeconds = withContext(Dispatchers.IO) {
					manager.lockoutSeconds()
				}
				error = if (lockoutSeconds > 0)
					"Too many attempts. Try again in ${lockoutSeconds}s."
				else "Incorrect vault password."
			}
			onDone(ok)
		}
	}

	fun lock() {
		scope.launch { withContext(Dispatchers.IO) { manager.lockVault() } }
		lockToUi()
	}

	private fun lockToUi() {
		phase = Phase.LOCKED
		passwords = emptyList()
		notes = emptyList()
		documents = emptyList()
		media = emptyList()
		wallet.clearSecrets()
		chat.zerion.desktop.ui.OpenCache.sweep()
	}

	fun onActivity() {
		scope.launch { withContext(Dispatchers.IO) { manager.updateActivity() } }
	}

	private fun refresh() {
		scope.launch {
			val items = io { manager.listItems() } ?: emptyList()
			passwords = items.filter { it.type == VaultItemType.PASSWORD }
					.map { Entry(it.id, it.name, "", it.modifiedTimestamp) }
			val noteItems = items.filter { it.type == VaultItemType.NOTE }
			notes = noteItems.filter { it.name != HIDDEN_NOTE_NAME }
					.map { Entry(it.id, it.name, "", it.modifiedTimestamp) }
			hiddenNoteIds = noteItems.filter { it.name == HIDDEN_NOTE_NAME }
					.map { it.id }
			documents = items.filter {
				it.type == VaultItemType.DOCUMENT || it.type == VaultItemType.AUDIO
			}.map { Entry(it.id, it.name, "", it.modifiedTimestamp, it.size) }
			media = items.filter {
				it.type == VaultItemType.IMAGE || it.type == VaultItemType.VIDEO
			}.map { Entry(it.id, it.name, "", it.modifiedTimestamp, it.size) }
		}
	}


	private val imageExts = setOf("jpg", "jpeg", "png", "gif", "bmp")
	private val videoExts = setOf("mp4", "m4v")
	private val MAX_IMPORT_BYTES = 50L * 1024 * 1024

	fun addDocument(file: File, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				if (file.length() > MAX_IMPORT_BYTES) return@io false
				val bytes = if (file.extension.lowercase() == "pdf")
					try {
						chat.zerion.desktop.ui.DocScrubber.scrubPdf(file.readBytes())
					} catch (e: Exception) {
						file.readBytes()
					}
				else file.readBytes()
				manager.addItem(VaultItemType.DOCUMENT, file.name, bytes)
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}

	fun addMedia(file: File, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				if (file.length() > MAX_IMPORT_BYTES) return@io false
				val ext = file.extension.lowercase()
				when {
					ext in imageExts -> {
						val scrubbed = chat.zerion.desktop.ui.ImageScrubber
								.scrubToJpeg(file.readBytes())
						manager.addItem(VaultItemType.IMAGE,
								file.nameWithoutExtension + ".jpg", scrubbed)
					}
					ext == "mp4" || ext == "m4v" -> {
						val scrubbed = chat.zerion.desktop.ui.VideoScrubber
								.scrubMp4(file)
						manager.addItem(VaultItemType.VIDEO,
								file.nameWithoutExtension + ".mp4", scrubbed)
					}
					else -> return@io false
				}
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}

	fun isVideoName(name: String): Boolean =
			name.substringAfterLast('.', "").lowercase() in videoExts

	fun loadItemBytes(id: String, onResult: (ByteArray?) -> Unit) {
		scope.launch { onResult(io { manager.getItemContent(id) }) }
	}

	fun exportItem(id: String, dest: File, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				dest.writeBytes(manager.getItemContent(id)); true
			} ?: false
			onDone(ok)
		}
	}

	fun addPassword(entry: PasswordEntry, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				manager.addItem(VaultItemType.PASSWORD,
						entry.title.ifBlank { "Untitled" }, entry.toJsonBytes())
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}

	fun loadPassword(id: String, onResult: (PasswordEntry?) -> Unit) {
		scope.launch {
			onResult(io { PasswordEntry.fromJsonBytes(manager.getItemContent(id)) })
		}
	}

	fun addNote(title: String, text: String, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				manager.addItem(VaultItemType.NOTE, title.ifBlank { "Untitled" },
						text.toByteArray(Charsets.UTF_8))
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}

	fun loadNote(id: String, onResult: (String?) -> Unit) {
		scope.launch {
			onResult(io { String(manager.getItemContent(id), Charsets.UTF_8) })
		}
	}

	fun updateNote(id: String, title: String, text: String, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				manager.updateItem(id, title.ifBlank { "Untitled" },
						text.toByteArray(Charsets.UTF_8))
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}


	data class RevealedNote(val id: String, val title: String, val body: String)

	var hiddenNoteIds by mutableStateOf<List<String>>(emptyList())
		private set

	fun addProtectedNote(secret: CharArray, title: String, body: String,
			onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				manager.addItem(VaultItemType.NOTE, HIDDEN_NOTE_NAME,
						packProtected(secret, title, body))
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}

	fun updateProtectedNote(id: String, secret: CharArray, title: String,
			body: String, onDone: (Boolean) -> Unit) {
		scope.launch {
			val ok = io {
				manager.updateItem(id, HIDDEN_NOTE_NAME,
						packProtected(secret, title, body))
				true
			} ?: false
			if (ok) refresh()
			onDone(ok)
		}
	}

	fun revealHiddenNotes(secret: CharArray, onResult: (List<RevealedNote>) -> Unit) {
		val ids = hiddenNoteIds
		if (ids.isEmpty()) return onResult(emptyList())
		scope.launch {
			val out = io {
				val list = mutableListOf<RevealedNote>()
				for (id in ids) {
					try {
						val blob = manager.getItemContent(id)
						if (blob.size < 16) continue
						val salt = blob.copyOfRange(0, 16)
						val enc = blob.copyOfRange(16, blob.size)
						val key = VaultCrypto.deriveNoteKey(secret, salt)
						val plain = try {
							VaultCrypto.decrypt(
									VaultCrypto.EncryptedData.fromBytes(enc), key,
									ByteArray(0))
						} finally {
							java.util.Arrays.fill(key, 0)
						}
						val buf = java.nio.ByteBuffer.wrap(plain)
						val tl = buf.int
						val tb = ByteArray(tl); buf.get(tb)
						val bb = ByteArray(buf.remaining()); buf.get(bb)
						list.add(RevealedNote(id, String(tb, Charsets.UTF_8),
								String(bb, Charsets.UTF_8)))
					} catch (e: Exception) {
					}
				}
				list
			} ?: emptyList()
			onResult(out)
		}
	}

	private fun packProtected(secret: CharArray, title: String,
			body: String): ByteArray {
		val salt = VaultCrypto.randomBytes(16)
		val key = VaultCrypto.deriveNoteKey(secret, salt)
		try {
			val tb = title.toByteArray(Charsets.UTF_8)
			val bb = body.toByteArray(Charsets.UTF_8)
			val plain = java.nio.ByteBuffer.allocate(4 + tb.size + bb.size)
			plain.putInt(tb.size); plain.put(tb); plain.put(bb)
			val enc = VaultCrypto.encrypt(plain.array(), key, ByteArray(0)).toBytes()
			val out = ByteArray(16 + enc.size)
			System.arraycopy(salt, 0, out, 0, 16)
			System.arraycopy(enc, 0, out, 16, enc.size)
			return out
		} finally {
			java.util.Arrays.fill(key, 0)
		}
	}

	fun deleteItem(id: String) {
		scope.launch {
			io { manager.deleteItem(id) }
			refresh()
		}
	}

	fun changePassword(current: CharArray, next: CharArray,
			onDone: (Boolean) -> Unit) {
		busy = true; error = null
		scope.launch {
			val ok = io {
				manager.verifyCurrentPassword(current) &&
						manager.changePassword(next)
			} ?: false
			Arrays.fill(current, ' ')
			Arrays.fill(next, ' ')
			busy = false
			if (!ok) error = "Couldn't change the vault password."
			onDone(ok)
		}
	}

	fun exportBackup(password: CharArray, dest: File, onDone: (Boolean) -> Unit) {
		busy = true
		scope.launch {
			val ok = io {
				dest.writeBytes(manager.exportVault(password)); true
			} ?: false
			Arrays.fill(password, ' ')
			busy = false
			onDone(ok)
		}
	}

	fun importBackup(password: CharArray, src: File, onDone: (Int) -> Unit) {
		busy = true
		scope.launch {
			val count = io {
				manager.importVault(password, src.readBytes())
			} ?: -1
			Arrays.fill(password, ' ')
			busy = false
			if (count > 0) refresh()
			onDone(count)
		}
	}

	fun wipe(onDone: () -> Unit) {
		scope.launch {
			io { manager.wipeVault() }
			phase = Phase.NOT_CREATED
			passwords = emptyList(); notes = emptyList()
			documents = emptyList(); media = emptyList()
			hiddenNoteIds = emptyList()
			wallet.clearSecrets()
			onDone()
		}
	}

	fun shutdown() {
		wallet.shutdown()
		scope.launch { withContext(Dispatchers.IO) { manager.lockVault() } }
		scope.cancel()
	}

	private suspend fun <T> io(block: () -> T): T? = withContext(Dispatchers.IO) {
		try { block() } catch (e: Exception) { null }
	}
}

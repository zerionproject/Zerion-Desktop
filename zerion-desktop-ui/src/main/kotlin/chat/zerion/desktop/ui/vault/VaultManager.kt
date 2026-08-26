package chat.zerion.desktop.ui.vault

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import java.util.Arrays
import java.util.UUID

/**
 * Headless vault engine for one profile. Stores encrypted items as files under
 * `<vaultRoot>/`, protected by a separate vault password (Argon2id) combined
 * with a DPAPI machine-bound secret (see [MachineSecret]). Content uses
 * envelope encryption: every item gets a fresh random key, AES-256-GCM-encrypts
 * its content, and the item key is itself wrapped by the vault master key.
 *
 * Thread-safety: all mutating operations are synchronized. The master key is
 * held in memory only while unlocked and shredded on lock / auto-lock.
 */
internal class VaultManager(private val vaultRoot: File) {

	private val random = SecureRandom()
	private var masterKey: ByteArray? = null
	private var lastActivity = 0L
	private var failedAttempts = 0
	private var backoffUntil = 0L

	private val headerFile get() = File(vaultRoot, HEADER_FILE)
	private val itemsDir get() = File(vaultRoot, ITEMS_DIR)
	private val throttleFile get() = File(vaultRoot, THROTTLE_FILE)

	init {
		resumeRekeyIfNeeded()
		loadThrottle()
	}

	private fun loadThrottle() {
		try {
			val f = throttleFile
			if (!f.isFile) return
			val parts = f.readText().trim().split(":")
			if (parts.size == 2) {
				failedAttempts = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: 0
				backoffUntil = parts[1].toLongOrNull() ?: 0L
			}
		} catch (_: Throwable) {
		}
	}

	private fun saveThrottle() {
		try {
			writeAtomic(throttleFile,
					"$failedAttempts:$backoffUntil".toByteArray(Charsets.UTF_8))
		} catch (_: Throwable) {
		}
	}

	val isUnlocked: Boolean
		@Synchronized get() {
			if (masterKey == null) return false
			if (System.currentTimeMillis() - lastActivity > AUTO_LOCK_MS) {
				lockVault()
				return false
			}
			return true
		}

	/** Seconds the caller must wait before another unlock attempt (0 if none). */
	@Synchronized
	fun lockoutSeconds(): Long {
		val remain = backoffUntil - System.currentTimeMillis()
		return if (remain > 0) (remain / 1000) + 1 else 0
	}

	fun vaultExists(): Boolean = headerFile.isFile

	@Synchronized
	fun createVault(password: CharArray) {
		require(!vaultExists()) { "vault already exists" }
		vaultRoot.mkdirs()
		itemsDir.mkdirs()
		val salt = VaultCrypto.randomBytes(32)
		val randomSecret = VaultCrypto.randomBytes(32)
		val params = VaultArgon2.choose()
		val passwordKey = VaultArgon2.deriveKey(password, salt, params)
		val machineBound = MachineSecret.isAvailable()
		val wrapped = if (machineBound) MachineSecret.wrap(randomSecret)
				else ByteArray(0)
		val effectiveSecret = if (machineBound) randomSecret else ByteArray(32)
		val master = VaultCrypto.deriveMasterKey(passwordKey, effectiveSecret, salt)
		val mac = VaultCrypto.passwordVerificationMac(master)
		val header = VaultHeader(salt, params.memoryKb, params.iterations,
				params.parallelism, wrapped, machineBound, mac, 0L, 0L)
		writeAtomic(headerFile, header.toBytes())
		Arrays.fill(passwordKey, 0)
		Arrays.fill(randomSecret, 0)
		if (!machineBound) Arrays.fill(effectiveSecret, 0)
		masterKey = master
		failedAttempts = 0
		backoffUntil = 0
		saveThrottle()
		touch()
	}

	@Synchronized
	fun unlockVault(password: CharArray): Boolean {
		if (!vaultExists()) return false
		if (System.currentTimeMillis() < backoffUntil) return false
		val start = System.currentTimeMillis()
		val header = VaultHeader.fromBytes(headerFile.readBytes())
		val randomSecret = if (header.machineBound) {
			try {
				MachineSecret.unwrap(header.wrappedSecret)
			} catch (e: Throwable) {
				enforceTimeFloor(start)
				return false
			}
		} else {
			ByteArray(32)
		}
		val params = VaultArgon2.Params(header.argonMemoryKb,
				header.argonIterations, header.argonParallelism)
		val passwordKey = VaultArgon2.deriveKey(password, header.salt, params)
		val candidate = VaultCrypto.deriveMasterKey(passwordKey, randomSecret,
				header.salt)
		Arrays.fill(passwordKey, 0)
		Arrays.fill(randomSecret, 0)
		val mac = VaultCrypto.passwordVerificationMac(candidate)
		val ok = VaultCrypto.constantTimeEquals(mac, header.verificationMac)
		enforceTimeFloor(start)
		return if (ok) {
			masterKey = candidate
			failedAttempts = 0
			backoffUntil = 0
			saveThrottle()
			migrateSecretNames(candidate)
			touch()
			true
		} else {
			Arrays.fill(candidate, 0)
			failedAttempts++
			val shift = (failedAttempts - 1).coerceIn(0, 20)
			backoffUntil = System.currentTimeMillis() +
					minOf(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS shl shift)
			saveThrottle()
			false
		}
	}

	@Synchronized
	fun verifyCurrentPassword(password: CharArray): Boolean {
		requireUnlocked()
		if (!vaultExists()) return false
		val header = VaultHeader.fromBytes(headerFile.readBytes())
		val randomSecret = if (header.machineBound) {
			try {
				MachineSecret.unwrap(header.wrappedSecret)
			} catch (e: Throwable) {
				return false
			}
		} else {
			ByteArray(32)
		}
		val params = VaultArgon2.Params(header.argonMemoryKb,
				header.argonIterations, header.argonParallelism)
		val passwordKey = VaultArgon2.deriveKey(password, header.salt, params)
		val candidate = VaultCrypto.deriveMasterKey(passwordKey, randomSecret,
				header.salt)
		Arrays.fill(passwordKey, 0)
		Arrays.fill(randomSecret, 0)
		val mac = VaultCrypto.passwordVerificationMac(candidate)
		val ok = VaultCrypto.constantTimeEquals(mac, header.verificationMac)
		Arrays.fill(candidate, 0)
		return ok
	}

	@Synchronized
	fun lockVault() {
		masterKey?.let { Arrays.fill(it, 0) }
		masterKey = null
	}

	@Synchronized
	fun addItem(type: VaultItemType, name: String, content: ByteArray): VaultItem {
		val master = requireUnlocked()
		val id = UUID.randomUUID().toString()
		val nameAad = name.toByteArray(Charsets.UTF_8)
		val itemKey = VaultCrypto.generateKey()
		try {
			val encContent = VaultCrypto.encrypt(content, itemKey, nameAad).toBytes()
			val wrappedKey =
					VaultCrypto.encrypt(itemKey, master, EMPTY_AAD).toBytes()
			val now = System.currentTimeMillis()
			val item = VaultItem(id, type, name, now, now,
					content.size.toLong(), wrappedKey)
			val encMeta = VaultCrypto.encrypt(item.serialize(), master,
					id.toByteArray(Charsets.UTF_8)).toBytes()
			val dir = File(itemsDir, id)
			dir.mkdirs()
			writeAtomic(File(dir, HEADER_BIN), encMeta)
			writeAtomic(File(dir, CONTENT_BIN), pad(encContent))
			touch()
			return item
		} finally {
			Arrays.fill(itemKey, 0)
		}
	}

	@Synchronized
	fun updateItem(itemId: String, name: String, content: ByteArray): VaultItem {
		val master = requireUnlocked()
		val dir = File(itemsDir, itemId)
		require(dir.isDirectory) { "no such item" }
		val existing = VaultItem.deserialize(VaultCrypto.decrypt(
				VaultCrypto.EncryptedData.fromBytes(File(dir, HEADER_BIN).readBytes()),
				master, itemId.toByteArray(Charsets.UTF_8)))
		val nameAad = name.toByteArray(Charsets.UTF_8)
		val itemKey = VaultCrypto.generateKey()
		try {
			val encContent = VaultCrypto.encrypt(content, itemKey, nameAad).toBytes()
			val wrappedKey =
					VaultCrypto.encrypt(itemKey, master, EMPTY_AAD).toBytes()
			val now = System.currentTimeMillis()
			val item = VaultItem(itemId, existing.type, name,
					existing.createdTimestamp, now, content.size.toLong(), wrappedKey)
			val encMeta = VaultCrypto.encrypt(item.serialize(), master,
					itemId.toByteArray(Charsets.UTF_8)).toBytes()
			writeAtomic(File(dir, HEADER_BIN), encMeta)
			writeAtomic(File(dir, CONTENT_BIN), pad(encContent))
			touch()
			return item
		} finally {
			Arrays.fill(itemKey, 0)
		}
	}

	@Synchronized
	fun listItems(): List<VaultItem> {
		val master = requireUnlocked()
		val dirs = itemsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
		val out = ArrayList<VaultItem>()
		for (dir in dirs) {
			try {
				val enc = File(dir, HEADER_BIN).readBytes()
				val meta = VaultCrypto.decrypt(
						VaultCrypto.EncryptedData.fromBytes(enc), master,
						dir.name.toByteArray(Charsets.UTF_8))
				out.add(VaultItem.deserialize(meta))
			} catch (e: Exception) {
			}
		}
		touch()
		return out.sortedByDescending { it.modifiedTimestamp }
	}

	@Synchronized
	fun getItemContent(itemId: String): ByteArray {
		val master = requireUnlocked()
		val dir = File(itemsDir, itemId)
		val meta = VaultItem.deserialize(VaultCrypto.decrypt(
				VaultCrypto.EncryptedData.fromBytes(File(dir, HEADER_BIN).readBytes()),
				master, itemId.toByteArray(Charsets.UTF_8)))
		val itemKey = VaultCrypto.decrypt(
				VaultCrypto.EncryptedData.fromBytes(meta.encryptedKey), master,
				EMPTY_AAD)
		try {
			val disk = File(dir, CONTENT_BIN).readBytes()
			val encContent = unpad(disk)
			return VaultCrypto.decrypt(
					VaultCrypto.EncryptedData.fromBytes(encContent), itemKey,
					meta.name.toByteArray(Charsets.UTF_8))
		} finally {
			Arrays.fill(itemKey, 0)
		}
	}

	@Synchronized
	fun deleteItem(itemId: String) {
		requireUnlocked()
		secureDeleteRecursive(File(itemsDir, itemId))
		touch()
	}

	@Synchronized
	fun wipeVault() {
		lockVault()
		secureDeleteRecursive(vaultRoot)
	}

	@Synchronized
	fun changePassword(newPassword: CharArray): Boolean {
		val oldMaster = requireUnlocked()
		discardRekey()
		val header = VaultHeader.fromBytes(headerFile.readBytes())
		val randomSecret = if (header.machineBound) {
			try {
				MachineSecret.unwrap(header.wrappedSecret)
			} catch (e: Throwable) {
				return false
			}
		} else {
			ByteArray(32)
		}
		val newSalt = VaultCrypto.randomBytes(32)
		val params = VaultArgon2.choose()
		val newPasswordKey = VaultArgon2.deriveKey(newPassword, newSalt, params)
		val effectiveSecret = if (header.machineBound) randomSecret
				else ByteArray(32)
		val newMaster = VaultCrypto.deriveMasterKey(newPasswordKey,
				effectiveSecret, newSalt)
		Arrays.fill(newPasswordKey, 0)
		Arrays.fill(randomSecret, 0)
		val newMac = VaultCrypto.passwordVerificationMac(newMaster)
		val newHeader = VaultHeader(newSalt, params.memoryKb, params.iterations,
				params.parallelism, header.wrappedSecret, header.machineBound,
				newMac, 0L, 0L)

		val staged = ArrayList<Pair<File, ByteArray>>()
		val oldSecretFiles = ArrayList<String>()
		try {
			val dirs = itemsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
			for (dir in dirs) {
				val metaFile = File(dir, HEADER_BIN)
				if (!metaFile.isFile) continue
				val item = VaultItem.deserialize(VaultCrypto.decrypt(
						VaultCrypto.EncryptedData.fromBytes(metaFile.readBytes()),
						oldMaster, dir.name.toByteArray(Charsets.UTF_8)))
				val itemKey = VaultCrypto.decrypt(
						VaultCrypto.EncryptedData.fromBytes(item.encryptedKey),
						oldMaster, EMPTY_AAD)
				try {
					val rewrapped = VaultCrypto.encrypt(itemKey, newMaster,
							EMPTY_AAD).toBytes()
					val reItem = VaultItem(item.id, item.type, item.name,
							item.createdTimestamp, item.modifiedTimestamp,
							item.size, rewrapped)
					val encMeta = VaultCrypto.encrypt(reItem.serialize(),
							newMaster, item.id.toByteArray(Charsets.UTF_8)).toBytes()
					staged.add(File(dir, HEADER_BIN + NEW_SUFFIX) to encMeta)
				} finally {
					Arrays.fill(itemKey, 0)
				}
			}
			val secretFiles = secretsDir.listFiles { f -> f.isFile }
					?: emptyArray()
			for (f in secretFiles) {
				if (f.name.endsWith(NEW_SUFFIX)) continue
				val blob = VaultCrypto.decrypt(VaultCrypto.EncryptedData
						.fromBytes(unpad(f.readBytes())), oldMaster, EMPTY_AAD)
				try {
					val name = unpackSecret(blob).first
					val reEnc = VaultCrypto.encrypt(blob, newMaster, EMPTY_AAD)
							.toBytes()
					val newName = secretFileName(name, newMaster)
					staged.add(File(secretsDir, newName + NEW_SUFFIX)
							to pad(reEnc))
					oldSecretFiles.add(f.name)
				} finally {
					Arrays.fill(blob, 0)
				}
			}
		} catch (e: Exception) {
			Arrays.fill(newMaster, 0)
			return false
		}

		for ((target, bytes) in staged) writeAtomic(target, bytes)
		writeAtomic(File(vaultRoot, HEADER_FILE + NEW_SUFFIX), newHeader.toBytes())
		writeAtomic(rekeyMarker,
				oldSecretFiles.joinToString("\n").toByteArray(Charsets.UTF_8))
		commitRekey()

		masterKey?.let { Arrays.fill(it, 0) }
		masterKey = newMaster
		touch()
		return true
	}

	@Synchronized
	fun exportVault(exportPassword: CharArray): ByteArray {
		requireUnlocked()
		val salt = VaultCrypto.randomBytes(32)
		val params = VaultArgon2.choose()
		val exportKey = VaultArgon2.deriveKey(exportPassword, salt, params)
		try {
			val bos = java.io.ByteArrayOutputStream()
			val d = java.io.DataOutputStream(bos)
			d.writeInt(BACKUP_MAGIC)
			d.writeInt(BACKUP_VERSION)
			d.writeInt(salt.size); d.write(salt)
			d.writeInt(params.memoryKb)
			d.writeInt(params.iterations)
			d.writeInt(params.parallelism)

			val items = listItems()
			d.writeInt(items.size)
			for (item in items) {
				val content = getItemContent(item.id)
				val rec = java.io.ByteArrayOutputStream()
				java.io.DataOutputStream(rec).use { r ->
					r.writeInt(item.type.id)
					r.writeUTF(item.name)
					r.writeLong(item.createdTimestamp)
					r.writeLong(item.modifiedTimestamp)
					r.writeInt(content.size); r.write(content)
				}
				val enc = VaultCrypto.encrypt(rec.toByteArray(), exportKey,
						BACKUP_AAD).toBytes()
				d.writeInt(enc.size); d.write(enc)
			}

			val names = listSecretNames("")
			d.writeInt(names.size)
			for (name in names) {
				val data = getSecret(name) ?: continue
				val blob = packSecret(name, data)
				try {
					val enc = VaultCrypto.encrypt(blob, exportKey, BACKUP_AAD)
							.toBytes()
					d.writeInt(enc.size); d.write(enc)
				} finally {
					Arrays.fill(blob, 0); Arrays.fill(data, 0)
				}
			}
			return bos.toByteArray()
		} finally {
			Arrays.fill(exportKey, 0)
		}
	}

	@Synchronized
	fun importVault(exportPassword: CharArray, backup: ByteArray): Int {
		requireUnlocked()
		val d = java.io.DataInputStream(java.io.ByteArrayInputStream(backup))
		if (d.readInt() != BACKUP_MAGIC) return -1
		val version = d.readInt()
		if (version > BACKUP_VERSION) return -1
		val salt = ByteArray(d.readInt().coerceIn(0, 64)); d.readFully(salt)
		val params = VaultArgon2.Params(d.readInt(), d.readInt(), d.readInt())
		val exportKey = VaultArgon2.deriveKey(exportPassword, salt, params)
		var restored = 0
		try {
			val itemCount = d.readInt()
			if (itemCount < 0 || itemCount > MAX_BACKUP_ENTRIES) return -1
			for (i in 0 until itemCount) {
				val enc = ByteArray(d.readInt().coerceIn(0, MAX_BACKUP_BLOB))
				d.readFully(enc)
				val rec = try {
					VaultCrypto.decrypt(VaultCrypto.EncryptedData.fromBytes(enc),
							exportKey, BACKUP_AAD)
				} catch (e: Exception) {
					return -1
				}
				java.io.DataInputStream(java.io.ByteArrayInputStream(rec)).use { r ->
					val type = VaultItemType.fromId(r.readInt())
					val name = r.readUTF()
					r.readLong(); r.readLong()
					val content = ByteArray(r.readInt()); r.readFully(content)
					addItem(type, name, content)
				}
				restored++
			}
			val secretCount = d.readInt()
			if (secretCount < 0 || secretCount > MAX_BACKUP_ENTRIES) return restored
			for (i in 0 until secretCount) {
				val enc = ByteArray(d.readInt().coerceIn(0, MAX_BACKUP_BLOB))
				d.readFully(enc)
				val blob = try {
					VaultCrypto.decrypt(VaultCrypto.EncryptedData.fromBytes(enc),
							exportKey, BACKUP_AAD)
				} catch (e: Exception) {
					continue
				}
				try {
					val (name, data) = unpackSecret(blob)
					putSecret(name, data)
					restored++
				} finally {
					Arrays.fill(blob, 0)
				}
			}
			return restored
		} finally {
			Arrays.fill(exportKey, 0)
		}
	}

	private val rekeyMarker get() = File(vaultRoot, REKEY_MARKER)

	private fun resumeRekeyIfNeeded() {
		try {
			if (!rekeyMarker.exists()) return
			val stagedHeader = File(vaultRoot, HEADER_FILE + NEW_SUFFIX)
			if (stagedHeader.isFile) commitRekey() else discardRekey()
		} catch (e: Throwable) {
		}
	}

	private fun commitRekey() {
		try {
			if (rekeyMarker.isFile) {
				rekeyMarker.readText(Charsets.UTF_8).split("\n")
						.filter { it.isNotBlank() }
						.forEach { File(secretsDir, it).delete() }
			}
		} catch (e: Exception) {
		}
		val dirs = itemsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
		for (dir in dirs) {
			val staged = File(dir, HEADER_BIN + NEW_SUFFIX)
			if (staged.isFile) replaceFile(staged, File(dir, HEADER_BIN))
		}
		val secretFiles = secretsDir.listFiles { f -> f.isFile } ?: emptyArray()
		for (f in secretFiles) {
			if (!f.name.endsWith(NEW_SUFFIX)) continue
			val target = File(f.parentFile,
					f.name.removeSuffix(NEW_SUFFIX))
			replaceFile(f, target)
		}
		val stagedHeader = File(vaultRoot, HEADER_FILE + NEW_SUFFIX)
		if (stagedHeader.isFile) replaceFile(stagedHeader, headerFile)
		rekeyMarker.delete()
	}

	private fun discardRekey() {
		fun purge(dir: File?) {
			dir?.listFiles { f -> f.name.endsWith(NEW_SUFFIX) }
					?.forEach { it.delete() }
		}
		itemsDir.listFiles { f -> f.isDirectory }?.forEach {
			File(it, HEADER_BIN + NEW_SUFFIX).delete()
		}
		purge(secretsDir)
		File(vaultRoot, HEADER_FILE + NEW_SUFFIX).delete()
		rekeyMarker.delete()
	}

	private fun replaceFile(from: File, to: File) {
		if (!from.isFile) return
		if (!from.renameTo(to)) {
			to.delete()
			if (!from.renameTo(to)) {
				from.copyTo(to, overwrite = true)
				from.delete()
			}
		}
	}


	private val secretsDir get() = File(vaultRoot, "secrets")

	@Synchronized
	fun putSecret(name: String, data: ByteArray) {
		val master = requireUnlocked()
		val blob = packSecret(name, data)
		try {
			val enc = VaultCrypto.encrypt(blob, master, EMPTY_AAD).toBytes()
			secretsDir.mkdirs()
			writeAtomic(File(secretsDir, secretFileName(name, master)), pad(enc))
			touch()
		} finally {
			Arrays.fill(blob, 0)
		}
	}

	@Synchronized
	fun getSecret(name: String): ByteArray? {
		val master = requireUnlocked()
		val f = File(secretsDir, secretFileName(name, master))
		if (!f.isFile) return null
		val enc = unpad(f.readBytes())
		val blob = VaultCrypto.decrypt(VaultCrypto.EncryptedData.fromBytes(enc),
				master, EMPTY_AAD)
		try {
			val (storedName, data) = unpackSecret(blob)
			return if (storedName == name) data else null
		} finally {
			Arrays.fill(blob, 0)
		}
	}

	@Synchronized
	fun deleteSecret(name: String) {
		val master = requireUnlocked()
		secureDeleteRecursive(File(secretsDir, secretFileName(name, master)))
		touch()
	}

	@Synchronized
	fun listSecretNames(prefix: String): List<String> {
		val master = requireUnlocked()
		val files = secretsDir.listFiles { f -> f.isFile } ?: return emptyList()
		return files.mapNotNull { f ->
			try {
				val blob = VaultCrypto.decrypt(
						VaultCrypto.EncryptedData.fromBytes(unpad(f.readBytes())),
						master, EMPTY_AAD)
				try {
					unpackSecret(blob).first
				} finally {
					Arrays.fill(blob, 0)
				}
			} catch (e: Exception) {
				null
			}
		}.filter { it.startsWith(prefix) }
	}

	private fun secretFileName(name: String, master: ByteArray): String {
		val fkey = VaultCrypto.hkdfSha256(master, ByteArray(0),
				"vault secret filename", 32)
		try {
			return VaultCrypto.hmacSha256(fkey, name.toByteArray(Charsets.UTF_8))
					.joinToString("") { "%02x".format(it) }
		} finally {
			Arrays.fill(fkey, 0)
		}
	}

	private fun packSecret(name: String, data: ByteArray): ByteArray {
		val nb = name.toByteArray(Charsets.UTF_8)
		val out = ByteArray(4 + nb.size + data.size)
		val bb = ByteBuffer.wrap(out)
		bb.putInt(nb.size); bb.put(nb); bb.put(data)
		return out
	}

	private fun unpackSecret(blob: ByteArray): Pair<String, ByteArray> {
		val bb = ByteBuffer.wrap(blob)
		val nl = bb.int
		require(nl in 0..(blob.size - 4)) { "bad secret name length" }
		val nb = ByteArray(nl); bb.get(nb)
		val data = ByteArray(bb.remaining()); bb.get(data)
		return String(nb, Charsets.UTF_8) to data
	}

	private fun migrateSecretNames(master: ByteArray) {
		val dir = secretsDir
		val files = dir.listFiles { f -> f.isFile } ?: return
		for (f in files) {
			if (isKeyedName(f.name)) continue
			val legacyName = decodeLegacyName(f.name) ?: continue
			try {
				val data = VaultCrypto.decrypt(
						VaultCrypto.EncryptedData.fromBytes(unpad(f.readBytes())),
						master, legacyName.toByteArray(Charsets.UTF_8))
				val blob = packSecret(legacyName, data)
				try {
					val reEnc = VaultCrypto.encrypt(blob, master, EMPTY_AAD).toBytes()
					writeAtomic(File(dir, secretFileName(legacyName, master)),
							pad(reEnc))
					secureDeleteRecursive(f)
				} finally {
					Arrays.fill(blob, 0); Arrays.fill(data, 0)
				}
			} catch (e: Exception) {
			}
		}
	}

	private fun isKeyedName(s: String): Boolean =
			s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

	private fun decodeLegacyName(file: String): String? = try {
		String(Base64.getUrlDecoder().decode(file), Charsets.UTF_8)
	} catch (e: Exception) {
		null
	}

	fun updateActivity() {
		touch()
	}


	private fun requireUnlocked(): ByteArray {
		val m = masterKey
		check(m != null && System.currentTimeMillis() - lastActivity <= AUTO_LOCK_MS) {
			"vault is locked"
		}
		return m
	}

	private fun touch() {
		lastActivity = System.currentTimeMillis()
	}

	private fun enforceTimeFloor(start: Long) {
		val elapsed = System.currentTimeMillis() - start
		if (elapsed < UNLOCK_FLOOR_MS) {
			try { Thread.sleep(UNLOCK_FLOOR_MS - elapsed) } catch (e: Exception) {}
		}
	}

	private fun pad(data: ByteArray): ByteArray {
		val total = 4 + data.size
		var bucket = 4096
		while (bucket < total) bucket = bucket shl 1
		val out = ByteArray(bucket)
		random.nextBytes(out)
		ByteBuffer.wrap(out).putInt(data.size)
		System.arraycopy(data, 0, out, 4, data.size)
		return out
	}

	private fun unpad(disk: ByteArray): ByteArray {
		val len = ByteBuffer.wrap(disk).int
		require(len in 0..(disk.size - 4)) { "bad content length" }
		return disk.copyOfRange(4, 4 + len)
	}

	private fun writeAtomic(target: File, bytes: ByteArray) {
		val tmp = File(target.parentFile, target.name + ".tmp")
		RandomAccessFile(tmp, "rws").use { it.write(bytes) }
		if (!tmp.renameTo(target)) {
			target.delete()
			if (!tmp.renameTo(target)) {
				tmp.copyTo(target, overwrite = true)
				tmp.delete()
			}
		}
	}

	private fun secureDeleteRecursive(file: File) {
		if (!file.exists()) return
		if (file.isDirectory) {
			file.listFiles()?.forEach { secureDeleteRecursive(it) }
			file.delete()
			return
		}
		try {
			val len = file.length()
			if (len > 0) {
				RandomAccessFile(file, "rws").use { raf ->
					val buf = ByteArray(8192)
					var written = 0L
					while (written < len) {
						random.nextBytes(buf)
						val n = minOf(buf.size.toLong(), len - written).toInt()
						raf.write(buf, 0, n)
						written += n
					}
					raf.fd.sync()
				}
			}
		} catch (e: Exception) {
		}
		file.delete()
	}

	private companion object {
		const val HEADER_FILE = "vault.header"
		const val THROTTLE_FILE = "vault.throttle"
		const val REKEY_MARKER = "vault.rekey"
		const val NEW_SUFFIX = ".new"
		const val ITEMS_DIR = "items"
		const val HEADER_BIN = "header.bin"
		const val CONTENT_BIN = "content.bin"
		const val BACKUP_MAGIC = 0x5A424B31
		const val BACKUP_VERSION = 1
		const val MAX_BACKUP_ENTRIES = 100_000
		const val MAX_BACKUP_BLOB = 100 * 1024 * 1024
		val BACKUP_AAD = "zerion-vault-export".toByteArray(Charsets.UTF_8)
		const val AUTO_LOCK_MS = 30L * 60 * 1000
		const val UNLOCK_FLOOR_MS = 1000L
		const val INITIAL_BACKOFF_MS = 1000L
		const val MAX_BACKOFF_MS = 5L * 60 * 1000
		val EMPTY_AAD = ByteArray(0)
	}
}

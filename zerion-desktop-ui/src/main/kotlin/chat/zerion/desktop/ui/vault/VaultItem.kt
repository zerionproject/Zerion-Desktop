package chat.zerion.desktop.ui.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The kinds of thing the vault stores (ids match the Android Vault). */
internal enum class VaultItemType(val id: Int) {
	NOTE(1), IMAGE(2), VIDEO(3), DOCUMENT(4), AUDIO(5), PASSWORD(6);

	companion object {
		fun fromId(id: Int): VaultItemType = entries.firstOrNull { it.id == id }
				?: NOTE
	}
}

/**
 * Encrypted-item metadata. The bulk content lives in a separate per-item file
 * encrypted with [encryptedKey] (itself wrapped by the vault master key); this
 * record only carries the metadata, and is itself stored AES-GCM-encrypted with
 * the item id as AAD.
 */
internal class VaultItem(
		val id: String,
		val type: VaultItemType,
		val name: String,
		val createdTimestamp: Long,
		val modifiedTimestamp: Long,
		val size: Long,
		val encryptedKey: ByteArray,
) {
	fun serialize(): ByteArray {
		val out = ByteArrayOutputStream()
		DataOutputStream(out).use { d ->
			d.writeInt(VERSION)
			d.writeUTF(id)
			d.writeInt(type.id)
			d.writeUTF(name)
			d.writeLong(createdTimestamp)
			d.writeLong(modifiedTimestamp)
			d.writeLong(size)
			d.writeInt(encryptedKey.size)
			d.write(encryptedKey)
		}
		return out.toByteArray()
	}

	companion object {
		private const val VERSION = 1
		private const val MAX_KEY = 1024

		fun deserialize(bytes: ByteArray): VaultItem {
			DataInputStream(ByteArrayInputStream(bytes)).use { d ->
				d.readInt()
				val id = d.readUTF()
				val type = VaultItemType.fromId(d.readInt())
				val name = d.readUTF()
				val created = d.readLong()
				val modified = d.readLong()
				val size = d.readLong()
				val keyLen = d.readInt()
				require(keyLen in 0..MAX_KEY) { "bad key length" }
				val key = ByteArray(keyLen)
				d.readFully(key)
				return VaultItem(id, type, name, created, modified, size, key)
			}
		}
	}
}

/**
 * A password-manager entry. Stored as the content of a PASSWORD-typed item,
 * serialized as JSON with the same field set as the Android Vault
 * ({title, username, password, url, notes}).
 */
data class PasswordEntry(
		val title: String,
		val username: String,
		val password: String,
		val url: String,
		val notes: String,
) {
	fun toJsonBytes(): ByteArray {
		val sb = StringBuilder()
		sb.append('{')
		field(sb, "title", title); sb.append(',')
		field(sb, "username", username); sb.append(',')
		field(sb, "password", password); sb.append(',')
		field(sb, "url", url); sb.append(',')
		field(sb, "notes", notes)
		sb.append('}')
		return sb.toString().toByteArray(Charsets.UTF_8)
	}

	companion object {
		fun fromJsonBytes(bytes: ByteArray): PasswordEntry {
			val s = String(bytes, Charsets.UTF_8)
			return PasswordEntry(
					extract(s, "title"), extract(s, "username"),
					extract(s, "password"), extract(s, "url"),
					extract(s, "notes"))
		}

		private fun field(sb: StringBuilder, key: String, value: String) {
			sb.append('"').append(key).append("\":\"").append(escape(value))
					.append('"')
		}

		private fun escape(s: String): String {
			val sb = StringBuilder(s.length)
			for (c in s) when (c) {
				'\\' -> sb.append("\\\\")
				'"' -> sb.append("\\\"")
				'\n' -> sb.append("\\n")
				'\r' -> sb.append("\\r")
				'\t' -> sb.append("\\t")
				else -> sb.append(c)
			}
			return sb.toString()
		}

		private fun extract(json: String, key: String): String {
			val marker = "\"$key\":\""
			val start = json.indexOf(marker)
			if (start < 0) return ""
			var i = start + marker.length
			val sb = StringBuilder()
			while (i < json.length) {
				val c = json[i]
				if (c == '\\' && i + 1 < json.length) {
					when (json[i + 1]) {
						'\\' -> sb.append('\\')
						'"' -> sb.append('"')
						'n' -> sb.append('\n')
						'r' -> sb.append('\r')
						't' -> sb.append('\t')
						else -> sb.append(json[i + 1])
					}
					i += 2
				} else if (c == '"') {
					break
				} else {
					sb.append(c); i++
				}
			}
			return sb.toString()
		}
	}
}

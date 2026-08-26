package chat.zerion.desktop.ui.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * The vault's header file: KDF salt + parameters, the (DPAPI-wrapped) random
 * secret used for machine-binding, and an HMAC of the master key used to verify
 * the password on unlock without decrypting any item. Mirrors the shape of the
 * Android Vault header.
 */
internal class VaultHeader(
		val salt: ByteArray,
		val argonMemoryKb: Int,
		val argonIterations: Int,
		val argonParallelism: Int,
		val wrappedSecret: ByteArray,
		val machineBound: Boolean,
		val verificationMac: ByteArray,
		val createdTimestamp: Long,
		val modifiedTimestamp: Long,
) {
	fun toBytes(): ByteArray {
		val out = ByteArrayOutputStream()
		DataOutputStream(out).use { d ->
			d.writeInt(MAGIC)
			d.writeInt(VERSION)
			d.writeInt(salt.size); d.write(salt)
			d.writeInt(argonMemoryKb)
			d.writeInt(argonIterations)
			d.writeInt(argonParallelism)
			d.writeInt(wrappedSecret.size); d.write(wrappedSecret)
			d.writeBoolean(machineBound)
			d.writeInt(verificationMac.size); d.write(verificationMac)
			d.writeLong(createdTimestamp)
			d.writeLong(modifiedTimestamp)
		}
		return out.toByteArray()
	}

	companion object {
		private const val MAGIC = 0x5A564C54
		private const val VERSION = 1
		private const val MAX_BLOB = 4096

		fun fromBytes(bytes: ByteArray): VaultHeader {
			DataInputStream(ByteArrayInputStream(bytes)).use { d ->
				require(d.readInt() == MAGIC) { "not a vault header" }
				require(d.readInt() <= VERSION) { "unsupported vault version" }
				val salt = readBlob(d)
				val mem = d.readInt()
				val iters = d.readInt()
				val par = d.readInt()
				val wrapped = readBlob(d)
				val bound = d.readBoolean()
				val mac = readBlob(d)
				val created = d.readLong()
				val modified = d.readLong()
				return VaultHeader(salt, mem, iters, par, wrapped, bound, mac,
						created, modified)
			}
		}

		private fun readBlob(d: DataInputStream): ByteArray {
			val len = d.readInt()
			require(len in 0..MAX_BLOB) { "bad blob length" }
			val b = ByteArray(len)
			d.readFully(b)
			return b
		}
	}
}

package chat.zerion.desktop.db;

import static org.zerionproject.core.util.StringUtils.fromHexString;
import static org.zerionproject.core.util.StringUtils.toHexString;

import org.zerionproject.core.api.crypto.CryptoComponent;
import org.zerionproject.core.api.crypto.DecryptionException;
import org.zerionproject.core.api.crypto.KeyStrengthener;
import org.zerionproject.core.api.crypto.SecretKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Upgrades a legacy password-only database-key envelope to the machine-bound
 * (DPAPI-strengthened) representation on Windows, without ever leaving the
 * account unrecoverable.
 *
 * <p>Sequence: detect legacy envelope, authenticate it with the password,
 * construct the strengthened envelope, write it to a durable staging file,
 * decrypt the staged envelope back and verify it reproduces the exact database
 * key, then commit it with the same crash-safe double-write the account manager
 * uses (db.key + db.key.bak), and finally remove the staging file. A crash at
 * any point leaves either the original legacy envelope or the verified
 * strengthened envelope intact; a stale staging file is discarded on the next
 * attempt.
 */
public final class DbKeyStrengthenMigration {

	public interface EnvelopeCrypto {
		byte[] encryptWithPassword(byte[] plaintext, char[] password,
				KeyStrengthener strengthener);

		byte[] decryptWithPassword(byte[] ciphertext, char[] password,
				KeyStrengthener strengthener) throws DecryptionException;

		boolean isEncryptedWithStrengthenedKey(byte[] ciphertext);
	}

	private DbKeyStrengthenMigration() {
	}

	public static void migrateIfNeeded(File keyDir, CryptoComponent crypto,
			KeyStrengthener strengthener, char[] password) {
		migrateIfNeeded(keyDir, adapt(crypto), strengthener, password);
	}

	public static void migrateIfNeeded(File keyDir, EnvelopeCrypto crypto,
			KeyStrengthener strengthener, char[] password) {
		if (strengthener == null || keyDir == null) return;
		try {
			run(keyDir, crypto, strengthener, password);
		} catch (Throwable t) {
			return;
		}
	}

	private static EnvelopeCrypto adapt(CryptoComponent c) {
		return new EnvelopeCrypto() {
			@Override
			public byte[] encryptWithPassword(byte[] pt, char[] pw,
					KeyStrengthener s) {
				return c.encryptWithPassword(pt, pw, s);
			}

			@Override
			public byte[] decryptWithPassword(byte[] ct, char[] pw,
					KeyStrengthener s) throws DecryptionException {
				return c.decryptWithPassword(ct, pw, s);
			}

			@Override
			public boolean isEncryptedWithStrengthenedKey(byte[] ct) {
				return c.isEncryptedWithStrengthenedKey(ct);
			}
		};
	}

	private static void run(File keyDir, EnvelopeCrypto crypto,
			KeyStrengthener strengthener, char[] password)
			throws IOException {
		File key = new File(keyDir, "db.key");
		File bak = new File(keyDir, "db.key.bak");
		File staging = new File(keyDir, "db.key.upgrade");

		if (staging.exists()) staging.delete();
		if (bak.exists() && !key.exists()) bak.renameTo(key);

		File source = key.exists() ? key : (bak.exists() ? bak : null);
		if (source == null) return;

		byte[] ciphertext;
		try {
			ciphertext = fromHexString(readText(source));
		} catch (Exception e) {
			return;
		}
		if (crypto.isEncryptedWithStrengthenedKey(ciphertext)) return;

		SecretKey dbKey;
		try {
			dbKey = new SecretKey(
					crypto.decryptWithPassword(ciphertext, password, null));
		} catch (DecryptionException e) {
			return;
		}
		try {
			byte[] upgraded = crypto.encryptWithPassword(
					dbKey.getBytes(), password, strengthener);
			String upgradedHex = toHexString(upgraded);

			writeDurably(staging, upgradedHex);

			byte[] verified;
			try {
				verified = crypto.decryptWithPassword(upgraded, password,
						strengthener);
			} catch (DecryptionException e) {
				staging.delete();
				return;
			}
			boolean matches = Arrays.equals(verified, dbKey.getBytes());
			Arrays.fill(verified, (byte) 0);
			if (!matches) {
				staging.delete();
				return;
			}

			commit(key, bak, upgradedHex);
			staging.delete();
		} finally {
			Arrays.fill(dbKey.getBytes(), (byte) 0);
		}
	}

	private static void commit(File key, File bak, String hex)
			throws IOException {
		if (bak.exists() && !key.exists()) {
			bak.renameTo(key);
		}
		writeDurably(bak, hex);
		if (key.exists() && !key.delete()) {
			throw new IOException("could not replace db.key");
		}
		if (!bak.renameTo(key)) {
			throw new IOException("could not commit db.key");
		}
		writeDurably(bak, hex);
	}

	private static String readText(File f) throws IOException {
		return new String(Files.readAllBytes(f.toPath()),
				StandardCharsets.US_ASCII).trim();
	}

	private static void writeDurably(File f, String text) throws IOException {
		try (FileOutputStream out = new FileOutputStream(f)) {
			out.write(text.getBytes(StandardCharsets.US_ASCII));
			out.flush();
			out.getFD().sync();
		}
	}
}

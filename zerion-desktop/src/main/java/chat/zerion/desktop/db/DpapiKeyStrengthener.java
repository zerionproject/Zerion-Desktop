package chat.zerion.desktop.db;

import org.zerionproject.core.api.crypto.KeyStrengthener;
import org.zerionproject.core.api.crypto.SecretKey;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Windows machine/user binding for the messenger database key, mirroring the
 * Vault's DPAPI protection. The strengthener holds a persistent 32-byte secret
 * that is stored only as a DPAPI-wrapped blob (CurrentUser scope) at
 * {@code <keyDir>/db.strengthen}. {@code strengthenKey} returns
 * HMAC-SHA256(secret, kdfKey) so the stored database-key envelope can only be
 * unwrapped on the same Windows user and machine that created it.
 *
 * <p>Fail-closed rules: a corrupt or foreign DPAPI blob throws rather than
 * yielding a wrong key; a missing secret is regenerated only when no
 * machine-bound database-key envelope depends on it yet, so losing the secret
 * never silently reissues one and quietly discards the existing account.
 */
public class DpapiKeyStrengthener implements KeyStrengthener {

	private static final byte PBKDF_FORMAT_ARGON2ID_STRENGTHENED = 3;
	private static final byte PBKDF_FORMAT_SCRYPT_STRENGTHENED = 1;

	private final File keyDir;
	private final Object lock = new Object();
	private byte[] secret;

	public DpapiKeyStrengthener(File keyDir) {
		this.keyDir = keyDir;
	}

	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private File secretFile() {
		return new File(keyDir, "db.strengthen");
	}

	@Override
	public boolean isInitialised() {
		if (!isWindows()) return false;
		synchronized (lock) {
			if (secret != null) return true;
			File f = secretFile();
			if (f.exists()) {
				try {
					byte[] s = Crypt32Util.cryptUnprotectData(readAll(f));
					if (s.length == 32) {
						secret = s;
						return true;
					}
					return false;
				} catch (Throwable t) {
					return false;
				}
			}
			return !strengthenedEnvelopeExists();
		}
	}

	@Override
	public SecretKey strengthenKey(SecretKey k) {
		byte[] s = loadOrCreateSecret();
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(s, "HmacSHA256"));
			return new SecretKey(mac.doFinal(k.getBytes()));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC unavailable", e);
		}
	}

	private byte[] loadOrCreateSecret() {
		synchronized (lock) {
			if (secret != null) return secret;
			File f = secretFile();
			File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
			if (!f.exists() && tmp.exists()) {
				try {
					byte[] s = Crypt32Util.cryptUnprotectData(readAll(tmp));
					if (s.length == 32 && tmp.renameTo(f)) {
						secret = s;
						return secret;
					}
				} catch (Throwable t) {
				}
			}
			if (f.exists()) {
				byte[] s;
				try {
					s = Crypt32Util.cryptUnprotectData(readAll(f));
				} catch (Throwable t) {
					throw new IllegalStateException(
							"database key protection could not be unwrapped", t);
				}
				if (s.length != 32) {
					throw new IllegalStateException(
							"database key protection is malformed");
				}
				secret = s;
				return secret;
			}
			if (strengthenedEnvelopeExists()) {
				throw new IllegalStateException(
						"machine secret is missing but a machine-bound database "
								+ "key exists; refusing to reissue it");
			}
			byte[] s = new byte[32];
			new SecureRandom().nextBytes(s);
			try {
				byte[] wrapped = Crypt32Util.cryptProtectData(s);
				writeDurably(f, wrapped);
			} catch (IOException e) {
				throw new IllegalStateException(
						"could not store database key protection", e);
			}
			secret = s;
			return secret;
		}
	}

	private boolean strengthenedEnvelopeExists() {
		byte first = envelopeFormatByte(new File(keyDir, "db.key"));
		if (first == 0) first = envelopeFormatByte(new File(keyDir, "db.key.bak"));
		return first == PBKDF_FORMAT_ARGON2ID_STRENGTHENED
				|| first == PBKDF_FORMAT_SCRYPT_STRENGTHENED;
	}

	private byte envelopeFormatByte(File f) {
		if (!f.exists()) return 0;
		try {
			String hex = new String(readAll(f), StandardCharsets.US_ASCII).trim();
			if (hex.length() < 2) return 0;
			return (byte) Integer.parseInt(hex.substring(0, 2), 16);
		} catch (Throwable t) {
			return 0;
		}
	}

	private static byte[] readAll(File f) {
		try {
			return Files.readAllBytes(f.toPath());
		} catch (IOException e) {
			throw new IllegalStateException("could not read " + f.getName(), e);
		}
	}

	private static void writeDurably(File f, byte[] bytes) throws IOException {
		File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
		try (FileOutputStream out = new FileOutputStream(tmp)) {
			out.write(bytes);
			out.flush();
			out.getFD().sync();
		}
		if (f.exists() && !f.delete()) {
			throw new IOException("could not replace " + f.getName());
		}
		if (!tmp.renameTo(f)) {
			throw new IOException("could not commit " + f.getName());
		}
	}
}

package chat.zerion.desktop.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.zerionproject.core.util.StringUtils.fromHexString;
import static org.zerionproject.core.util.StringUtils.toHexString;

import chat.zerion.desktop.db.DbKeyStrengthenMigration.EnvelopeCrypto;

import org.zerionproject.core.api.crypto.DecryptionException;
import org.zerionproject.core.api.crypto.DecryptionResult;
import org.zerionproject.core.api.crypto.KeyStrengthener;
import org.zerionproject.core.api.crypto.SecretKey;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

public class DbKeyStrengthenMigrationTest {

	private File keyDir;
	private final EnvelopeCrypto crypto = new FakeCrypto();
	private final char[] pw = "correct horse".toCharArray();
	private final byte[] dbKey = new byte[32];

	@Before
	public void setUp() throws Exception {
		assumeTrue("DPAPI is Windows-only", DpapiKeyStrengthener.isWindows());
		keyDir = Files.createTempDirectory("dbkey-mig").toFile();
		for (int i = 0; i < dbKey.length; i++) dbKey[i] = (byte) (i * 7 + 1);
	}

	private KeyStrengthener str() {
		return new DpapiKeyStrengthener(keyDir);
	}

	private void writeKey(File f, byte[] cipher) throws Exception {
		Files.write(f.toPath(), toHexString(cipher).getBytes(StandardCharsets.US_ASCII));
	}

	private byte[] readCipher(File f) throws Exception {
		return fromHexString(new String(Files.readAllBytes(f.toPath()),
				StandardCharsets.US_ASCII).trim());
	}

	private void assertOpensStrengthened() throws Exception {
		File key = new File(keyDir, "db.key");
		assertTrue("db.key must exist", key.exists());
		byte[] cipher = readCipher(key);
		assertTrue("envelope must be strengthened",
				crypto.isEncryptedWithStrengthenedKey(cipher));
		byte[] back = crypto.decryptWithPassword(cipher, pw, str());
		assertArrayEquals("must decrypt to the exact db key", dbKey, back);
	}

	@Test
	public void migratesLegacyEnvelopeToStrengthened() throws Exception {
		writeKey(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		assertOpensStrengthened();
		assertFalse(new File(keyDir, "db.key.upgrade").exists());
	}

	@Test
	public void wrongPasswordLeavesLegacyEnvelopeUntouched() throws Exception {
		byte[] legacy = crypto.encryptWithPassword(dbKey, pw, null);
		writeKey(new File(keyDir, "db.key"), legacy);
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto,
				str(), "wrong".toCharArray());
		byte[] after = readCipher(new File(keyDir, "db.key"));
		assertFalse("must not migrate under a wrong password",
				crypto.isEncryptedWithStrengthenedKey(after));
		assertArrayEquals(legacy, after);
		assertFalse(new File(keyDir, "db.strengthen").exists());
	}

	@Test
	public void migrationIsIdempotent() throws Exception {
		writeKey(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		byte[] first = readCipher(new File(keyDir, "db.key"));
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		byte[] second = readCipher(new File(keyDir, "db.key"));
		assertArrayEquals("second run must not re-wrap", first, second);
		assertOpensStrengthened();
	}

	@Test
	public void staleStagingFileIsDiscardedThenMigrationCompletes()
			throws Exception {
		writeKey(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));
		Files.write(new File(keyDir, "db.key.upgrade").toPath(),
				"garbage".getBytes());
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		assertOpensStrengthened();
		assertFalse(new File(keyDir, "db.key.upgrade").exists());
	}

	@Test
	public void crashWithBackupNewAndKeyLegacyRecovers() throws Exception {
		str().strengthenKey(new SecretKey(dbKey));
		byte[] legacy = crypto.encryptWithPassword(dbKey, pw, null);
		byte[] strong = crypto.encryptWithPassword(dbKey, pw, str());
		writeKey(new File(keyDir, "db.key"), legacy);
		writeKey(new File(keyDir, "db.key.bak"), strong);
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		assertOpensStrengthened();
	}

	@Test
	public void crashWithKeyAbsentAndBackupStrengthenedRecovers()
			throws Exception {
		str().strengthenKey(new SecretKey(dbKey));
		byte[] strong = crypto.encryptWithPassword(dbKey, pw, str());
		writeKey(new File(keyDir, "db.key.bak"), strong);
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		assertOpensStrengthened();
	}

	@Test
	public void crashAfterCommitLeavesStrengthenedKeyReadable() throws Exception {
		str().strengthenKey(new SecretKey(dbKey));
		byte[] strong = crypto.encryptWithPassword(dbKey, pw, str());
		writeKey(new File(keyDir, "db.key"), strong);
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		assertOpensStrengthened();
	}

	@Test
	public void migratedEnvelopeFailsClosedWithoutTheMachineSecret()
			throws Exception {
		writeKey(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto, str(), pw);
		assertOpensStrengthened();

		new File(keyDir, "db.strengthen").delete();
		byte[] cipher = readCipher(new File(keyDir, "db.key"));
		try {
			crypto.decryptWithPassword(cipher, pw, str());
			fail("expected fail-closed once the machine secret is gone");
		} catch (DecryptionException e) {
			assertEquals(DecryptionResult.KEY_STRENGTHENER_ERROR,
					e.getDecryptionResult());
		}
	}

	private static final class FakeCrypto implements EnvelopeCrypto {
		private static byte[] sha(byte[]... parts) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				for (byte[] p : parts) md.update(p);
				return md.digest();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public byte[] encryptWithPassword(byte[] pt, char[] password,
				KeyStrengthener s) {
			byte[] pwh = sha(new String(password).getBytes(StandardCharsets.UTF_8));
			byte[] tag;
			byte fmt;
			if (s != null) {
				byte[] sk = s.strengthenKey(new SecretKey(
						Arrays.copyOf(pwh, 32))).getBytes();
				tag = Arrays.copyOf(sha(sk), 16);
				fmt = 3;
			} else {
				tag = new byte[16];
				fmt = 2;
			}
			byte[] out = new byte[1 + 16 + 16 + pt.length];
			out[0] = fmt;
			System.arraycopy(Arrays.copyOf(pwh, 16), 0, out, 1, 16);
			System.arraycopy(tag, 0, out, 17, 16);
			System.arraycopy(pt, 0, out, 33, pt.length);
			return out;
		}

		@Override
		public byte[] decryptWithPassword(byte[] ct, char[] password,
				KeyStrengthener s) throws DecryptionException {
			if (ct.length < 33) {
				throw new DecryptionException(DecryptionResult.INVALID_CIPHERTEXT);
			}
			byte[] pwh = sha(new String(password).getBytes(StandardCharsets.UTF_8));
			byte[] gotPw = Arrays.copyOfRange(ct, 1, 17);
			if (!Arrays.equals(gotPw, Arrays.copyOf(pwh, 16))) {
				throw new DecryptionException(DecryptionResult.INVALID_PASSWORD);
			}
			if (ct[0] == 3) {
				if (s == null || !s.isInitialised()) {
					throw new DecryptionException(
							DecryptionResult.KEY_STRENGTHENER_ERROR);
				}
				byte[] sk = s.strengthenKey(new SecretKey(
						Arrays.copyOf(pwh, 32))).getBytes();
				byte[] expected = Arrays.copyOf(sha(sk), 16);
				byte[] gotTag = Arrays.copyOfRange(ct, 17, 33);
				if (!Arrays.equals(expected, gotTag)) {
					throw new DecryptionException(DecryptionResult.INVALID_PASSWORD);
				}
			}
			return Arrays.copyOfRange(ct, 33, ct.length);
		}

		@Override
		public boolean isEncryptedWithStrengthenedKey(byte[] ct) {
			return ct.length > 0 && ct[0] == 3;
		}
	}
}

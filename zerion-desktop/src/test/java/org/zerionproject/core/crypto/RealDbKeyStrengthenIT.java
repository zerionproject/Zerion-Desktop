package org.zerionproject.core.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.zerionproject.core.util.StringUtils.fromHexString;
import static org.zerionproject.core.util.StringUtils.toHexString;

import chat.zerion.desktop.db.DbKeyStrengthenMigration;
import chat.zerion.desktop.db.DpapiKeyStrengthener;

import org.zerionproject.core.api.crypto.CryptoComponent;
import org.zerionproject.core.api.crypto.DecryptionException;
import org.zerionproject.core.jvm.JvmSecureRandomProvider;
import org.zerionproject.core.system.SystemClock;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class RealDbKeyStrengthenIT {

	private CryptoComponent crypto;
	private File keyDir;
	private final char[] pw = "a real password".toCharArray();
	private byte[] dbKey;

	@Before
	public void setUp() throws Exception {
		assumeTrue("DPAPI is Windows-only", DpapiKeyStrengthener.isWindows());
		crypto = new CryptoComponentImpl(new JvmSecureRandomProvider(),
				new ScryptKdf(new SystemClock()),
				new Argon2idKdf(new SystemClock()));
		keyDir = Files.createTempDirectory("real-dbkey").toFile();
		dbKey = crypto.generateSecretKey().getBytes();
	}

	private void write(File f, byte[] cipher) throws Exception {
		Files.write(f.toPath(),
				toHexString(cipher).getBytes(StandardCharsets.US_ASCII));
	}

	private byte[] read(File f) throws Exception {
		return fromHexString(new String(Files.readAllBytes(f.toPath()),
				StandardCharsets.US_ASCII).trim());
	}

	@Test
	public void realArgon2PlusDpapiMigrationRoundTrips() throws Exception {
		write(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));

		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto,
				new DpapiKeyStrengthener(keyDir), pw);

		byte[] cipher = read(new File(keyDir, "db.key"));
		assertTrue("must be machine-bound after migration",
				crypto.isEncryptedWithStrengthenedKey(cipher));
		byte[] back = crypto.decryptWithPassword(cipher, pw,
				new DpapiKeyStrengthener(keyDir));
		assertArrayEquals(dbKey, back);
	}

	@Test
	public void portabilityIsDeniedWithoutTheMachineSecret() throws Exception {
		write(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto,
				new DpapiKeyStrengthener(keyDir), pw);

		new File(keyDir, "db.strengthen").delete();
		byte[] cipher = read(new File(keyDir, "db.key"));
		try {
			crypto.decryptWithPassword(cipher, pw,
					new DpapiKeyStrengthener(keyDir));
			fail("password alone must not decrypt a machine-bound envelope");
		} catch (DecryptionException expected) {
		}
	}

	@Test
	public void wrongPasswordStillFailsAfterMigration() throws Exception {
		write(new File(keyDir, "db.key"),
				crypto.encryptWithPassword(dbKey, pw, null));
		DbKeyStrengthenMigration.migrateIfNeeded(keyDir, crypto,
				new DpapiKeyStrengthener(keyDir), pw);
		byte[] cipher = read(new File(keyDir, "db.key"));
		try {
			crypto.decryptWithPassword(cipher, "nope".toCharArray(),
					new DpapiKeyStrengthener(keyDir));
			fail("wrong password must fail");
		} catch (DecryptionException expected) {
		}
		assertFalse(false);
	}
}

package chat.zerion.desktop.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import org.zerionproject.core.api.crypto.SecretKey;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;

public class DpapiKeyStrengthenerTest {

	private File keyDir;

	@Before
	public void setUp() throws Exception {
		assumeTrue("DPAPI is Windows-only", DpapiKeyStrengthener.isWindows());
		keyDir = Files.createTempDirectory("dpapi-str").toFile();
	}

	private SecretKey key(byte fill) {
		byte[] b = new byte[32];
		java.util.Arrays.fill(b, fill);
		return new SecretKey(b);
	}

	@Test
	public void deterministicAcrossInstancesSharingTheSecret() {
		DpapiKeyStrengthener a = new DpapiKeyStrengthener(keyDir);
		byte[] out1 = a.strengthenKey(key((byte) 7)).getBytes();
		assertTrue(new File(keyDir, "db.strengthen").exists());

		DpapiKeyStrengthener b = new DpapiKeyStrengthener(keyDir);
		byte[] out2 = b.strengthenKey(key((byte) 7)).getBytes();
		assertArrayEquals(out1, out2);
	}

	@Test
	public void differentSecretYieldsDifferentOutput() throws Exception {
		byte[] withSecretA =
				new DpapiKeyStrengthener(keyDir).strengthenKey(key((byte) 1))
						.getBytes();
		new File(keyDir, "db.strengthen").delete();
		byte[] withSecretB =
				new DpapiKeyStrengthener(keyDir).strengthenKey(key((byte) 1))
						.getBytes();
		boolean equal = java.util.Arrays.equals(withSecretA, withSecretB);
		assertFalse("a fresh secret must change the output", equal);
	}

	@Test
	public void corruptDpapiBlobFailsClosed() throws Exception {
		File secret = new File(keyDir, "db.strengthen");
		byte[] garbage = new byte[64];
		new SecureRandom().nextBytes(garbage);
		Files.write(secret.toPath(), garbage);
		try {
			new DpapiKeyStrengthener(keyDir).strengthenKey(key((byte) 2));
			fail("expected fail-closed on corrupt DPAPI blob");
		} catch (IllegalStateException expected) {
		}
	}

	@Test
	public void missingSecretIsNeverReissuedWhenAStrengthenedEnvelopeExists()
			throws Exception {
		File dbKey = new File(keyDir, "db.key");
		Files.write(dbKey.toPath(), "03deadbeef".getBytes());
		File secret = new File(keyDir, "db.strengthen");
		assertFalse(secret.exists());
		try {
			new DpapiKeyStrengthener(keyDir).strengthenKey(key((byte) 3));
			fail("expected refusal to reissue the machine secret");
		} catch (IllegalStateException expected) {
		}
		assertFalse("must not silently create a new secret", secret.exists());
	}

	@Test
	public void recoversTheSecretFromAnUnrenamedTempToAvoidLockout()
			throws Exception {
		DpapiKeyStrengthener a = new DpapiKeyStrengthener(keyDir);
		byte[] out = a.strengthenKey(key((byte) 9)).getBytes();
		File secret = new File(keyDir, "db.strengthen");
		File tmp = new File(keyDir, "db.strengthen.tmp");
		assertTrue(secret.renameTo(tmp));
		Files.write(new File(keyDir, "db.key").toPath(), "03aa".getBytes());
		assertFalse(secret.exists());

		DpapiKeyStrengthener b = new DpapiKeyStrengthener(keyDir);
		byte[] recovered = b.strengthenKey(key((byte) 9)).getBytes();
		assertArrayEquals("must recover the same secret from the temp", out,
				recovered);
		assertTrue("temp must be promoted to the final name", secret.exists());
	}

	@Test
	public void isInitialisedReflectsSecretAvailability() throws Exception {
		DpapiKeyStrengthener s = new DpapiKeyStrengthener(keyDir);
		assertTrue("generatable when no strengthened envelope exists",
				s.isInitialised());
		s.strengthenKey(key((byte) 5));
		assertTrue(new DpapiKeyStrengthener(keyDir).isInitialised());

		File dir2 = Files.createTempDirectory("dpapi-str2").toFile();
		Files.write(new File(dir2, "db.key").toPath(), "03aa".getBytes());
		assertFalse("strengthened envelope + no secret must not be initialised",
				new DpapiKeyStrengthener(dir2).isInitialised());
	}
}

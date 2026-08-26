package chat.zerion.desktop.i2p;

import net.i2p.router.Router;

import org.zerionproject.transport.i2p.I2pRouter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

/**
 * Boots the embedded net.i2p router on the desktop JVM. Ported from the Android
 * BundledI2pRouter: same hidden-mode, NTCP2-only, reseed-over-Tor config, but
 * with a plain-file data directory and reseed certificates copied from the
 * classpath instead of Android assets. By default I2P reseeds through the Tor
 * SOCKS proxy and fails closed (no clearnet reseed) unless the user opts in.
 */
public class DesktopI2pRouter implements I2pRouter {

	private static final long TOR_WAIT_MS = 90_000;
	private static final long TOR_POLL_MS = 3_000;

	private static final String PINNED_RESEED_URLS =
			"https://reseed.stormycloud.org/,"
			+ "https://reseed.diva.exchange/,"
			+ "https://i2p.novg.net/,"
			+ "https://www2.mk16.de/,"
			+ "https://spiral.likogan.dev/,"
			+ "https://reseed.sahil.world/,"
			+ "https://i2p.diyarciftci.xyz/,"
			+ "https://i2pseed.creativecowpat.net:8443/";

	private final File baseDir;
	private final int torSocksPort;
	private final BooleanSupplier directReseedAllowed;
	private final BooleanSupplier torActive;
	private final Object lock = new Object();

	@Nullable
	private Router router;

	public DesktopI2pRouter(File baseDir, int torSocksPort,
			BooleanSupplier directReseedAllowed, BooleanSupplier torActive) {
		this.baseDir = baseDir;
		this.torSocksPort = torSocksPort;
		this.directReseedAllowed = directReseedAllowed;
		this.torActive = torActive;
	}

	@Override
	public void start() throws IOException {
		boolean useDirect = shouldReseedDirect();
		synchronized (lock) {
			if (router != null) return;
			if (!baseDir.exists() && !baseDir.mkdirs()) {
				throw new IOException("Could not create I2P directory");
			}
			extractCertificates();
			writeLoggerConfig();
			System.setProperty("i2p.dir.base", baseDir.getAbsolutePath());
			System.setProperty("i2p.dir.config", baseDir.getAbsolutePath());
			net.i2p.router.I2pGlobalContextReset.reset();
			Router r = new Router(routerProperties(useDirect));
			r.setKillVMOnEnd(false);
			r.runRouter();
			router = r;
		}
	}

	@Override
	public void stop() {
		synchronized (lock) {
			Router r = router;
			if (r != null) {
				r.shutdown(Router.EXIT_HARD);
			}
			router = null;
		}
	}

	private void writeLoggerConfig() {
		File cfg = new File(baseDir, "logger.config");
		try (OutputStream out = new FileOutputStream(cfg)) {
			out.write(("logger.defaultLevel=OFF\n"
					+ "logger.logRotationLimit=0\n"
					+ "logger.dropDuplicates=true\n")
					.getBytes(StandardCharsets.US_ASCII));
		} catch (IOException e) {
		}
	}

	private Properties routerProperties(boolean useDirect) {
		Properties p = new Properties();
		p.setProperty("i2cp.disableInterface", "true");
		p.setProperty("router.maxParticipatingTunnels", "0");
		p.setProperty("router.floodfillParticipant", "false");
		p.setProperty("i2p.hiddenMode", "true");
		p.setProperty("i2np.udp.enable", "false");
		p.setProperty("i2np.ntcp2.enable", "true");
		p.setProperty("i2np.inboundKBytesPerSecond", "128");
		p.setProperty("i2np.outboundKBytesPerSecond", "64");
		p.setProperty("i2np.upnp.enable", "false");
		p.setProperty("router.enableUPnP", "false");
		if (useDirect) {
			applyDirectReseed(p);
		} else {
			applyReseedOverTor(p);
		}
		return p;
	}

	private boolean shouldReseedDirect() {
		if (!directReseedAllowed.getAsBoolean()) return false;
		return !awaitTorActive();
	}

	private boolean awaitTorActive() {
		long deadline = System.currentTimeMillis() + TOR_WAIT_MS;
		while (System.currentTimeMillis() < deadline) {
			if (torActive.getAsBoolean()) return true;
			try {
				Thread.sleep(TOR_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return torActive.getAsBoolean();
			}
		}
		return torActive.getAsBoolean();
	}

	private void applyDirectReseed(Properties p) {
		p.setProperty("router.reseedSSLRequired", "true");
		p.setProperty("i2p.reseedURL", PINNED_RESEED_URLS);
	}

	private void applyReseedOverTor(Properties p) {
		if (torSocksPort <= 0) {
			throw new IllegalStateException(
					"I2P reseed requires the Tor SOCKS proxy; refusing to reseed "
							+ "over clearnet.");
		}
		p.setProperty("router.reseedSSLProxyEnable", "true");
		p.setProperty("router.reseedSSLProxyType", "SOCKS5");
		p.setProperty("router.reseedSSLProxyHost", "127.0.0.1");
		p.setProperty("router.reseedSSLProxyPort", String.valueOf(torSocksPort));
		p.setProperty("router.reseedSSLRequired", "true");
	}

	private void extractCertificates() throws IOException {
		copyCertGroup("reseed");
		copyCertGroup("ssl");
	}

	private void copyCertGroup(String group) throws IOException {
		File dir = new File(new File(baseDir, "certificates"), group);
		dir.mkdirs();
		for (String name : listResources("/i2p/certificates/" + group
				+ ".list")) {
			File target = new File(dir, name);
			if (target.exists()) continue;
			try (InputStream in = getClass().getResourceAsStream(
					"/i2p/certificates/" + group + "/" + name)) {
				if (in == null) continue;
				try (OutputStream out = new FileOutputStream(target)) {
					byte[] buf = new byte[8192];
					int read;
					while ((read = in.read(buf)) != -1) {
						out.write(buf, 0, read);
					}
				}
			}
		}
	}

	private List<String> listResources(String indexPath) throws IOException {
		List<String> names = new ArrayList<>();
		InputStream in = getClass().getResourceAsStream(indexPath);
		if (in == null) return names;
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) names.add(trimmed);
			}
		}
		return names;
	}
}

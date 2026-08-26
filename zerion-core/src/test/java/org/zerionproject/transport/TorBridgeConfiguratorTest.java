package org.zerionproject.transport;

import org.briarproject.onionwrapper.CircumventionProvider;
import org.briarproject.onionwrapper.LocationUtils;
import org.briarproject.onionwrapper.TorWrapper;
import org.zerionproject.core.api.db.DbException;
import org.zerionproject.core.api.db.Transaction;
import org.zerionproject.core.api.event.Event;
import org.zerionproject.core.api.event.EventBus;
import org.zerionproject.core.api.event.EventListener;
import org.zerionproject.core.api.settings.Settings;
import org.zerionproject.core.api.settings.SettingsManager;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.zerionproject.core.api.plugin.TorConstants.PREF_TOR_CUSTOM_BRIDGES;
import static org.zerionproject.core.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.zerionproject.core.api.plugin.TorConstants.PREF_TOR_NETWORK_AUTOMATIC;
import static org.zerionproject.core.api.plugin.TorConstants.PREF_TOR_NETWORK_WITHOUT_BRIDGES;
import static org.zerionproject.core.api.plugin.TorConstants.PREF_TOR_NETWORK_WITH_BRIDGES;

public class TorBridgeConfiguratorTest {

	// Direct mode (the default posture): bridges are disabled and Tor is used
	// directly. apply() succeeds so the network is enabled without bridges.
	@Test
	public void directModeDisablesBridges() {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_WITHOUT_BRIDGES);
		StubTor tor = new StubTor();
		TorBridgeConfigurator c = configurator(s, tor,
				stubCircumvention(false, new ArrayList<>()));
		assertTrue(c.apply());
		assertTrue(tor.disableBridgesCalled);
		assertNull(tor.enabledBridges);
	}

	// Explicit bridge mode with user-supplied lines: each line becomes a Tor
	// bridge line and is applied.
	@Test
	public void customBridgesAreApplied() {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_WITH_BRIDGES);
		s.put(PREF_TOR_CUSTOM_BRIDGES,
				"obfs4 1.2.3.4:443 CERT iat-mode=0\n" +
				"Bridge vanilla 5.6.7.8:9001\n" +
				"   \n");
		StubTor tor = new StubTor();
		TorBridgeConfigurator c = configurator(s, tor,
				stubCircumvention(false, new ArrayList<>()));
		assertTrue(c.apply());
		assertEquals(Arrays.asList(
				"Bridge obfs4 1.2.3.4:443 CERT iat-mode=0",
				"Bridge vanilla 5.6.7.8:9001"), tor.enabledBridges);
	}

	// Bridge mode with no usable bridges fails; the caller then keeps the Tor
	// network disabled rather than falling back to a direct or clearnet path.
	@Test
	public void emptyBridgesFailClosed() {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_WITH_BRIDGES);
		StubTor tor = new StubTor();
		TorBridgeConfigurator c = configurator(s, tor,
				stubCircumvention(false, new ArrayList<>()));
		assertFalse(c.apply());
		assertNull(tor.enabledBridges);
	}

	// If Tor rejects the bridges, apply() fails closed.
	@Test
	public void enableBridgesFailureFailsClosed() {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_WITH_BRIDGES);
		s.put(PREF_TOR_CUSTOM_BRIDGES, "obfs4 1.2.3.4:443 CERT");
		StubTor tor = new StubTor();
		tor.throwOnEnableBridges = true;
		TorBridgeConfigurator c = configurator(s, tor,
				stubCircumvention(false, new ArrayList<>()));
		assertFalse(c.apply());
	}

	// Automatic mode in an uncensored location uses Tor directly.
	@Test
	public void automaticUncensoredIsDirect() {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_AUTOMATIC);
		StubTor tor = new StubTor();
		TorBridgeConfigurator c = configurator(s, tor,
				stubCircumvention(false, new ArrayList<>()));
		assertTrue(c.apply());
		assertTrue(tor.disableBridgesCalled);
	}

	// Automatic mode in a censored location uses the built-in bridges.
	@Test
	public void automaticCensoredUsesBuiltInBridges() {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_AUTOMATIC);
		StubTor tor = new StubTor();
		List<String> builtIn = Arrays.asList("snowflake 192.0.2.3:1");
		TorBridgeConfigurator c = configurator(s, tor,
				stubCircumvention(true, builtIn));
		assertTrue(c.apply());
		assertEquals(builtIn, tor.enabledBridges);
	}

	@Test
	public void parseCustomBridgesNormalisesAndSkipsBlanks() {
		List<String> out = TorBridgeConfigurator.parseCustomBridges(
				"\n bridge obfs4 A \n vanilla B \n\n");
		assertEquals(Arrays.asList("bridge obfs4 A", "Bridge vanilla B"), out);
	}

	// ---- stubs ----

	private TorBridgeConfigurator configurator(Settings settings, TorWrapper tor,
			CircumventionProvider cp) {
		return new TorBridgeConfigurator(new StubSettingsManager(settings), cp,
				() -> "US", tor, new StubEventBus(), Runnable::run);
	}

	private CircumventionProvider stubCircumvention(boolean useBridges,
			List<String> builtIn) {
		return new CircumventionProvider() {
			public boolean shouldUseBridges(String countryCode) {
				return useBridges;
			}

			public List<BridgeType> getSuitableBridgeTypes(String countryCode) {
				return builtIn.isEmpty() ? new ArrayList<>()
						: Arrays.asList(BridgeType.SNOWFLAKE);
			}

			public List<String> getBridges(BridgeType type, String countryCode) {
				return builtIn;
			}
		};
	}

	private static class StubSettingsManager implements SettingsManager {
		private final Settings settings;

		StubSettingsManager(Settings settings) {
			this.settings = settings;
		}

		public Settings getSettings(String namespace) {
			return settings;
		}

		public Settings getSettings(Transaction txn, String namespace) {
			return settings;
		}

		public void mergeSettings(Settings s, String namespace) {
		}

		public void mergeSettings(Transaction txn, Settings s, String namespace) {
		}
	}

	private static class StubEventBus implements EventBus {
		public void addListener(EventListener l) {
		}

		public void removeListener(EventListener l) {
		}

		public void broadcast(Event e) {
		}
	}

	private static class StubTor implements TorWrapper {
		@Nullable List<String> enabledBridges = null;
		boolean disableBridgesCalled = false;
		boolean throwOnEnableBridges = false;

		public void start() {
		}

		public void stop() {
		}

		public void setObserver(@Nullable Observer observer) {
		}

		public TorState getTorState() {
			return TorState.STOPPED;
		}

		public boolean isTorRunning() {
			return false;
		}

		@Nullable
		public HiddenServiceProperties publishHiddenService(int localPort,
				int remotePort, @Nullable String privateKey) {
			return null;
		}

		public void removeHiddenService(String onion) {
		}

		public void enableNetwork(boolean enable) {
		}

		public void enableBridges(List<String> bridges) throws IOException {
			if (throwOnEnableBridges) throw new IOException("rejected");
			enabledBridges = new ArrayList<>(bridges);
		}

		public void disableBridges() {
			disableBridgesCalled = true;
		}

		public void enableConnectionPadding(boolean enable) {
		}

		public void enableIpv6(boolean ipv6Only) {
		}

		public File getLyrebirdExecutableFile() {
			return new File(".");
		}
	}
}

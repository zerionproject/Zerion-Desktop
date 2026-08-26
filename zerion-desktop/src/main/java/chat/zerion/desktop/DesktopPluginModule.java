package chat.zerion.desktop;

import org.zerionproject.core.api.FeatureFlags;
import org.zerionproject.core.api.plugin.PluginConfig;
import org.zerionproject.core.api.plugin.TransportId;
import org.zerionproject.core.api.plugin.duplex.DuplexPluginFactory;
import org.zerionproject.core.api.plugin.simplex.SimplexPluginFactory;
import org.zerionproject.transport.ZtpDuplexPluginFactory;
import org.zerionproject.transport.i2p.I2pDuplexPluginFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

/**
 * Desktop {@link PluginConfig}: registers Zerion's Tor duplex plugin
 * ({@link ZtpDuplexPluginFactory}) and, when I2P is enabled, the I2P duplex
 * plugin ({@link I2pDuplexPluginFactory}). No BLE mesh on desktop. The I2P
 * plugin stays dormant until the user turns it on (per-plugin enable setting).
 */
@Module
class DesktopPluginModule {

	@Provides
	@Singleton
	PluginConfig providePluginConfig(ZtpDuplexPluginFactory ztp,
			I2pDuplexPluginFactory i2p, FeatureFlags featureFlags) {
		Collection<DuplexPluginFactory> duplex =
				featureFlags.shouldEnableI2p()
						? Arrays.asList(ztp, i2p) : singletonList(ztp);
		return new PluginConfig() {
			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return duplex;
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return emptyList();
			}

			@Override
			public boolean shouldPoll() {
				return true;
			}

			@Override
			public Map<TransportId, List<TransportId>>
					getTransportPreferences() {
				return emptyMap();
			}
		};
	}
}

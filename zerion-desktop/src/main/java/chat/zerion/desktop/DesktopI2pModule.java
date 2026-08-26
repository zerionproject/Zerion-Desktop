package chat.zerion.desktop;

import chat.zerion.desktop.i2p.DesktopI2pStack;

import org.zerionproject.core.api.plugin.PluginManager;
import org.zerionproject.core.api.plugin.TorSocksPort;
import org.zerionproject.core.api.settings.SettingsManager;
import org.zerionproject.transport.i2p.I2pStack;

import java.io.File;

import javax.inject.Provider;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Desktop I2P bindings: provides the {@link I2pStack} backed by the embedded
 * net.i2p router. The I2P plugin only starts if it is enabled (feature flag +
 * the per-plugin enable setting), so this binding is inert until then.
 */
@Module
class DesktopI2pModule {

	private final File dataDir;

	DesktopI2pModule(File dataDir) {
		this.dataDir = dataDir;
	}

	@Provides
	@Singleton
	I2pStack provideI2pStack(@TorSocksPort int torSocksPort,
			Provider<PluginManager> pluginManagerProvider,
			SettingsManager settingsManager) {
		return new DesktopI2pStack(new File(dataDir, "i2p"), torSocksPort,
				pluginManagerProvider, settingsManager);
	}
}

package chat.zerion.desktop.i2p;

import org.zerionproject.core.api.db.DbException;
import org.zerionproject.core.api.plugin.I2pConstants;
import org.zerionproject.core.api.plugin.Plugin;
import org.zerionproject.core.api.plugin.PluginManager;
import org.zerionproject.core.api.plugin.TorConstants;
import org.zerionproject.core.api.settings.SettingsManager;
import org.zerionproject.transport.ZtpConnectionHandler;
import org.zerionproject.transport.i2p.I2pOverlayTransport;
import org.zerionproject.transport.i2p.I2pStack;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import javax.inject.Provider;

/**
 * Desktop {@link I2pStack}: builds the embedded-router transport ({@link
 * DesktopI2pRouter} + {@link I2pStreamingTransport}). Reseed goes over Tor and
 * fails closed unless the user has opted into direct reseed.
 */
public class DesktopI2pStack implements I2pStack {

	private final File i2pDir;
	private final int torSocksPort;
	private final Provider<PluginManager> pluginManagerProvider;
	private final SettingsManager settingsManager;

	public DesktopI2pStack(File i2pDir, int torSocksPort,
			Provider<PluginManager> pluginManagerProvider,
			SettingsManager settingsManager) {
		this.i2pDir = i2pDir;
		this.torSocksPort = torSocksPort;
		this.pluginManagerProvider = pluginManagerProvider;
		this.settingsManager = settingsManager;
	}

	@Override
	public I2pOverlayTransport createTransport(Executor ioExecutor,
			ZtpConnectionHandler handler) {
		BooleanSupplier directReseedAllowed = () -> {
			try {
				return settingsManager.getSettings(I2pConstants.ID.getString())
						.getBoolean(I2pConstants.PREF_I2P_DIRECT_RESEED,
								I2pConstants.DEFAULT_PREF_I2P_DIRECT_RESEED);
			} catch (DbException e) {
				return false;
			}
		};
		BooleanSupplier torActive = () -> {
			Plugin p = pluginManagerProvider.get().getPlugin(TorConstants.ID);
			return p != null && p.getState() == Plugin.State.ACTIVE;
		};
		DesktopI2pRouter router = new DesktopI2pRouter(i2pDir, torSocksPort,
				directReseedAllowed, torActive);
		return new I2pStreamingTransport(router, ioExecutor, handler);
	}
}

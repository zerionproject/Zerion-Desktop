package chat.zerion.desktop.tor;

import org.briarproject.onionwrapper.JavaLocationUtilsFactory;
import org.briarproject.onionwrapper.LocationUtils;
import org.briarproject.onionwrapper.MacTorWrapper;
import org.briarproject.onionwrapper.TorWrapper;
import org.briarproject.onionwrapper.UnixTorWrapper;
import org.briarproject.onionwrapper.WindowsTorWrapper;
import org.zerionproject.core.api.event.EventExecutor;
import org.zerionproject.core.api.lifecycle.IoExecutor;
import org.zerionproject.core.api.plugin.FastConnectSocketFactory;
import org.zerionproject.core.api.plugin.TorControlPort;
import org.zerionproject.core.api.plugin.TorDirectory;
import org.zerionproject.core.api.plugin.TorSocksPort;
import org.zerionproject.transport.TorBridgeConfigurator;
import org.zerionproject.transport.ZtpConnectionHandler;
import org.zerionproject.transport.ZtpTorTransport;

import java.io.File;
import java.util.concurrent.Executor;

import javax.inject.Singleton;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

/**
 * Desktop Tor bindings, the twin of {@code ZerionTorWrapperModule} +
 * {@code AndroidSystemModule}'s LocationUtils. Uses onionwrapper-java's per-OS
 * {@link TorWrapper} implementations, which load the Tor executable from the
 * per-OS {@code org.briarproject:tor-<os>} resource jar on the classpath. See
 * docs/DESKTOP_PORT.md.
 */
@Module
public class DesktopTorModule {

	private final File torDirectory;

	public DesktopTorModule(File dataDir) {
		this.torDirectory = new File(dataDir, "tor");
	}

	@Provides
	@Singleton
	@TorDirectory
	File provideTorDirectory() {
		torDirectory.mkdirs();
		return torDirectory;
	}

	@Provides
	@TorSocksPort
	int provideTorSocksPort() {
		return 59050;
	}

	@Provides
	@TorControlPort
	int provideTorControlPort() {
		return 59051;
	}

	@Provides
	@Singleton
	LocationUtils provideLocationUtils() {
		return JavaLocationUtilsFactory.createJavaLocationUtils();
	}

	@Provides
	@Singleton
	TorWrapper provideTorWrapper(@IoExecutor Executor ioExecutor,
			@EventExecutor Executor eventExecutor,
			@TorDirectory File torDir, @TorSocksPort int socksPort,
			@TorControlPort int controlPort) {
		String os = System.getProperty("os.name", "").toLowerCase();
		String arch = architecture();
		if (os.contains("win")) {
			return new WindowsTorWrapper(ioExecutor, eventExecutor, arch,
					torDir, socksPort, controlPort);
		} else if (os.contains("mac") || os.contains("darwin")) {
			return new MacTorWrapper(ioExecutor, eventExecutor, arch, torDir,
					socksPort, controlPort);
		} else {
			return new UnixTorWrapper(ioExecutor, eventExecutor, arch, torDir,
					socksPort, controlPort);
		}
	}

	@Provides
	@Singleton
	ZtpTorTransport provideTorTransport(TorWrapper tor,
			SocketFactory torSocketFactory,
			@FastConnectSocketFactory SocketFactory fastSocketFactory,
			@IoExecutor Executor ioExecutor, ZtpConnectionHandler handler,
			TorBridgeConfigurator bridgeConfigurator) {
		return new ZtpTorTransport(tor, torSocketFactory, fastSocketFactory,
				ioExecutor, handler, bridgeConfigurator);
	}

	private static String architecture() {
		String a = System.getProperty("os.arch", "").toLowerCase();
		if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
		if (a.contains("amd64") || a.contains("x86_64")) return "x86_64";
		if (a.contains("86")) return "x86";
		return a;
	}
}

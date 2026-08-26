package org.zerionproject.core.jvm;

import org.zerionproject.core.battery.DefaultBatteryManagerModule;
import org.zerionproject.core.event.DefaultEventExecutorModule;
import org.zerionproject.core.system.DefaultTaskSchedulerModule;
import org.zerionproject.core.system.DefaultThreadFactoryModule;
import org.zerionproject.core.system.DefaultWakefulIoExecutorModule;
import org.zerionproject.core.api.network.NetworkManager;
import org.zerionproject.core.api.system.ResourceProvider;
import org.zerionproject.core.api.system.SecureRandomProvider;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * JVM/desktop platform bindings, the twin of {@code BrambleAndroidModule}.
 * Reuses zerion-core's JVM-safe {@code Default*Module}s (which Android
 * overrides) and adds the platform services that had Android-only providers.
 *
 * <p>Phase 0 omits Tor: {@code CircumventionModule}, {@code DnsModule},
 * {@code SocksModule} and a {@code LocationUtils} binding come in Phase 1 with
 * {@code onionwrapper-java}.
 */
@Module(includes = {
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		DefaultTaskSchedulerModule.class,
		DefaultThreadFactoryModule.class,
		DefaultWakefulIoExecutorModule.class
})
public class BrambleJavaModule {

	@Provides
	@Singleton
	SecureRandomProvider provideSecureRandomProvider(
			JvmSecureRandomProvider provider) {
		return provider;
	}

	@Provides
	@Singleton
	ResourceProvider provideResourceProvider(JvmResourceProvider provider) {
		return provider;
	}

	@Provides
	@Singleton
	NetworkManager provideNetworkManager(JvmNetworkManager networkManager) {
		return networkManager;
	}
}

package chat.zerion.desktop;

import org.zerionproject.core.api.FeatureFlags;

import dagger.Module;
import dagger.Provides;

/**
 * Desktop app-layer bindings. Provides {@link FeatureFlags}; the desktop build
 * enables image attachments, profile pictures and disappearing messages, keeps
 * private groups out of core (Briar parity), and leaves I2P off (the desktop
 * transport wiring is Tor-only for now).
 */
@Module
class DesktopAppModule {

	@Provides
	FeatureFlags provideFeatureFlags() {
		return new FeatureFlags() {
			@Override
			public boolean shouldEnableImageAttachments() {
				return true;
			}

			@Override
			public boolean shouldEnableProfilePictures() {
				return true;
			}

			@Override
			public boolean shouldEnableDisappearingMessages() {
				return true;
			}

			@Override
			public boolean shouldEnablePrivateGroupsInCore() {
				return false;
			}

			@Override
			public boolean shouldEnableI2p() {
				return true;
			}
		};
	}
}

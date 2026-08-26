package chat.zerion.desktop;

import chat.zerion.desktop.tor.DesktopTorModule;

import org.zerionproject.app.BriarCoreEagerSingletons;
import org.zerionproject.app.BriarCoreModule;
import org.zerionproject.app.api.conversation.ConversationManager;
import org.zerionproject.app.api.identity.AuthorManager;
import org.zerionproject.app.api.messaging.MessagingManager;
import org.zerionproject.app.api.messaging.PrivateMessageFactory;
import org.zerionproject.core.BrambleCoreEagerSingletons;
import org.zerionproject.core.BrambleCoreModule;
import org.zerionproject.core.account.AccountModule;
import org.zerionproject.core.api.account.AccountManager;
import org.zerionproject.core.api.connection.ConnectionRegistry;
import org.zerionproject.core.api.contact.ContactManager;
import org.zerionproject.core.api.db.DatabaseComponent;
import org.zerionproject.core.api.event.EventBus;
import org.zerionproject.core.api.identity.IdentityManager;
import org.zerionproject.core.api.lifecycle.LifecycleManager;
import org.zerionproject.core.api.plugin.PluginManager;
import org.zerionproject.core.io.DnsModule;
import org.zerionproject.core.jvm.BrambleJavaModule;
import org.zerionproject.core.plugin.tor.CircumventionModule;
import org.zerionproject.core.socks.SocksModule;
import org.zerionproject.core.system.ClockModule;
import org.zerionproject.transport.ZerionTransportModule;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Desktop object graph: the JVM engine (bramble-core) + the app layer
 * ({@link BriarCoreModule}: contacts, conversations, messaging, identity) + the
 * JVM platform bindings with an encrypted HyperSqlDatabase and the Tor
 * transport. Shared by the headless {@link Main} and the Compose UI; boot it via
 * {@link DesktopBoot}.
 */
@Singleton
@Component(modules = {
		BrambleCoreModule.class,
		BriarCoreModule.class,
		BrambleJavaModule.class,
		ClockModule.class,
		AccountModule.class,
		org.zerionproject.core.db.DatabaseModule.class,
		DesktopDatabaseModule.class,
		DesktopAppModule.class,
		DesktopTorModule.class,
		DesktopI2pModule.class,
		DesktopPluginModule.class,
		ZerionTransportModule.class,
		CircumventionModule.class,
		DnsModule.class,
		SocksModule.class
})
public interface ZerionDesktopComponent
		extends BrambleCoreEagerSingletons, BriarCoreEagerSingletons {

	AccountManager accountManager();

	org.zerionproject.core.api.crypto.CryptoComponent cryptoComponent();

	org.zerionproject.core.api.db.DatabaseConfig databaseConfig();

	LifecycleManager lifecycleManager();

	PluginManager pluginManager();

	ContactManager contactManager();

	ConnectionRegistry connectionRegistry();

	IdentityManager identityManager();

	EventBus eventBus();

	DatabaseComponent db();

	org.zerionproject.core.api.settings.SettingsManager settingsManager();

	ConversationManager conversationManager();

	MessagingManager messagingManager();

	PrivateMessageFactory privateMessageFactory();

	org.zerionproject.app.api.attachment.AttachmentReader attachmentReader();

	org.zerionproject.app.api.autodelete.AutoDeleteManager autoDeleteManager();

	org.zerionproject.app.api.grouptr.GroupTrManager groupTrManager();

	org.zerionproject.app.api.avatar.AvatarManager avatarManager();

	org.zerionproject.app.api.channel.ChannelManager channelManager();

	AuthorManager authorManager();

	org.zerionproject.app.api.messaging.VoiceSignalFactory voiceSignalFactory();

	org.zerionproject.app.conversation.voice.VoiceCallCrypto voiceCallCrypto();

	org.zerionproject.app.conversation.voice.VoiceCallConnectionManager
			voiceCallConnectionManager();

	@org.zerionproject.core.api.plugin.TorSocksPort
	int torSocksPort();

	void inject(ZerionTransportModule.EagerSingletons init);
}

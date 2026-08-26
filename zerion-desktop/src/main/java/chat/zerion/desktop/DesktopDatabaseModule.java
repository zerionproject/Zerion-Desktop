package chat.zerion.desktop;

import org.zerionproject.core.api.crypto.KeyStrengthener;
import org.zerionproject.core.api.db.DatabaseConfig;
import org.zerionproject.core.jvm.DesktopDatabaseConfig;

import chat.zerion.desktop.db.DpapiKeyStrengthener;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/** Provides the desktop {@link DatabaseConfig} rooted at a chosen data dir. */
@Module
class DesktopDatabaseModule {

	private final File dataDir;

	DesktopDatabaseModule(File dataDir) {
		this.dataDir = dataDir;
	}

	@Provides
	@Singleton
	DatabaseConfig provideDatabaseConfig() {
		File dbDir = new File(dataDir, "db");
		File keyDir = new File(dataDir, "key");
		KeyStrengthener strengthener = DpapiKeyStrengthener.isWindows()
				? new DpapiKeyStrengthener(keyDir) : null;
		return new DesktopDatabaseConfig(dbDir, keyDir, strengthener);
	}
}

package org.zerionproject.core.jvm;

import org.zerionproject.core.api.crypto.KeyStrengthener;
import org.zerionproject.core.api.db.DatabaseConfig;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;

import javax.annotation.Nullable;

/**
 * Desktop {@link DatabaseConfig}: points the AES-encrypted HyperSqlDatabase at a
 * per-OS data directory. Where a platform key strengthener is supplied (DPAPI on
 * Windows) the database key envelope is additionally machine/user bound
 * (PBKDF_FORMAT_ARGON2ID_STRENGTHENED); otherwise it stays wrapped with the
 * password-derived key only (PBKDF_FORMAT_ARGON2ID).
 */
@NotNullByDefault
public class DesktopDatabaseConfig implements DatabaseConfig {

	private final File databaseDirectory;
	private final File databaseKeyDirectory;
	@Nullable
	private final KeyStrengthener keyStrengthener;

	public DesktopDatabaseConfig(File databaseDirectory,
			File databaseKeyDirectory) {
		this(databaseDirectory, databaseKeyDirectory, null);
	}

	public DesktopDatabaseConfig(File databaseDirectory,
			File databaseKeyDirectory,
			@Nullable KeyStrengthener keyStrengthener) {
		this.databaseDirectory = databaseDirectory;
		this.databaseKeyDirectory = databaseKeyDirectory;
		this.keyStrengthener = keyStrengthener;
	}

	@Override
	public File getDatabaseDirectory() {
		return databaseDirectory;
	}

	@Override
	public File getDatabaseKeyDirectory() {
		return databaseKeyDirectory;
	}

	@Nullable
	@Override
	public KeyStrengthener getKeyStrengthener() {
		return keyStrengthener;
	}
}

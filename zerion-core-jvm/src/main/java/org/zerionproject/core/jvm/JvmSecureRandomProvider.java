package org.zerionproject.core.jvm;

import org.zerionproject.core.api.system.SecureRandomProvider;
import org.briarproject.nullsafety.NotNullByDefault;

import java.security.Provider;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Desktop {@link SecureRandomProvider}: returns {@code null}, i.e. use the
 * platform default {@link java.security.SecureRandom} seeding (the OS CSPRNG on
 * Windows/Linux/macOS). No Android LinuxSecureRandom shim is needed on the JVM.
 */
@NotNullByDefault
public class JvmSecureRandomProvider implements SecureRandomProvider {

	@Inject
	public JvmSecureRandomProvider() {
	}

	@Nullable
	@Override
	public Provider getProvider() {
		return null;
	}
}

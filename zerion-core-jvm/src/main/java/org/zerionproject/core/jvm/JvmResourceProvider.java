package org.zerionproject.core.jvm;

import org.zerionproject.core.api.system.ResourceProvider;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.InputStream;

import javax.inject.Inject;

/**
 * Desktop {@link ResourceProvider}: loads bundled resources (e.g. Tor geoip
 * data in Phase 1) from the application classpath.
 */
@NotNullByDefault
public class JvmResourceProvider implements ResourceProvider {

	@Inject
	public JvmResourceProvider() {
	}

	@Override
	public InputStream getResourceInputStream(String name, String extension) {
		String path = name + extension;
		InputStream in = getClass().getClassLoader().getResourceAsStream(path);
		if (in == null) {
			throw new IllegalArgumentException("Resource not found: " + path);
		}
		return in;
	}
}

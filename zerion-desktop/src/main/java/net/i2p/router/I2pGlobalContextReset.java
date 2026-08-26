package net.i2p.router;

/**
 * Reaches the package-private {@code RouterContext.killGlobalContext()} so the
 * embedded router can be (re)started cleanly in one JVM. Pure Java; lives in the
 * {@code net.i2p.router} package only for the access it needs.
 */
public final class I2pGlobalContextReset {

	private I2pGlobalContextReset() {
	}

	public static void reset() {
		RouterContext.killGlobalContext();
	}
}

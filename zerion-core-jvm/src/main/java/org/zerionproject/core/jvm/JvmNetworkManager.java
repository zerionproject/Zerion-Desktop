package org.zerionproject.core.jvm;

import org.zerionproject.core.api.network.NetworkManager;
import org.zerionproject.core.api.network.NetworkStatus;
import org.briarproject.nullsafety.NotNullByDefault;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import javax.inject.Inject;

/**
 * Desktop {@link NetworkManager}: reports connectivity by scanning network
 * interfaces for an up, non-loopback interface with an assigned address, and
 * flags whether any global IPv6 address exists. Tor is the only consumer of the
 * wifi/ipv6 hints; desktop never uses Wi-Fi Direct, so wifi is always false.
 */
@NotNullByDefault
public class JvmNetworkManager implements NetworkManager {

	@Inject
	public JvmNetworkManager() {
	}

	@Override
	public NetworkStatus getNetworkStatus() {
		boolean connected = false;
		boolean ipv6 = false;
		boolean ipv4 = false;
		try {
			Enumeration<NetworkInterface> ifaces =
					NetworkInterface.getNetworkInterfaces();
			while (ifaces != null && ifaces.hasMoreElements()) {
				NetworkInterface iface = ifaces.nextElement();
				if (!iface.isUp() || iface.isLoopback()) continue;
				Enumeration<InetAddress> addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					if (addr.isLoopbackAddress()
							|| addr.isLinkLocalAddress()) {
						continue;
					}
					connected = true;
					if (addr.getAddress().length == 4) {
						ipv4 = true;
					} else {
						ipv6 = true;
					}
				}
			}
		} catch (SocketException e) {
			// If interface enumeration fails, assume we are online rather than
			// wedging the transports; a failed dial will correct it.
			connected = true;
		}
		boolean ipv6Only = connected && ipv6 && !ipv4;
		return new NetworkStatus(connected, false, ipv6Only);
	}
}

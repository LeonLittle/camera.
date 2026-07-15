package com.leonlittle.t10smonitor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

final class NetworkUtils {
    private NetworkUtils() { }

    static String findWifiIpv4Address() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!"wlan0".equals(network.getName()) || !network.isUp()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // The activity remains usable while Wi-Fi is reconnecting.
        }
        return null;
    }
}

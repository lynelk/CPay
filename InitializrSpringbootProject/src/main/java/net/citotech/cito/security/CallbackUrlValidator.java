package net.citotech.cito.security;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates callback URLs to prevent Server-Side Request Forgery (SSRF) attacks.
 */
public class CallbackUrlValidator {

    private static final List<String> PRIVATE_IP_PREFIXES = Arrays.asList(
        "10.", "192.168.", "127.", "0.",
        "169.254.", // link-local
        "::1",      // IPv6 loopback
        "fc", "fd"  // IPv6 private
    );

    private static final List<String> PRIVATE_HOSTNAMES = Arrays.asList(
        "localhost", "127.0.0.1", "::1"
    );

    /**
     * Validates that a callback URL is safe to use.
     * @param callbackUrl the URL to validate
     * @return null if valid, or an error message if invalid
     */
    public static String validate(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            return "callback_url is required";
        }

        URL url;
        try {
            url = new URL(callbackUrl.trim());
        } catch (MalformedURLException e) {
            return "callback_url is not a valid URL";
        }

        if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol())) {
            return "callback_url must use http or https protocol";
        }

        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            return "callback_url must have a valid host";
        }

        // Block private hostnames directly
        if (PRIVATE_HOSTNAMES.contains(host.toLowerCase())) {
            return "callback_url must not point to a private or loopback address";
        }

        // Resolve host and check the resolved IP
        try {
            InetAddress address = InetAddress.getByName(host);
            String resolvedIp = address.getHostAddress();

            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                return "callback_url must not resolve to a private or loopback address";
            }

            for (String prefix : PRIVATE_IP_PREFIXES) {
                if (resolvedIp.startsWith(prefix)) {
                    return "callback_url must not point to a private address range";
                }
            }
        } catch (UnknownHostException e) {
            Logger.getLogger(CallbackUrlValidator.class.getName())
                .log(Level.WARNING, "Could not resolve callback_url host: " + host, e);
            return "callback_url host could not be resolved. Please use a publicly resolvable hostname.";
        }

        return null; // valid
    }
}

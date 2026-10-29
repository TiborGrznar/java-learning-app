package com.tgrznar.javalearningapp.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Decides which address a request counts against. By default it is the address of the TCP
 * connection. X-Forwarded-For is believed only when the connection comes from a configured proxy,
 * and even then only the entries added by trusted proxies are skipped: the rightmost entry that is
 * not a trusted proxy is the client. Entries further left are written by the client and can be forged.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_LENGTH = 64;

    private final Set<String> trustedProxies;

    @Autowired
    public ClientIpResolver(RateLimitProperties properties) {
        this(properties.trustedProxies());
    }

    ClientIpResolver(Collection<String> trustedProxies) {
        this.trustedProxies = Set.copyOf(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || !trustedProxies.contains(remote)) {
            return cap(remote == null ? "unknown" : remote);
        }
        String header = String.join(",", Collections.list(request.getHeaders(FORWARDED_FOR)));
        String[] parts = header.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (!candidate.isEmpty() && !trustedProxies.contains(candidate)) {
                return cap(candidate);
            }
        }
        return cap(remote);
    }

    /** The key comes from the network, so its length is bounded. */
    private static String cap(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
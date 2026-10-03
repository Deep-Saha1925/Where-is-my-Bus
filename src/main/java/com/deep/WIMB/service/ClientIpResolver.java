package com.deep.WIMB.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Works out the real client IP for rate limiting.
 *
 * Behind a host's reverse proxy (Render, Cloudflare...) request.getRemoteAddr() is the
 * PROXY's address, so every driver would share one rate-limit bucket. The real client is in
 * the X-Forwarded-For header -- but anyone can send that header themselves, so it is only
 * trusted when the connection really comes from an internal proxy (loopback / private
 * network address). A client connecting straight from the internet can never use it to fake
 * its IP.
 *
 * X-Forwarded-For looks like "client, proxy1, proxy2". Each trusted proxy appends the
 * address it received the request from, so the real client is "trusted-proxy-hops" entries
 * from the RIGHT (anything further left was supplied by the client and is ignored).
 * Default 1 (one proxy in front, e.g. Render). If a CDN sits in front of that too, set
 * TRUSTED_PROXY_HOPS=2.
 */
@Component
public class ClientIpResolver {

    @Value("${wimb.security.trusted-proxy-hops:1}")
    private int trustedHops;

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedHops <= 0 || !isInternalAddress(remote)) {
            return remote;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }

        String[] parts = forwarded.split(",");
        int index = parts.length - trustedHops;
        if (index < 0) {
            return remote; // fewer entries than expected proxies: don't guess
        }

        String candidate = parts[index].trim();
        return looksLikeIp(candidate) ? candidate : remote;
    }

    private static boolean looksLikeIp(String s) {
        if (s.isEmpty() || s.length() > 45) return false;
        for (char c : s.toCharArray()) {
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':';
            if (!ok) return false;
        }
        return true;
    }

    /** True for loopback, 10.x / 172.16-31.x / 192.168.x, link-local and IPv6 unique-local (fc00::/7). */
    private static boolean isInternalAddress(String ip) {
        if (ip == null || !looksLikeIp(ip)) return false;
        try {
            InetAddress addr = InetAddress.getByName(ip); // a literal IP: no DNS lookup happens
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
                return true;
            }
            byte[] bytes = addr.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (Exception e) {
            return false;
        }
    }
}
package com.example.liveChat.infra.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ClientIpResolver {
    private static final String CLOUDFLARE_CONNECTING_IP = "CF-Connecting-IP";

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${livechat.security.trusted-proxies:}") String trustedProxyCidrs) {
        this.trustedProxies = parseTrustedProxies(trustedProxyCidrs);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = parseIpLiteral(request.getRemoteAddr())
                .map(InetAddress::getHostAddress)
                .orElseThrow(() -> new IllegalArgumentException("request remoteAddr must be a valid IPv4 or IPv6 address"));

        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        return parseIpLiteral(request.getHeader(CLOUDFLARE_CONNECTING_IP))
                .map(InetAddress::getHostAddress)
                .orElse(remoteAddress);
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private static List<IpAddressMatcher> parseTrustedProxies(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            return List.of();
        }

        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String cidr : configuredCidrs.split(",")) {
            String candidate = cidr.trim();
            if (!candidate.isEmpty()) {
                matchers.add(new IpAddressMatcher(candidate));
            }
        }
        return List.copyOf(matchers);
    }

    private static Optional<InetAddress> parseIpLiteral(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        if (candidate.indexOf(':') >= 0) {
            if (!candidate.matches("[0-9A-Fa-f:.]+")) {
                return Optional.empty();
            }
            return parseWithInetAddress(candidate);
        }

        String[] octets = candidate.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }

        byte[] address = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || !octet.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            try {
                int parsed = Integer.parseInt(octet);
                if (parsed > 255) {
                    return Optional.empty();
                }
                address[index] = (byte) parsed;
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        try {
            return Optional.of(InetAddress.getByAddress(address));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static Optional<InetAddress> parseWithInetAddress(String candidate) {
        try {
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }
}

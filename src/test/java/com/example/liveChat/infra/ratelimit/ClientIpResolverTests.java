package com.example.liveChat.infra.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTests {
    @Test
    void ignoresCloudflareHeaderWhenNoProxyIsTrustedByDefault() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = request("198.51.100.10", "203.0.113.20");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void acceptsCloudflareHeaderOnlyFromAConfiguredProxyCidr() {
        ClientIpResolver resolver = new ClientIpResolver("198.51.100.0/24");
        MockHttpServletRequest request = request("198.51.100.10", "203.0.113.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.20");
    }

    @Test
    void ignoresSpoofedHeaderFromAnUntrustedRemoteAddress() {
        ClientIpResolver resolver = new ClientIpResolver("192.0.2.0/24");
        MockHttpServletRequest request = request("198.51.100.10", "203.0.113.20");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void ignoresInvalidOrForwardedListsInCloudflareHeader() {
        ClientIpResolver resolver = new ClientIpResolver("198.51.100.0/24");
        MockHttpServletRequest request = request("198.51.100.10", "203.0.113.20, 192.0.2.1");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void validatesAndResolvesIpv6Addresses() throws Exception {
        ClientIpResolver resolver = new ClientIpResolver("2001:db8:1::/48");
        MockHttpServletRequest request = request("2001:db8:1::10", "2001:db8:2::20");

        assertThat(resolver.resolve(request))
                .isEqualTo(InetAddress.getByName("2001:db8:2::20").getHostAddress());
    }

    @Test
    void rejectsAnInvalidServletRemoteAddress() {
        ClientIpResolver resolver = new ClientIpResolver("0.0.0.0/0");
        MockHttpServletRequest request = request("proxy.internal", "203.0.113.20");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remoteAddr");
    }

    private static MockHttpServletRequest request(String remoteAddress, String cloudflareAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("CF-Connecting-IP", cloudflareAddress);
        return request;
    }
}

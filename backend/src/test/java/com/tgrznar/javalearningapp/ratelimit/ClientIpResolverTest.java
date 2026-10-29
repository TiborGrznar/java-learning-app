package com.tgrznar.javalearningapp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remote, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        for (String value : forwardedFor) {
            request.addHeader("X-Forwarded-For", value);
        }
        return request;
    }

    @Test
    void withoutTrustedProxies_theConnectionAddressIsUsedAndTheHeaderIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());

        assertThat(resolver.resolve(request("203.0.113.7", "1.2.3.4"))).isEqualTo("203.0.113.7");
    }

    @Test
    void trustedProxy_usesTheAddressItAppended() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5"));

        assertThat(resolver.resolve(request("10.0.0.5", "198.51.100.9"))).isEqualTo("198.51.100.9");
    }

    @Test
    void forgedLeftEntries_areIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5"));

        assertThat(resolver.resolve(request("10.0.0.5", "1.1.1.1, 198.51.100.9"))).isEqualTo("198.51.100.9");
    }

    @Test
    void chainOfTrustedProxies_isSkipped() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5", "10.0.0.6"));

        assertThat(resolver.resolve(request("10.0.0.5", "198.51.100.9, 10.0.0.6"))).isEqualTo("198.51.100.9");
    }

    @Test
    void trustedProxyWithoutHeaderOrWithOnlyTrustedEntries_fallsBackToTheConnectionAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5", "10.0.0.6"));

        assertThat(resolver.resolve(request("10.0.0.5"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "10.0.0.6"))).isEqualTo("10.0.0.5");
    }

    @Test
    void untrustedConnectionIsNeverBelievedEvenWithAHeaderFromATrustedLookingAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5"));

        assertThat(resolver.resolve(request("203.0.113.7", "10.0.0.5"))).isEqualTo("203.0.113.7");
    }

    @Test
    void longValues_areCut() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.5"));

        assertThat(resolver.resolve(request("10.0.0.5", "x".repeat(500)))).hasSize(64);
    }
}
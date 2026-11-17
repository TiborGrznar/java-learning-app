package com.tgrznar.javalearningapp.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.tgrznar.javalearningapp.ratelimit.BodySizeLimitFilter;
import com.tgrznar.javalearningapp.ratelimit.RateLimitFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiCorsFilterTest {

    private static final String ORIGIN = "https://frontend.test";

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        return request;
    }

    private static int orderOf(Class<?> type) {
        Order order = AnnotationUtils.findAnnotation(type, Order.class);
        assertThat(order).as("@Order of " + type.getSimpleName()).isNotNull();
        return order.value();
    }

    @Test
    void withoutOrigins_addsNothingAndPassesTheRequestOn() throws Exception {
        ApiCorsFilter filter = new ApiCorsFilter(new CorsProperties(List.of(), null));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/users/me"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void allowedOrigin_getsTheHeadersAndTheRequestGoesOn() throws Exception {
        ApiCorsFilter filter = new ApiCorsFilter(new CorsProperties(List.of(ORIGIN), null));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/modules"), response, chain);

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)).isEqualTo("Retry-After");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void pathsOutsideTheApi_areLeftAlone() throws Exception {
        ApiCorsFilter filter = new ApiCorsFilter(new CorsProperties(List.of(ORIGIN), null));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/swagger-ui/index.html"), response, chain);

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void runsBeforeTheBodySizeAndTheRateLimitFilters() {
        // Their 413 and 429 answers are written before Spring Security, so they carry CORS headers
        // only when this filter is first. Moving it breaks the frontend's handling of RATE_LIMITED.
        assertThat(ApiCorsFilter.ORDER).isLessThan(orderOf(BodySizeLimitFilter.class));
        assertThat(ApiCorsFilter.ORDER).isLessThan(orderOf(RateLimitFilter.class));
        assertThat(orderOf(ApiCorsFilter.class)).isEqualTo(ApiCorsFilter.ORDER);
    }
}

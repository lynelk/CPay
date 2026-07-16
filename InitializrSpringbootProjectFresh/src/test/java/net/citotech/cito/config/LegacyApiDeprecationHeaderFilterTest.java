package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class LegacyApiDeprecationHeaderFilterTest {

    @Test
    void legacyApiResponsesIncludeDeprecationHeaders() throws ServletException, IOException {
        LegacyApiDeprecationHeaderFilter filter = new LegacyApiDeprecationHeaderFilter();
        ReflectionTestUtils.setField(filter, "sunsetDate", "2027-02-01");
        ReflectionTestUtils.setField(filter, "migrationDocsUrl", "/docs/api-versioning-deprecation.md");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/doPayOut");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo("2027-02-01");
        assertThat(response.getHeaders("Link"))
            .anySatisfy(link -> assertThat(link).contains("rel=\"deprecation\""))
            .anySatisfy(link -> assertThat(link).contains("rel=\"successor-version\""));
    }

    @Test
    void nonLegacyRoutesAreLeftUntouched() throws ServletException, IOException {
        LegacyApiDeprecationHeaderFilter filter = new LegacyApiDeprecationHeaderFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Deprecation")).isNull();
    }
}

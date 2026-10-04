package com.paytm.seatreservation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void testFilterGeneratesRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertNotNull(MDC.get("request_id"));
            assertEquals(MDC.get("request_id"), response.getHeader("X-Request-ID"));
        };

        filter.doFilter(request, response, chain);

        assertNotNull(response.getHeader("X-Request-ID"));
        assertNull(MDC.get("request_id")); // Should be cleared after chain
    }

    @Test
    void testFilterUsesExistingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "test-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertEquals("test-id-123", MDC.get("request_id"));
        };

        filter.doFilter(request, response, chain);

        assertEquals("test-id-123", response.getHeader("X-Request-ID"));
    }
}

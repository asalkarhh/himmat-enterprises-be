package com.asalkartechworks.himmat.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that establishes the request correlation ID early in the filter chain,
 * propagates it to MDC and ensures it is included in response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingId = request.getHeader(CorrelationIdHolder.HEADER_NAME);
        CorrelationIdHolder.set(incomingId);
        try {
            response.setHeader(CorrelationIdHolder.HEADER_NAME, CorrelationIdHolder.get());
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdHolder.clear();
        }
    }
}

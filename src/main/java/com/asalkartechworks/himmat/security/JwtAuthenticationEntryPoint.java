package com.asalkartechworks.himmat.security;

import com.asalkartechworks.himmat.common.dto.ErrorDetails;
import com.asalkartechworks.himmat.common.dto.ErrorResponse;
import com.asalkartechworks.himmat.common.filter.CorrelationIdHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized access attempt to {}: {}", request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdHolder.HEADER_NAME, CorrelationIdHolder.get());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code("UNAUTHENTICATED")
                        .message("Authentication required. Please provide a valid Bearer token.")
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

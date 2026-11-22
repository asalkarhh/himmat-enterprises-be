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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied for URI {}: {}", request.getRequestURI(), accessDeniedException.getMessage());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationIdHolder.HEADER_NAME, CorrelationIdHolder.get());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code("FORBIDDEN")
                        .message("Insufficient permissions to perform this operation.")
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

package com.booki.config;

import com.booki.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One line per request: method, path, status, who (if authenticated) and how
 * long it took. Placed after {@code JwtAuthenticationFilter} in the security
 * chain (see SecurityConfig) so {@link SecurityUtil#currentUserId()} is
 * already populated. Deliberately never logs headers, query params, or body —
 * no auth tokens, no chat text, nothing a reader typed or said.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("http.request method={} path={} status={} userId={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    SecurityUtil.currentUserId(), System.currentTimeMillis() - startedAt);
        }
    }
}

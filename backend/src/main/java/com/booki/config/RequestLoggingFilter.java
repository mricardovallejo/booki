package com.booki.config;

import com.booki.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One line per request: method, path, status, who (if authenticated) and how
 * long it took. Placed after {@code JwtAuthenticationFilter} in the security
 * chain (see SecurityConfig) so {@link SecurityUtil#currentUserId()} is
 * already populated. Deliberately never logs headers, query params, or body —
 * no auth tokens, no chat text, nothing a reader typed or said.
 *
 * <p>Also puts {@code userId} and {@code sessionId} in the MDC, so every line
 * logged while handling the request carries them (see
 * {@code logging.pattern.correlation} in application.yml) without each call
 * site having to pass them along.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Pattern SESSION_PATH = Pattern.compile("^/api/sessions/(\\d+)(/.*)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        Long userId = SecurityUtil.currentUserId();
        if (userId != null) {
            MDC.put("userId", userId.toString());
        }
        Matcher session = SESSION_PATH.matcher(request.getRequestURI());
        if (session.matches()) {
            MDC.put("sessionId", session.group(1));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("http.request method={} path={} status={} userId={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    userId, System.currentTimeMillis() - startedAt);
            MDC.remove("userId");
            MDC.remove("sessionId");
        }
    }
}

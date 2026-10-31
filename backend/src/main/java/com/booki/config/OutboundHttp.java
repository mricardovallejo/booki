package com.booki.config;

import io.netty.channel.ChannelOption;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Shared connect/read timeouts for outbound calls to external AI and voice
 * providers. Without them a hung upstream keeps a WebClient {@code .block()} —
 * and the servlet request thread behind it — parked indefinitely.
 *
 * <p>Non-streaming callers also apply {@link #CALL_TIMEOUT} with {@code
 * Mono#timeout} as a whole-call ceiling; streaming callers use it as a per-chunk
 * idle timeout ({@code Flux#timeout}).
 */
public final class OutboundHttp {

    private OutboundHttp() {
    }

    /** Fail fast if the TCP connection can't be established. */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /**
     * Max gap with no bytes received before the response is abandoned. Reactor
     * Netty resets it on every read, so it also tolerates a slow streaming
     * response and only trips on a genuinely stalled connection.
     */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    /** Whole-call ceiling for a non-streaming request / per-chunk idle timeout for a stream. */
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);

    /** Upper bound on {@link #describeFailure}, so an upstream error page can't flood the logs. */
    static final int MAX_FAILURE_CHARS = 600;

    public static ReactorClientHttpConnector connector() {
        return new ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(RESPONSE_TIMEOUT));
    }

    /**
     * One bounded line saying why an outbound call failed, for log messages. A
     * rejection's reason is in its response body, which neither the exception's
     * message nor its stack trace includes; anything else is described by its
     * root cause (timeout, refused connection, SMTP reply, SQL error).
     */
    public static String describeFailure(Throwable failure) {
        String detail = null;
        for (Throwable t = failure; t != null && detail == null; t = t.getCause()) {
            if (t instanceof WebClientResponseException rejected) {
                detail = "status=" + rejected.getStatusCode().value() + " body=" + rejected.getResponseBodyAsString();
            }
        }
        if (detail == null) {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
            detail = root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        String oneLine = detail.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_FAILURE_CHARS ? oneLine : oneLine.substring(0, MAX_FAILURE_CHARS);
    }
}

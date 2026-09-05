package com.booki.config;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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

    public static ReactorClientHttpConnector connector() {
        return new ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(RESPONSE_TIMEOUT));
    }
}

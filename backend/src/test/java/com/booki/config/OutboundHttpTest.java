package com.booki.config;

import com.booki.ai.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundHttpTest {

    @Test
    void reportsStatusAndBodyOfAnUpstreamRejectionEvenWhenWrapped() {
        WebClientResponseException upstream = WebClientResponseException.create(
                401, "Unauthorized", HttpHeaders.EMPTY,
                "{\"error\":\n  \"invalid x-api-key\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        String description = OutboundHttp.describeFailure(new AiProviderException("claude", upstream));

        assertThat(description).isEqualTo("status=401 body={\"error\": \"invalid x-api-key\"}");
    }

    @Test
    void reportsTheRootCauseWhenThereWasNoHttpResponse() {
        Exception wrapped = new IllegalStateException("outer", new ConnectException("Connection refused"));

        assertThat(OutboundHttp.describeFailure(wrapped)).isEqualTo("ConnectException: Connection refused");
    }

    @Test
    void capsLongDetailToOneBoundedLine() {
        String description = OutboundHttp.describeFailure(new IllegalStateException("x".repeat(5000)));

        assertThat(description.length()).isLessThanOrEqualTo(OutboundHttp.MAX_FAILURE_CHARS);
    }
}

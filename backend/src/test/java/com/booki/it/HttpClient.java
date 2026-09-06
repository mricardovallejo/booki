package com.booki.it;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Minimal HTTP test client for the {@code com.booki.it} integration tests — the
 * subset of the old {@code TestRestTemplate} surface those tests use, backed by
 * {@link RestClient} (Spring Framework 7; {@code TestRestTemplate} was removed in
 * Spring Boot 4).
 *
 * <p>Configured never to throw on a 4xx/5xx so a test can assert on the returned
 * status and error body, exactly as {@code TestRestTemplate} behaved.
 */
class HttpClient {

    private final RestClient client;

    HttpClient(String baseUrl) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    <T> ResponseEntity<T> getForEntity(String uri, Class<T> type) {
        return exchange(uri, HttpMethod.GET, null, type);
    }

    <T> ResponseEntity<T> getForEntity(String uri, ParameterizedTypeReference<T> type) {
        return exchange(uri, HttpMethod.GET, null, type);
    }

    <T> ResponseEntity<T> postForEntity(String uri, Object body, Class<T> type) {
        return exchange(uri, HttpMethod.POST, new HttpEntity<>(body), type);
    }

    <T> ResponseEntity<T> postForEntity(String uri, Object body, ParameterizedTypeReference<T> type) {
        return exchange(uri, HttpMethod.POST, new HttpEntity<>(body), type);
    }

    @SuppressWarnings("unchecked")
    <T> ResponseEntity<T> exchange(String uri, HttpMethod method, HttpEntity<?> entity, Class<T> type) {
        RestClient.ResponseSpec spec = request(uri, method, entity).retrieve();
        return type == Void.class
                ? (ResponseEntity<T>) spec.toBodilessEntity()
                : spec.toEntity(type);
    }

    <T> ResponseEntity<T> exchange(String uri, HttpMethod method, HttpEntity<?> entity,
                                   ParameterizedTypeReference<T> type) {
        return request(uri, method, entity).retrieve().toEntity(type);
    }

    private RestClient.RequestBodySpec request(String uri, HttpMethod method, HttpEntity<?> entity) {
        RestClient.RequestBodySpec spec = client.method(method).uri(uri);
        if (entity != null) {
            if (!entity.getHeaders().isEmpty()) {
                spec.headers(headers -> headers.addAll(entity.getHeaders()));
            }
            if (entity.getBody() != null) {
                spec.body(entity.getBody());
            }
        }
        return spec;
    }
}

package com.booki.it;

import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.AuthResponse;
import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentication, JWT security and public-endpoint exposure — one class covers
 * {@code AuthController}, {@code UserController}, {@code SecurityConfig},
 * {@code JwtUtil} and the registration seeding in {@code AuthServiceImpl}.
 */
class AuthSecurityIT extends IntegrationTestBase {

    @Test
    void registerReturnsTokenAndUser() {
        String email = uniqueEmail("ada");
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/register", authRequest(email, "password123", "Ada"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getUser().getEmail()).isEqualTo(email);
        assertThat(response.getBody().getUser().getName()).isEqualTo("Ada");
    }

    @Test
    void registerDefaultsNameToEmailLocalPart() {
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/register", authRequest("lovelace@example.com", "password123", null), AuthResponse.class);

        assertThat(response.getBody().getUser().getName()).isEqualTo("lovelace");
    }

    @Test
    void registerSeedsEveryShippedAiProfile() {
        AuthData user = register();

        ResponseEntity<List<AiProfileSummaryResponse>> response = rest.exchange(
                "/api/ai-profiles", HttpMethod.GET, new HttpEntity<>(auth(user.token())),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AiProfileSummaryResponse> profiles = response.getBody();
        assertThat(profiles).hasSize(5);
        assertThat(profiles).extracting(AiProfileSummaryResponse::name)
                .containsExactlyInAnyOrder("Patient Tutor", "Study Buddy", "Subject Expert", "Accessible Pace",
                        "Dyslexia-Friendly Guide");
        assertThat(profiles).filteredOn(AiProfileSummaryResponse::isDefault)
                .singleElement().extracting(AiProfileSummaryResponse::name).isEqualTo("Patient Tutor");
    }

    @Test
    void duplicateEmailIsRejected() {
        String email = uniqueEmail("dup");
        register(email);

        ResponseEntity<Map<String, String>> response = rest.postForEntity("/api/auth/register", authRequest(email, "password123", null), errorType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Email already registered");
    }

    @Test
    void shortPasswordFailsValidation() {
        ResponseEntity<Map<String, String>> response = rest.postForEntity("/api/auth/register", authRequest(uniqueEmail("short"), "123", null), errorType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).contains("password");
    }

    @Test
    void loginSucceedsAndRejectsBadCredentialsUniformly() {
        String email = uniqueEmail("login");
        register(email);

        ResponseEntity<AuthResponse> ok = login(email, "password123");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().getToken()).isNotBlank();

        ResponseEntity<Map<String, String>> wrongPassword = login(email, "wrong-pass", errorType());
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().get("error")).isEqualTo("Invalid credentials");

        ResponseEntity<Map<String, String>> unknownEmail = login(uniqueEmail("ghost"), "password123", errorType());
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getBody().get("error")).isEqualTo("Invalid credentials");
    }

    @Test
    void emailIsNormalizedForRegisterAndLogin() {
        String email = uniqueEmail("mixed");

        register(email.toUpperCase());

        ResponseEntity<AuthResponse> response = login(email, "password123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUser().getEmail()).isEqualTo(email);
    }

    @Test
    void protectedEndpointsRejectAnonymousAndGarbageTokens() {
        ResponseEntity<Map<String, String>> anonymous = rest.getForEntity("/api/users/me", errorType());
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders garbage = new HttpHeaders();
        garbage.setBearerAuth("not-a-real-jwt");
        ResponseEntity<Map<String, String>> badToken = rest.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(garbage), errorType());
        assertThat(badToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userCanReadAndUpdateOwnProfile() {
        AuthData user = register(uniqueEmail("profile"));

        ResponseEntity<UserResponse> me = rest.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(auth(user.token())), UserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);

        UpdateUserRequest update = new UpdateUserRequest();
        update.setName("New Name");
        ResponseEntity<UserResponse> patched = rest.exchange("/api/users/me", HttpMethod.PATCH, new HttpEntity<>(update, auth(user.token())), UserResponse.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().getName()).isEqualTo("New Name");
    }

    @Test
    void healthAndLivenessArePublicButActuatorInfoIsNot() {
        ResponseEntity<Map<String, Object>> health = rest.getForEntity("/api/health", mapType());
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).containsEntry("status", "ok");

        ResponseEntity<Map<String, Object>> liveness = rest.getForEntity("/actuator/health/liveness", mapType());
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, String>> info = rest.getForEntity("/actuator/info", errorType());
        assertThat(info.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private com.booki.dto.AuthRequest authRequest(String email, String password, String name) {
        com.booki.dto.AuthRequest request = new com.booki.dto.AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setName(name);
        return request;
    }

    private ParameterizedTypeReference<Map<String, String>> errorType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    private ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}

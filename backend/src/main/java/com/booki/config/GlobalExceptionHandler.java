package com.booki.config;

import com.booki.ai.AiProviderException;
import com.booki.conversation.ConversationFailedException;
import com.booki.voice.VoiceTranscriptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Every handler here returns the same {@code {"error": "..."}} shape the
 * mock backend and the OpenAPI contract (docs/openapi.yaml) use, instead of
 * Spring's default ProblemDetail — the frontend reads {@code error} directly
 * off the response body (see LoginPage.tsx's error handling).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * An AI provider failed to produce a genuine response. Surfaced as 502 with
     * a neutral message — the real cause is logged, never sent to the client and
     * never persisted as a BooKI answer.
     */
    @ExceptionHandler({ConversationFailedException.class, AiProviderException.class})
    public ResponseEntity<Map<String, String>> handleAiUnavailable(RuntimeException ex) {
        log.warn("AI provider unavailable", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("The reading assistant is temporarily unavailable. Please try again in a moment."));
    }

    /**
     * Voice input could not be transcribed. 502 with a plain message — the
     * reader can retry or switch to typing. A failed reply synthesis (TTS) does
     * not reach here; that turn degrades to text-only.
     */
    @ExceptionHandler(VoiceTranscriptionException.class)
    public ResponseEntity<Map<String, String>> handleVoiceTranscription(VoiceTranscriptionException ex) {
        log.warn("Voice transcription failed", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(error(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(error("'" + ex.getRequestPartName() + "' is required"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(error("File is too large"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Resource not found"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(ex.getMessage()));
    }

    private Map<String, String> error(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message == null ? "Unexpected error" : message);
        return body;
    }
}

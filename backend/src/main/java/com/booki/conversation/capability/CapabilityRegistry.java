package com.booki.conversation.capability;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Holds every {@link ConversationCapability} bean and owns the provider-neutral
 * routing contract (see ADR-008).
 *
 * <p>Rather than native tool/function calling — which would mean four different
 * provider wire formats — the session's normal {@code converse()} call gets
 * {@link #routerInstructions()} appended to its system prompt. When a capability
 * fits, the model replies with <em>only</em> {@code {"capability":"<name>"}};
 * {@link #parseDirective(String)} recognises that (strict: the whole trimmed
 * reply must be that JSON) and nothing else. No keyword matching.
 */
@Component
public class CapabilityRegistry {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** A real prose answer is never this short; a bare routing directive always is. */
    private static final int MAX_DIRECTIVE_LENGTH = 160;

    private final Map<String, ConversationCapability> byName;

    public CapabilityRegistry(List<ConversationCapability> capabilities) {
        Map<String, ConversationCapability> map = new LinkedHashMap<>();
        for (ConversationCapability capability : capabilities) {
            map.put(capability.name(), capability);
        }
        this.byName = Collections.unmodifiableMap(map);
    }

    public Optional<ConversationCapability> find(String name) {
        return Optional.ofNullable(name).map(byName::get);
    }

    public Set<String> names() {
        return byName.keySet();
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    /** System-prompt section that lets the model opt into a capability. Empty when none are registered. */
    public String routerInstructions() {
        if (byName.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n---\n")
                .append("You have specialized capabilities. If the reader's latest message is clearly ")
                .append("better served by one of them, reply with ONLY this JSON and nothing else: ")
                .append("{\"capability\":\"<name>\"}\n")
                .append("Available capabilities:\n");
        byName.values().forEach(c -> sb.append("- ").append(c.modelDescription()).append('\n'));
        sb.append("If none clearly applies, just answer the reader normally in prose.");
        return sb.toString();
    }

    /**
     * Returns the capability name iff {@code modelReply} is exactly a routing
     * directive for a known capability; empty otherwise (including any prose,
     * malformed JSON, or an unknown name) so the caller safely treats it as a
     * normal answer.
     */
    public Optional<String> parseDirective(String modelReply) {
        if (modelReply == null) {
            return Optional.empty();
        }
        String trimmed = modelReply.strip();
        if (trimmed.length() > MAX_DIRECTIVE_LENGTH || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return Optional.empty();
        }
        try {
            JsonNode node = JSON.readTree(trimmed);
            String capability = node.path("capability").asString().strip();
            return byName.containsKey(capability) ? Optional.of(capability) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

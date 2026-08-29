package com.booki.ai;

import java.util.List;

public interface AiProvider {
    String converse(String systemPrompt, List<Message> context, String userMessage);

    /** The concrete model name this provider is configured to call — for logging only. */
    String model();

    record Message(String role, String content) {
    }
}

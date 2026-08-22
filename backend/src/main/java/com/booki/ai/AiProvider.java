package com.booki.ai;

import java.util.List;

public interface AiProvider {
    String converse(String systemPrompt, List<Message> context, String userMessage);

    record Message(String role, String content) {
    }
}

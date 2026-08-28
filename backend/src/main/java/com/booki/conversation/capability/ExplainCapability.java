package com.booki.conversation.capability;

import com.booki.ai.AiProviderRegistry;
import com.booki.service.impl.SessionContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "I didn't understand this part." Re-explains the relevant idea from the
 * session's pages in plainer terms with one concrete analogy, on the shared
 * three-layer prompt. No existing service does this, so the (small) prompt
 * lives here; it still reuses {@link SessionContextBuilder} and the session's
 * chosen provider.
 */
@Component
@RequiredArgsConstructor
public class ExplainCapability implements ConversationCapability {

    private final AiProviderRegistry aiProviderRegistry;
    private final SessionContextBuilder sessionContextBuilder;

    @Override
    public String name() {
        return "explain";
    }

    @Override
    public String modelDescription() {
        return "explain — restate the reader's current passage in simpler terms with a concrete everyday analogy; "
                + "use only when the reader says they did not understand a specific part";
    }

    @Override
    public String execute(CapabilityInvocation invocation) {
        String languageName = sessionContextBuilder.languageName(invocation.session().getLanguage());
        String systemPrompt = sessionContextBuilder.buildSystemPrompt(
                invocation.session(), invocation.pageContextText());
        String instruction = "The reader said: \"" + invocation.userText() + "\". "
                + "Re-explain the idea they are stuck on, drawn from the pages above, in " + languageName + ". "
                + "Use plain language calibrated to \"" + invocation.session().getDifficulty() + "\" difficulty "
                + "and exactly one concrete everyday analogy. Keep it to a short paragraph.";
        return aiProviderRegistry.get(invocation.session().getAiProvider())
                .converse(systemPrompt, invocation.history(), instruction)
                .strip();
    }
}

package com.booki.conversation.capability;

import com.booki.ai.AiProviderRegistry;
import com.booki.domain.SlotKey;
import com.booki.prompt.PromptAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "I didn't understand this part." Re-explains the relevant idea from the
 * session's pages on the session's layered prompt with the {@code fn_explain}
 * SlotPrompt.
 */
@Component
@RequiredArgsConstructor
public class ExplainCapability implements ConversationCapability {

    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;

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
        String systemPrompt = promptAssembler.forFunction(
                invocation.session(), SlotKey.FN_EXPLAIN,
                invocation.session().getDifficulty(), invocation.pageContextText());
        String instruction = "The reader said: \"" + invocation.userText()
                + "\". Re-explain what they are stuck on, drawn from the pages above.";
        return aiProviderRegistry.get(invocation.session().getAiProvider())
                .converse(systemPrompt, invocation.history(), instruction)
                .strip();
    }
}

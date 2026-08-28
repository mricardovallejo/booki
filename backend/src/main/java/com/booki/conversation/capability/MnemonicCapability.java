package com.booki.conversation.capability;

import com.booki.ai.AiProviderRegistry;
import com.booki.service.impl.SessionContextBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Help me remember this." Builds a memory aid (acronym, vivid image, or short
 * rhyme) for the key ideas in the session's pages. Same shape as
 * {@link ExplainCapability}: a small prompt on top of the shared three-layer
 * context and the session's provider — no new subsystem.
 */
@Component
@RequiredArgsConstructor
public class MnemonicCapability implements ConversationCapability {

    private final AiProviderRegistry aiProviderRegistry;
    private final SessionContextBuilder sessionContextBuilder;

    @Override
    public String name() {
        return "mnemonic";
    }

    @Override
    public String modelDescription() {
        return "mnemonic — create a memory aid (acronym, vivid image, or short rhyme) for the key ideas in these pages; "
                + "use when the reader asks for help memorizing or remembering the material";
    }

    @Override
    public String execute(CapabilityInvocation invocation) {
        String languageName = sessionContextBuilder.languageName(invocation.session().getLanguage());
        String systemPrompt = sessionContextBuilder.buildSystemPrompt(
                invocation.session(), invocation.pageContextText());
        String instruction = "Identify the 3–5 most important points a reader should remember from the pages above, "
                + "then give ONE memory aid that ties them together — an acronym, a vivid mental image, or a short "
                + "rhyme. Reply in " + languageName + ". Show the memory aid first, then a one-line note on how to use it.";
        return aiProviderRegistry.get(invocation.session().getAiProvider())
                .converse(systemPrompt, invocation.history(), instruction)
                .strip();
    }
}

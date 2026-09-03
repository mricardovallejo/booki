package com.booki.conversation.capability;

import com.booki.ai.AiProviderRegistry;
import com.booki.domain.SlotKey;
import com.booki.prompt.PromptAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Help me remember this." Builds a memory aid for the key ideas in the session's
 * pages on the session's layered prompt with the {@code fn_mnemonic} SlotPrompt.
 */
@Component
@RequiredArgsConstructor
public class MnemonicCapability implements ConversationCapability {

    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;

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
        String systemPrompt = promptAssembler.forFunction(
                invocation.session(), SlotKey.FN_MNEMONIC,
                invocation.session().getDifficulty(), invocation.pageContextText());
        String instruction = "The reader said: \"" + invocation.userText()
                + "\". Build the memory aid for the key points of the pages above.";
        return aiProviderRegistry.get(invocation.session().getAiProvider())
                .converse(systemPrompt, invocation.history(), instruction)
                .strip();
    }
}

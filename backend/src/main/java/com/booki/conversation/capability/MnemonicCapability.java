package com.booki.conversation.capability;

import com.booki.ai.ActivityContentService;
import com.booki.ai.AiProvider;
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
    private final ActivityContentService activityContentService;

    @Override
    public String name() {
        return "mnemonic";
    }

    @Override
    public String modelDescription() {
        return "mnemonic — build a memory aid for the key points of these pages; "
                + "use when the reader asks for help memorising or remembering";
    }

    @Override
    public String execute(CapabilityInvocation invocation) {
        String systemPrompt = promptAssembler.forFunction(
                invocation.session(), SlotKey.FN_MNEMONIC, invocation.session().getDifficulty(),
                activityContentService.documentTextFor(invocation.content()));
        String instruction = "The reader said: \"" + invocation.userText()
                + "\". Build the memory aid for the key points of the pages above.";
        AiProvider provider = aiProviderRegistry.get(invocation.session().getAiProvider());
        return activityContentService.converse(provider, invocation.content(), systemPrompt, invocation.history(), instruction)
                .strip();
    }
}

package com.booki.conversation.capability;

import com.booki.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Ask me a question about what I just read." Delegates straight to
 * {@link QuizService#generateComprehensionQuestion} — the reader's answer and
 * any "give me a hint" follow-up are then ordinary chat turns, since the model
 * has the question in history and the pages in context.
 */
@Component
@RequiredArgsConstructor
public class QuizCapability implements ConversationCapability {

    private final QuizService quizService;

    @Override
    public String name() {
        return "quiz";
    }

    @Override
    public String modelDescription() {
        return "quiz — ask the reader ONE short comprehension question about what they are currently reading; "
                + "use when they ask to be quizzed, tested, or checked on their understanding";
    }

    @Override
    public String execute(CapabilityInvocation invocation) {
        return quizService.generateComprehensionQuestion(invocation.session());
    }
}

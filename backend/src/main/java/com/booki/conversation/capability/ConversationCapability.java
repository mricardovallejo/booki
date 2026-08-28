package com.booki.conversation.capability;

/**
 * A specialized thing BooKI can do inside a conversation — ask a comprehension
 * question, summarize, explain a passage, build a mnemonic — without the reader
 * leaving the chat.
 *
 * <p>Deliberately tiny: this is not an agent framework. A capability produces
 * the user-facing reply text for one turn and nothing else. It reuses the
 * existing services ({@code QuizService}, {@code ReportService}) rather than
 * re-implementing their logic.
 *
 * <p>The model decides when a capability applies (see
 * {@link CapabilityRegistry#routerInstructions()}); quick-action buttons can
 * also request one explicitly via {@code ConversationRequest.capabilityHint}.
 */
public interface ConversationCapability {

    /** Stable identifier used in the routing directive and the quick-action hint (e.g. {@code "quiz"}). */
    String name();

    /** One line shown to the model so it can decide whether this capability fits the reader's message. */
    String modelDescription();

    /** Runs the capability and returns the reply text to persist as BooKI's answer for this turn. */
    String execute(CapabilityInvocation invocation);
}

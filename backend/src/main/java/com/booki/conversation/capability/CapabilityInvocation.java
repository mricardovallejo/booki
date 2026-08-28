package com.booki.conversation.capability;

import com.booki.ai.AiProvider;
import com.booki.domain.Session;

import java.util.List;

/**
 * Everything a {@link ConversationCapability} needs for one turn, already
 * resolved and ownership-checked by {@code ConversationEngine}.
 *
 * @param session          the reading session (its provider, language, page range, difficulty, Profile Master)
 * @param userText         the reader's latest message
 * @param history          recent conversation, chronological, for capabilities that want it (explain / mnemonic)
 * @param pageContextText  the session's page-range text, already size-capped
 */
public record CapabilityInvocation(
        Session session,
        String userText,
        List<AiProvider.Message> history,
        String pageContextText) {
}

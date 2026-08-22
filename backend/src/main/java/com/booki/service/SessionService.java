package com.booki.service;

import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SessionContextResponse;
import com.booki.dto.SessionNotificationResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;

import java.util.List;

public interface SessionService {
    SessionResponse createSession(Long userId, SessionRequest request);
    SessionResponse getSession(Long userId, Long sessionId);
    SessionContextResponse getContext(Long userId, Long sessionId);
    SessionResponse updateCurrentPage(Long userId, Long sessionId, Integer currentPage);
    List<MessageResponse> getMessages(Long userId, Long sessionId);
    MessageResponse sendMessage(Long userId, Long sessionId, MessageRequest request);
    SessionProgressResponse getProgress(Long userId, Long sessionId);
    List<SessionNotificationResponse> getNotifications(Long userId, Long sessionId);
}

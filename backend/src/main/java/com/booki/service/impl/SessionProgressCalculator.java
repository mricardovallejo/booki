package com.booki.service.impl;

import com.booki.domain.Session;
import com.booki.dto.SessionProgressResponse;
import com.booki.repository.MessageRepository;
import com.booki.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared by the /progress endpoint and the PDF report generators. */
@Component
@RequiredArgsConstructor
public class SessionProgressCalculator {

    private final MessageRepository messageRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public SessionProgressResponse compute(Session session) {
        int totalPages = session.getDocument().getPageCount() - session.getStartPage() + 1;
        int pagesRead = session.getEndPage() - session.getStartPage() + 1;
        int pctRead = totalPages == 0 ? 0 : Math.round((pagesRead * 100f) / totalPages);

        // Aggregate in the database rather than loading every message/attempt row
        // just to size and average them.
        int messageCount = (int) messageRepository.countBySessionId(session.getId());
        int quizzesTaken = (int) quizAttemptRepository.countBySessionId(session.getId());
        double avg = quizAttemptRepository.averageScoreBySessionId(session.getId());

        return new SessionProgressResponse(
                Math.max(0, Math.min(pagesRead, totalPages)),
                totalPages,
                Math.max(0, Math.min(pctRead, 100)),
                messageCount,
                quizzesTaken,
                (int) Math.round(avg * 100)
        );
    }
}

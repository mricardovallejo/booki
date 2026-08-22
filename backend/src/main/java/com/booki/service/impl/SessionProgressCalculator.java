package com.booki.service.impl;

import com.booki.domain.QuizAttempt;
import com.booki.domain.Session;
import com.booki.dto.SessionProgressResponse;
import com.booki.repository.MessageRepository;
import com.booki.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Shared by the /progress endpoint and the PDF report generators. */
@Component
@RequiredArgsConstructor
public class SessionProgressCalculator {

    private final MessageRepository messageRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public SessionProgressResponse compute(Session session) {
        int totalPages = session.getEndPage() - session.getStartPage() + 1;
        int pagesRead = session.getCurrentPage() - session.getStartPage() + 1;
        int pctRead = totalPages == 0 ? 0 : Math.round((pagesRead * 100f) / totalPages);

        int messageCount = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).size();
        List<QuizAttempt> attempts = quizAttemptRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        int quizzesTaken = attempts.size();
        double avg = attempts.isEmpty() ? 0 : attempts.stream().mapToDouble(QuizAttempt::getScore).average().orElse(0);

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

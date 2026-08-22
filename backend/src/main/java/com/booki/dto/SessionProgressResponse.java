package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionProgressResponse {
    private int pagesRead;
    private int totalPages;
    private int pctRead;
    private int messageCount;
    private int quizzesTaken;
    private int quizAverageScore;
}

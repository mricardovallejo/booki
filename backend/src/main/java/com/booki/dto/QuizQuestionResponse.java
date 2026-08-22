package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuizQuestionResponse {
    private Integer id;
    private Integer pageNumber;
    private String question;
}

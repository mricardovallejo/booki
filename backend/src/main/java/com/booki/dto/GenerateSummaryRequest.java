package com.booki.dto;

import lombok.Data;

@Data
public class GenerateSummaryRequest {
    private Integer lengthPages;
    private String prompt;
    private Boolean includeCover;
    private String deliverAs;
    private String email;
}

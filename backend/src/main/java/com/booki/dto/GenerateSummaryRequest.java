package com.booki.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateSummaryRequest {

    @Min(1)
    @Max(10)
    private Integer lengthPages;

    /** The shared activity range — required, same one every other activity uses. */
    @NotNull
    @Min(1)
    private Integer startPage;

    @NotNull
    @Min(1)
    private Integer endPage;

    @Size(max = 2000)
    private String prompt;

    private Boolean includeCover;

    @Pattern(regexp = "chat|pdf")
    private String deliverAs;

    @Email
    private String email;
}

package com.booki.dto;

import com.booki.email.EmailList;
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

    /** physical|editorial; null means "physical". */
    private String pageMode;

    @Size(max = 2000)
    private String prompt;

    private Boolean includeCover;

    @Pattern(regexp = "chat|pdf")
    private String deliverAs;

    /** Only used when deliverAs is pdf; optional even then. One address, or several separated by ";". */
    @Pattern(regexp = EmailList.REGEX)
    private String email;
}

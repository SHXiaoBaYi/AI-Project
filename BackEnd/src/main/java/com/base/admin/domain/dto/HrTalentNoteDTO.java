package com.base.admin.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "人才跟进备注")
public class HrTalentNoteDTO {

    @NotNull
    @Schema(description = "人才ID")
    private Long candidateId;

    @NotBlank
    @Size(max = 1000)
    @Schema(description = "跟进内容")
    private String content;

    @Schema(description = "下次跟进日期")
    private LocalDate nextFollowAt;
}

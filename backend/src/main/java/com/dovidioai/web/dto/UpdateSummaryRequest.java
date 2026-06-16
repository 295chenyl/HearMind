package com.dovidioai.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSummaryRequest {

    @NotBlank(message = "摘要内容不能为空")
    private String content;
}

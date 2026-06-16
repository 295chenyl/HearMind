package com.dovidioai.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportUrlRequest {

    @NotBlank(message = "URL 不能为空")
    private String url;
}

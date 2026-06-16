package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportHealthResponse {

    private boolean globalCookieConfigured;
    private boolean bilibiliCookieHint;
}

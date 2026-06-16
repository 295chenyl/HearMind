package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImportHealthResponse {

    private boolean globalCookieConfigured;
    /** @deprecated 使用 cookieStatus */
    private boolean bilibiliCookieHint;
    private String cookieStatus;
    private Long sessdataExpiresAtEpochSec;
    private String sessdataExpiresAt;
    private Integer daysUntilExpiry;
    private String cookieMessage;
}

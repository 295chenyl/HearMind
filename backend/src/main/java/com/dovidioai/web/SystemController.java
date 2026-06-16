package com.dovidioai.web;

import com.dovidioai.service.importing.BilibiliCookieService;
import com.dovidioai.service.importing.BilibiliCookieService.CookieStatus;
import com.dovidioai.web.dto.ImportHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final BilibiliCookieService bilibiliCookieService;

    @GetMapping("/import-health")
    public ImportHealthResponse importHealth() {
        CookieStatus status = bilibiliCookieService.getStatus();
        boolean needsHint = BilibiliCookieService.STATUS_MISSING.equals(status.status())
                || BilibiliCookieService.STATUS_INVALID.equals(status.status())
                || BilibiliCookieService.STATUS_EXPIRED.equals(status.status())
                || BilibiliCookieService.STATUS_WARN.equals(status.status());
        return ImportHealthResponse.builder()
                .globalCookieConfigured(status.configured())
                .bilibiliCookieHint(needsHint)
                .cookieStatus(status.status())
                .sessdataExpiresAtEpochSec(status.sessdataExpiresAtEpochSec())
                .sessdataExpiresAt(status.sessdataExpiresAt())
                .daysUntilExpiry(status.daysUntilExpiry())
                .cookieMessage(status.message())
                .build();
    }
}

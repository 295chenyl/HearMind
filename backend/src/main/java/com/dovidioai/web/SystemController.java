package com.dovidioai.web;

import com.dovidioai.service.importing.YtDlpService;
import com.dovidioai.web.dto.ImportHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final YtDlpService ytDlpService;

    @GetMapping("/import-health")
    public ImportHealthResponse importHealth() {
        boolean globalCookie = ytDlpService.isGlobalCookieConfigured();
        return ImportHealthResponse.builder()
                .globalCookieConfigured(globalCookie)
                .bilibiliCookieHint(!globalCookie)
                .build();
    }
}

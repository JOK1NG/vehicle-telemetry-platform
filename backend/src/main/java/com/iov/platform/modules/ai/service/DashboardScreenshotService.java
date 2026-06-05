package com.iov.platform.modules.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.auth.service.AuthUserDetails;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardScreenshotService {

    private final ObjectMapper objectMapper;

    @Value("${ai.dashboard-screenshot.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${ai.dashboard-screenshot.chrome-executable-path:}")
    private String chromeExecutablePath;

    @Value("${ai.dashboard-screenshot.timeout-ms:15000}")
    private double timeoutMs;

    @Value("${ai.dashboard-screenshot.viewport-width:1280}")
    private int viewportWidth;

    @Value("${ai.dashboard-screenshot.viewport-height:720}")
    private int viewportHeight;

    public byte[] captureDashboardPng(String authorizationHeader, AuthUserDetails user) {
        String token = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("无法生成大屏截图：缺少 Authorization Bearer token");
        }
        if (user == null) {
            throw new IllegalArgumentException("无法生成大屏截图：当前用户未认证");
        }

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of("--disable-gpu", "--no-sandbox"));
            if (StringUtils.hasText(chromeExecutablePath) && Files.isExecutable(Path.of(chromeExecutablePath))) {
                launchOptions.setExecutablePath(Path.of(chromeExecutablePath));
                log.debug("Using dashboard screenshot browser executable: {}", chromeExecutablePath);
            }

            try (Browser browser = playwright.chromium().launch(launchOptions);
                 BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                         .setViewportSize(viewportWidth, viewportHeight))) {
                context.addInitScript(buildAuthInitScript(token, user));

                Page page = context.newPage();
                page.setDefaultTimeout(timeoutMs);
                page.navigate(dashboardUrl(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForSelector(".view-in");
                page.waitForTimeout(1_500);

                return page.screenshot(new Page.ScreenshotOptions()
                        .setFullPage(true)
                        .setType(com.microsoft.playwright.options.ScreenshotType.PNG));
            }
        }
    }

    private String dashboardUrl() {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/dashboard";
    }

    private String buildAuthInitScript(String token, AuthUserDetails user) {
        Map<String, Object> userPayload = Map.of(
                "id", user.getSysUser().getId(),
                "username", user.getSysUser().getUsername(),
                "role", user.getSysUser().getRole()
        );
        try {
            return "window.localStorage.setItem('token', " + objectMapper.writeValueAsString(token) + ");"
                    + "window.localStorage.setItem('user', JSON.stringify("
                    + objectMapper.writeValueAsString(userPayload)
                    + "));";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化截图认证上下文", e);
        }
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) return null;
        String prefix = "Bearer ";
        return authorizationHeader.startsWith(prefix)
                ? authorizationHeader.substring(prefix.length()).trim()
                : authorizationHeader.trim();
    }
}

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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    @Value("${ai.dashboard-screenshot.wait-after-load-ms:1500}")
    private double waitAfterLoadMs;

    public byte[] captureDashboardPng(String authorizationHeader, AuthUserDetails user) {
        DashboardPageSnapshot snapshot = captureDashboardSnapshot(authorizationHeader, user, true);
        if (snapshot.imageBytes() == null || snapshot.imageBytes().length == 0) {
            throw new IllegalStateException("无法生成大屏截图：截图结果为空");
        }
        return snapshot.imageBytes();
    }

    public DashboardPageSnapshot captureDashboardSnapshot(
            String authorizationHeader,
            AuthUserDetails user,
            boolean includeImage) {
        String token = extractBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("无法生成大屏快照：缺少 Authorization Bearer token");
        }
        if (user == null) {
            throw new IllegalArgumentException("无法生成大屏快照：当前用户未认证");
        }

        try (Playwright playwright = Playwright.create(skipBrowserDownloadOptions())) {
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
                navigateDashboard(page);
                page.waitForSelector(".view-in");
                page.waitForTimeout(waitAfterLoadMs);

                String visibleText = normalizeVisibleText(page.textContent("body"));
                byte[] imageBytes = includeImage
                        ? page.screenshot(new Page.ScreenshotOptions()
                                .setFullPage(true)
                                .setType(com.microsoft.playwright.options.ScreenshotType.PNG))
                        : null;
                return new DashboardPageSnapshot(imageBytes, visibleText);
            }
        }
    }

    private String dashboardUrl() {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/dashboard";
    }

    private void navigateDashboard(Page page) {
        RuntimeException lastFailure = null;
        for (String url : dashboardUrlCandidates()) {
            try {
                page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                log.debug("Dashboard screenshot navigation failed for {}: {}", url, e.getMessage());
            }
        }
        throw lastFailure != null ? lastFailure : new IllegalStateException("无法打开监控大屏页面");
    }

    private List<String> dashboardUrlCandidates() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        String primary = dashboardUrl();
        urls.add(primary);

        try {
            URI uri = new URI(primary);
            String host = uri.getHost();
            if ("localhost".equalsIgnoreCase(host)) {
                urls.add(rewriteHost(uri, "127.0.0.1"));
                urls.add(rewriteHost(uri, "[::1]"));
            }
        } catch (URISyntaxException e) {
            log.debug("Failed to parse dashboard URL candidate: {}", primary, e);
        }

        return new ArrayList<>(urls);
    }

    private static String rewriteHost(URI uri, String host) throws URISyntaxException {
        String authority = host;
        if (uri.getPort() >= 0) {
            authority += ":" + uri.getPort();
        }
        return new URI(
                uri.getScheme(),
                authority,
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
        ).toString();
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
        if (!StringUtils.hasText(authorizationHeader)) return "";
        String prefix = "Bearer ";
        return authorizationHeader.startsWith(prefix)
                ? authorizationHeader.substring(prefix.length()).trim()
                : authorizationHeader.trim();
    }

    private static String normalizeVisibleText(String text) {
        if (!StringUtils.hasText(text)) return "";
        return text.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static Playwright.CreateOptions skipBrowserDownloadOptions() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        return new Playwright.CreateOptions().setEnv(env);
    }

    public record DashboardPageSnapshot(byte[] imageBytes, String visibleText) {
    }
}

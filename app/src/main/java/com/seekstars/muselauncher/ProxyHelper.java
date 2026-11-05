package com.seekstars.muselauncher;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import java.util.concurrent.Executor;

/**
 * 按账号应用/清除 WebView 代理。
 * ProxyController 是 App 级全局开关，本应用同一时刻只有一个账号在前台，切换时应用对应代理即可。
 * proxy 为空表示清除覆盖，走系统网络。
 */
public class ProxyHelper {

    private static ProxyController controller;
    private static final Executor EXEC = Runnable::run;

    private ProxyHelper() {
    }

    public static boolean supported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);
    }

    public static void apply(String proxy) {
        if (!supported()) return;
        try {
            if (controller == null) controller = ProxyController.getInstance();
            if (proxy == null || proxy.trim().isEmpty()) {
                controller.clearProxyOverride(EXEC, () -> {
                });
                return;
            }
            String rule = normalize(proxy.trim());
            ProxyConfig config = new ProxyConfig.Builder()
                    .addProxyRule(rule)
                    .addDirect()
                    .build();
            controller.setProxyOverride(config, EXEC, () -> {
            });
        } catch (Exception ignored) {
        }
    }

    /** 统一代理地址格式：补全 scheme（默认 http） */
    public static String normalize(String p) {
        String lower = p.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("socks5://") || lower.startsWith("socks4://")) {
            return p;
        }
        return "http://" + p;
    }
}

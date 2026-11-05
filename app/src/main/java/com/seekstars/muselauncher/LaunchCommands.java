package com.seekstars.muselauncher;

import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

/**
 * am start 调试/自动化参数的解析结果。
 * 解析逻辑抽成纯 Java（parse(Map)）以便单元测试；Intent 仅负责取 Bundle 转成 Map。
 * 这些参数只在 debug 构建生效（MainActivity 里用 BuildConfig.DEBUG 门控）。
 */
public final class LaunchCommands {

    public final boolean showDrawer;
    public final long startAccount;        // -1 = 不切换
    public final boolean hasFullscreen;    // 是否带了 fullscreen 参数
    public final boolean fullscreen;
    public final boolean hasOpenConfig;    // 是否带了 open_config 参数
    public final long openConfigId;        // -1 = 当前账号
    public final String proxyPreset;       // null = 不预填
    public final boolean proxyAutoTest;
    public final boolean proxySave;
    public final String searchQuery;       // null = 不设置
    public final boolean dumpAuth;
    public final boolean addAccount;
    public final boolean hasDelAccount;    // 是否带了 del_account 参数
    public final long delAccountId;        // -1 = 无效

    private LaunchCommands(Map<String, Object> e) {
        showDrawer = bool(e, "show_drawer");
        startAccount = lon(e, "start_account");
        hasFullscreen = e.containsKey("fullscreen");
        fullscreen = bool(e, "fullscreen");
        hasOpenConfig = e.containsKey("open_config");
        openConfigId = lon(e, "open_config");
        proxyPreset = str(e, "proxy_preset");
        proxyAutoTest = bool(e, "proxy_autotest");
        proxySave = bool(e, "proxy_save");
        searchQuery = str(e, "search_query");
        dumpAuth = bool(e, "dump_auth");
        addAccount = bool(e, "add_account");
        hasDelAccount = e.containsKey("del_account");
        delAccountId = lon(e, "del_account");
    }

    /** release 构建（BuildConfig.DEBUG == false）不响应任何调试参数 */
    public static boolean enabled(boolean debugBuild) {
        return debugBuild;
    }

    public static LaunchCommands fromIntent(Intent intent) {
        Map<String, Object> raw = new HashMap<>();
        if (intent != null && intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Object v = intent.getExtras().get(key);
                if (v != null) raw.put(key, v);
            }
        }
        return new LaunchCommands(raw);
    }

    /** 供单元测试：直接从 typed map 解析 */
    public static LaunchCommands parse(Map<String, Object> extras) {
        return new LaunchCommands(extras == null ? new HashMap<>() : extras);
    }

    private static boolean bool(Map<String, Object> e, String key) {
        return e.get(key) instanceof Boolean && (Boolean) e.get(key);
    }

    private static long lon(Map<String, Object> e, String key) {
        return e.get(key) instanceof Long ? (Long) e.get(key) : -1L;
    }

    private static String str(Map<String, Object> e, String key) {
        return e.get(key) instanceof String ? (String) e.get(key) : null;
    }
}

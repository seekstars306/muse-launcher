package com.seekstars.muselauncher;

import android.app.DownloadManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

public class MainActivity extends AppCompatActivity {

    private static final String START_URL = "https://muse.ai/";
    private static final int REQ_FILE = 10001;
    private static final int REQ_CONFIG = 10002;
    private static final long MIN_SPLASH_MS = 500;
    private static final long SPLASH_TIMEOUT_MS = 8000;
    private static final int COLOR_TEXT = 0xFFE9EBEE;
    private static final int COLOR_MUTED = 0xFF8A9099;
    private static final int COLOR_ICON = 0xFF7D838C;
    /** muse.ai 登录会话 cookie（httpOnly）。匿名访问只会落下 hatch_native_auth_* 等流程 cookie。 */
    private static final String AUTH_COOKIE_MARKER = "hatch_sess=";

    private AccountsStore store;
    private ProfileStore profileStore;

    private DrawerLayout drawerLayout;
    private View drawerContent;
    private View rail;
    private View edgeSwipe;
    private LinearLayout railAvatars;
    private LinearLayout accountList;
    private EditText searchInput;
    private View drawerEmpty;
    private TextView accCount;
    private FrameLayout webContainer;
    private View emptyState;
    private View splash;
    private TextView splashName;

    /** 每个账号一个独立 WebView（Profile 只能在 WebView 初始化前设置一次） */
    private static final int MAX_ALIVE_WEBVIEWS = 4; // LRU：最多同时保留的账号页面数（含当前）
    private final LruAccountRegistry<WebView> webViews = new LruAccountRegistry<>(MAX_ALIVE_WEBVIEWS);
    private final SparseArray<Boolean> webLoaded = new SparseArray<>();
    private WebView currentWebView;

    private long editId = -1;
    private long confirmDelId = -1;
    private EditText renameInput;
    private long splashShownAt;
    private final Runnable hideSplashRunnable = this::hideSplash;
    private ValueCallback<Uri[]> fileCallback;
    private boolean fullscreen;
    private float edgeStartX;
    private long edgeDownAt;

    /** M4: 登录态分块探测。androidx Profile API 强制主线程调用，无法后台化；
     *  改为主线程分块（每块 20 个账号，块间让出一帧），账号多时不阻塞 UI，脏标记合并请求 */
    private static final int PROBE_CHUNK = 20;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean probeRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean probeDirty =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private List<Account> probeQueue;
    private int probeIndex;
    private boolean probeChanged;

    private final Runnable probeStep = this::probeStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            showWebViewUpgradeDialog();
            return;
        }

        setContentView(R.layout.activity_main);
        store = new AccountsStore(this);
        checkStoreNotice();
        profileStore = ProfileStore.getInstance();

        drawerLayout = findViewById(R.id.drawerLayout);
        drawerContent = findViewById(R.id.drawerContent);
        rail = findViewById(R.id.rail);
        edgeSwipe = findViewById(R.id.edgeSwipe);
        railAvatars = findViewById(R.id.railAvatars);
        accountList = findViewById(R.id.accountList);
        searchInput = findViewById(R.id.drawerSearch);
        drawerEmpty = findViewById(R.id.drawerEmpty);
        accCount = findViewById(R.id.accCount);
        webContainer = findViewById(R.id.webContainer);
        emptyState = findViewById(R.id.emptyState);
        splash = findViewById(R.id.splash);
        splashName = findViewById(R.id.splashName);

        findViewById(R.id.btnRailExpand).setOnClickListener(v ->
                drawerLayout.openDrawer(drawerContent));
        findViewById(R.id.btnCollapse).setOnClickListener(v ->
                drawerLayout.closeDrawer(drawerContent));
        findViewById(R.id.btnRailAdd).setOnClickListener(v -> addAccount());
        findViewById(R.id.btnAddAccount).setOnClickListener(v -> addAccount());
        findViewById(R.id.btnEmptyAdd).setOnClickListener(v -> addAccount());
        findViewById(R.id.btnFullscreen).setOnClickListener(v -> enterFullscreen());

        // 搜索框：输入即时按名称 + 标签过滤
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderAccounts();
            }
        });

        edgeSwipe.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    edgeStartX = event.getX();
                    edgeDownAt = SystemClock.uptimeMillis();
                    return true;
                case MotionEvent.ACTION_UP: {
                    float dx = event.getX() - edgeStartX;
                    boolean swipeIn = dx > dp(24);
                    boolean tap = Math.abs(dx) < dp(8)
                            && (SystemClock.uptimeMillis() - edgeDownAt) < 500;
                    if (swipeIn || tap) exitFullscreen();
                    return true;
                }
                default:
                    return true;
            }
        });

        Long cur = store.getCurrentId();
        if (cur == null || cur < 0 || store.findById(cur) == null) {
            if (store.all().isEmpty()) {
                renderAccounts();
            } else {
                switchTo(store.all().get(0).id, false);
            }
        } else {
            switchTo(cur, false);
        }

        // 恢复上次的全屏状态；调试 extra 优先
        if (store.isFullscreen()) enterFullscreen();

        // 调试入口：am start --ez show_drawer true / --el start_account <id> / --ez fullscreen true
        Intent launchIntent = getIntent();
        if (launchIntent != null) handleLaunchExtras(launchIntent);
    }

    @Override
    protected void onNewIntent(Intent launchIntent) {
        super.onNewIntent(launchIntent);
        // 应用已在前台时 am start 的 extra 走这里，同样要生效
        setIntent(launchIntent);
        if (launchIntent != null) handleLaunchExtras(launchIntent);
    }

    private void handleLaunchExtras(Intent launchIntent) {
        // M1: 调试/自动化参数仅 debug 构建响应；release 对 intent extra 不做任何动作，
        // 避免第三方应用通过显式 intent 操纵账号配置（如改写代理）。
        if (!LaunchCommands.enabled(BuildConfig.DEBUG)) return;
        LaunchCommands cmd = LaunchCommands.fromIntent(launchIntent);
        // 先切账号（switchTo 会关抽屉），再处理全屏，最后开抽屉，保证终态符合预期
        if (cmd.startAccount >= 0 && store.findById(cmd.startAccount) != null) {
            switchTo(cmd.startAccount, true);
        }
        if (cmd.hasFullscreen) {
            if (cmd.fullscreen) {
                enterFullscreen();
            } else {
                exitFullscreen();
            }
        }
        if (cmd.showDrawer) {
            drawerLayout.openDrawer(drawerContent);
        }
        if (cmd.hasOpenConfig) {
            long cfgId = cmd.openConfigId >= 0 ? cmd.openConfigId : store.getCurrentId();
            if (cfgId >= 0) {
                openConfig(cfgId, cmd.proxyPreset, cmd.proxyAutoTest, cmd.proxySave);
            }
        }
        if (cmd.searchQuery != null) {
            searchInput.setText(cmd.searchQuery);
        }
        if (cmd.dumpAuth) {
            dumpAuthState();
        }
        if (cmd.addAccount) {
            addAccount();
        }
        if (cmd.hasDelAccount && cmd.delAccountId >= 0 && store.findById(cmd.delAccountId) != null) {
            deleteAccount(cmd.delAccountId);
        }
    }

    private void dumpAuthState() {
        for (Account a : store.all()) {
            Profile p = profileStore.getProfile(profileName(a.id));
            String cookies = null;
            try {
                cookies = p != null ? p.getCookieManager().getCookie("https://muse.ai/") : null;
            } catch (Exception ignored) {
            }
            Log.i("MuseAuth", "acct=" + a.id + " name=" + a.name
                    + " cookies=" + cookieNames(cookies));
            WebView wv = webViews.get(a.id);
            if (wv != null) {
                wv.evaluateJavascript(
                        "(function(){try{return 'LS:'+(Object.keys(localStorage).join(',')||'-')"
                                + "+'|CK:'+document.cookie}catch(e){return 'ERR:'+e}})()",
                        v -> Log.i("MuseAuth", "acct=" + a.id + " js=" + v));
            } else {
                Log.i("MuseAuth", "acct=" + a.id + " js=<no webview>");
            }
        }
    }

    private static String cookieNames(String header) {
        if (header == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (String c : header.split(";")) {
            c = c.trim();
            int eq = c.indexOf('=');
            sb.append(eq > 0 ? c.substring(0, eq) : c).append(' ');
        }
        return sb.append(']').toString();
    }

    // ---------- 全屏模式 ----------

    private void enterFullscreen() {
        if (fullscreen) return;
        fullscreen = true;
        store.setFullscreen(true);
        rail.setVisibility(View.GONE);
        edgeSwipe.setVisibility(View.VISIBLE);
        // 锁定抽屉，避免左缘手势先被抽屉拦截
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        drawerLayout.closeDrawer(drawerContent);
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        store.setFullscreen(false);
        rail.setVisibility(View.VISIBLE);
        edgeSwipe.setVisibility(View.GONE);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    // ---------- WebView（每账号独立实例） ----------

    private WebView getOrCreateWebView(final Account a) {
        WebView wv = webViews.get(a.id);
        if (wv != null) return wv;

        wv = new WebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.setBackgroundColor(0xFF121316);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        // 去掉 WebView 标识（; wv / Version/4.0），否则 muse.ai 会把页面重定向到 Play 商店
        String ua = s.getUserAgentString();
        if (ua != null) {
            String chromeUa = ua.replace("; wv", "");
            chromeUa = chromeUa.replaceAll("\\s*Version/[\\d.]+", "");
            s.setUserAgentString(chromeUa);
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webLoaded.put((int) a.id, true);
                if (a.id != store.getCurrentId()) return;
                long elapsed = SystemClock.uptimeMillis() - splashShownAt;
                webContainer.postDelayed(hideSplashRunnable,
                        Math.max(0, MIN_SPLASH_MS - elapsed));
                refreshLogged();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // M2: 渲染进程崩溃时销毁并重建该账号的 WebView，返回 true 拦截，避免拖垮整个应用。
                // 账号的 Profile 数据不受影响，重建后仍是同一隔离环境（登录态保留）。
                Long crashedId = null;
                for (Map.Entry<Long, WebView> en : webViews.snapshot().entrySet()) {
                    if (en.getValue() == view) {
                        crashedId = en.getKey();
                        break;
                    }
                }
                webViews.remove(crashedId != null ? crashedId : -1L);
                if (crashedId != null) webLoaded.remove(crashedId.intValue());
                webContainer.removeView(view);
                view.destroy();

                boolean wasCurrent = crashedId != null
                        && crashedId.longValue() == store.getCurrentId();
                if (wasCurrent) {
                    currentWebView = null;
                    Account ca = store.findById(crashedId);
                    WebView nv = ca != null ? getOrCreateWebView(ca) : null;
                    if (nv != null) {
                        nv.setVisibility(View.VISIBLE);
                        currentWebView = nv;
                    }
                }
                if (crashedId != null) {
                    Log.i("MuseWebview", "renderer gone, id=" + crashedId + " rebuilt=" + wasCurrent);
                    toast(getString(R.string.toast_webview_recovered));
                }
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    toast(getString(R.string.toast_no_filechooser));
                    return false;
                }
            }
        });

        wv.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(req);
                toast(getString(R.string.toast_download_started));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
            }
        });

        // Profile 必须在任何页面加载前设置，且每个 WebView 只能设置一次
        try {
            WebViewCompat.setProfile(wv, profileName(a.id));
        } catch (Exception e) {
            wv.destroy();
            toast("创建隔离环境失败：" + e.getMessage());
            return null;
        }

        wv.loadUrl(START_URL);
        webContainer.addView(wv);
        webViews.put(a.id, wv);
        return wv;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        if (requestCode == REQ_CONFIG && resultCode == RESULT_OK) {
            // 配置已保存：代理/头像/名称可能变化，重新应用并重渲染
            Account cur = store.findById(store.getCurrentId());
            if (cur != null) ProxyHelper.apply(cur.proxy);
            renderAccounts();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentWebView != null) currentWebView.onResume();
        // 配置页等可能在别处改过存储；刷新内存列表并重应用当前账号代理
        store.reload();
        checkStoreNotice();
        refreshLogged();
        Account cur = store.findById(store.getCurrentId());
        ProxyHelper.apply(cur != null ? cur.proxy : "");
    }

    @Override
    protected void onPause() {
        if (currentWebView != null) currentWebView.onPause();
        try {
            for (Account a : store.all()) {
                Profile p = profileStore.getProfile(profileName(a.id));
                if (p != null) p.getCookieManager().flush();
            }
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
        } else if (drawerLayout.isDrawerOpen(drawerContent)) {
            drawerLayout.closeDrawer(drawerContent);
        } else if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    // ---------- 账号操作 ----------

    private void openConfig(long id) {
        openConfig(id, null, false, false);
    }

    private void openConfig(long id, String proxyPreset, boolean autoTest, boolean autoSave) {
        Intent i = new Intent(this, AccountConfigActivity.class);
        i.putExtra("account_id", id);
        if (proxyPreset != null) {
            i.putExtra("proxy_preset", proxyPreset);
            i.putExtra("proxy_autotest", autoTest);
            i.putExtra("proxy_save", autoSave);
        }
        startActivityForResult(i, REQ_CONFIG);
    }

    private void addAccount() {
        Account a = store.addDefault();
        editId = -1;
        confirmDelId = -1;
        switchTo(a.id, true);
        toast(getString(R.string.toast_created, a.name));
    }

    private void switchTo(long id, boolean animate) {
        Account a = store.findById(id);
        if (a == null) return;
        store.setCurrent(id);
        ProxyHelper.apply(a.proxy);
        renderAccounts();

        if (currentWebView != null) currentWebView.onPause();

        WebView target = getOrCreateWebView(a);
        if (target == null) return;

        target.setVisibility(View.VISIBLE);
        target.onResume();
        currentWebView = target;
        for (Map.Entry<Long, WebView> en : webViews.snapshot().entrySet()) {
            if (en.getValue() != target) en.getValue().setVisibility(View.GONE);
        }
        // M4: LRU 驱逐超出上限的旧账号页面（Profile 数据保留，仅页面状态丢弃）
        for (long evictedId : webViews.touchAndEvict(a.id, target)) {
            WebView old = webViews.remove(evictedId);
            if (old != null) {
                webLoaded.remove((int) evictedId);
                webContainer.removeView(old);
                old.destroy();
                Log.i("MuseWebview", "lru evict id=" + evictedId);
            }
        }

        if (animate) showSplashFor(a);
        drawerLayout.closeDrawer(drawerContent);
    }

    private void commitRename(long id) {
        Account a = store.findById(id);
        editId = -1;
        if (a == null) {
            renderAccounts();
            return;
        }
        String name = renameInput != null ? renameInput.getText().toString().trim() : "";
        if (name.length() > 16) name = name.substring(0, 16);
        if (name.isEmpty() || name.equals(a.name)) {
            renderAccounts();
            return;
        }
        store.rename(id, name);
        renderAccounts();
        toast(getString(R.string.toast_renamed, name));
    }

    private void deleteAccount(long id) {
        Account a = store.findById(id);
        if (a == null) return;
        try {
            Profile p = profileStore.getProfile(profileName(id));
            if (p != null) {
                p.getCookieManager().removeAllCookies(null);
                p.getCookieManager().flush();
                p.getWebStorage().deleteAllData();
            }
            profileStore.deleteProfile(profileName(id));
        } catch (Exception ignored) {
        }
        // 销毁该账号的 WebView 实例
        WebView wv = webViews.remove(id);
        if (wv != null) {
            if (wv == currentWebView) currentWebView = null;
            webLoaded.remove((int) id);
            webContainer.removeView(wv);
            wv.destroy();
        }
        store.remove(id);
        editId = -1;
        confirmDelId = -1;
        renderAccounts();
        toast(getString(R.string.toast_deleted));
        long cur = store.getCurrentId();
        if (cur >= 0) switchTo(cur, true);
    }

    private void refreshLogged() {
        if (!probeRunning.compareAndSet(false, true)) {
            probeDirty.set(true); // 已有一轮在跑，结束后补一轮
            return;
        }
        probeQueue = new ArrayList<>(store.all());
        probeIndex = 0;
        probeChanged = false;
        probeStep();
    }

    private void probeStep() {
        if (probeQueue == null || probeIndex >= probeQueue.size()) {
            probeQueue = null;
            probeRunning.set(false);
            if (probeDirty.compareAndSet(true, false)) refreshLogged();
            return;
        }
        int end = Math.min(probeIndex + PROBE_CHUNK, probeQueue.size());
        for (int i = probeIndex; i < end; i++) {
            Account a = probeQueue.get(i);
            try {
                Profile p = profileStore.getProfile(profileName(a.id));
                // 不能用 hasCookies()：匿名访问也会落下 Cloudflare/Meta 埋点 cookie，一律误判已登录。
                // 只认 muse.ai 的登录会话 cookie hatch_sess。
                String cookies = p != null
                        ? p.getCookieManager().getCookie("https://muse.ai/") : null;
                boolean logged = cookies != null && cookies.contains(AUTH_COOKIE_MARKER);
                if (logged != a.logged) {
                    a.logged = logged;
                    probeChanged = true;
                }
            } catch (Exception ignored) {
            }
        }
        probeIndex = end;
        if (probeChanged && editId == -1 && confirmDelId == -1) renderAccounts();
        mainHandler.postDelayed(probeStep, 16); // 让出一帧再继续下一块
    }

    private String profileName(long id) {
        return "acct_" + id;
    }

    // ---------- 渲染 ----------

    private void renderAccounts() {
        railAvatars.removeAllViews();
        accountList.removeAllViews();
        String q = query();
        int shown = 0;
        for (Account a : store.all()) {
            if (!matches(a, q)) continue;
            shown++;
            railAvatars.addView(makeRailAvatar(a));
            accountList.addView(makeDrawerRow(a));
        }
        accCount.setText(getString(R.string.accounts_count_fmt, store.all().size()));
        emptyState.setVisibility(store.all().isEmpty() ? View.VISIBLE : View.GONE);
        drawerEmpty.setVisibility(!q.isEmpty() && shown == 0 ? View.VISIBLE : View.GONE);
    }

    private String query() {
        return searchInput != null ? searchInput.getText().toString().trim() : "";
    }

    /** 按 名称 + 标签 过滤（忽略大小写，空 query 全匹配） */
    private boolean matches(Account a, String q) {
        if (q.isEmpty()) return true;
        String key = q.toLowerCase();
        if (a.name.toLowerCase().contains(key)) return true;
        for (String t : a.tags) {
            if (t.toLowerCase().contains(key)) return true;
        }
        return false;
    }

    private void showSplashFor(Account a) {
        splashName.setText(a.name);
        splash.setVisibility(View.VISIBLE);
        splashShownAt = SystemClock.uptimeMillis();
        webContainer.removeCallbacks(hideSplashRunnable);
        // 已加载过的账号不会再次触发 onPageFinished，直接短延时收起
        Boolean loaded = webLoaded.get((int) a.id);
        if (loaded != null && loaded) {
            webContainer.postDelayed(hideSplashRunnable, 450);
        }
        // 兜底：加载异常时也不会卡在过渡层
        webContainer.postDelayed(hideSplashRunnable, SPLASH_TIMEOUT_MS);
    }

    private void hideSplash() {
        webContainer.removeCallbacks(hideSplashRunnable);
        splash.setVisibility(View.GONE);
    }

    private View makeRailAvatar(Account a) {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        wrap.setLayoutParams(lp);
        wrap.setBackgroundResource(a.id == store.getCurrentId()
                ? R.drawable.bg_rail_active : R.drawable.bg_rail_btn);

        TextView tile = makeTile(dp(38), 20, a.avatar);
        wrap.addView(tile, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));

        View dot = new View(this);
        dot.setBackgroundResource(a.logged ? R.drawable.bg_dot_on : R.drawable.bg_dot_off);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(10), dp(10),
                Gravity.BOTTOM | Gravity.END);
        dlp.rightMargin = dp(1);
        dlp.bottomMargin = dp(1);
        wrap.addView(dot, dlp);

        wrap.setOnClickListener(v -> switchTo(a.id, true));
        return wrap;
    }

    private View makeDrawerRow(Account a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(4);
        row.setLayoutParams(rlp);

        // 删除确认态
        if (confirmDelId == a.id) {
            row.setBackgroundResource(R.drawable.bg_row);
            TextView msg = new TextView(this);
            msg.setText(getString(R.string.confirm_delete_fmt, a.name));
            msg.setTextColor(COLOR_TEXT);
            msg.setTextSize(12.5f);
            msg.setLineSpacing(dp(2), 1f);
            row.addView(msg, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(makeTextBtn(getString(R.string.btn_delete), true,
                    v -> deleteAccount(a.id)));
            row.addView(makeTextBtn(getString(R.string.btn_cancel), false,
                    v -> {
                        confirmDelId = -1;
                        renderAccounts();
                    }));
            return row;
        }

        // 重命名编辑态
        if (editId == a.id) {
            row.setBackgroundResource(R.drawable.bg_row);
            row.addView(makeTile(dp(40), 20, a.avatar));

            EditText input = new EditText(this);
            input.setText(a.name);
            input.setTextColor(COLOR_TEXT);
            input.setHintTextColor(0xFF686E78);
            input.setHint(R.string.rename_hint);
            input.setTextSize(13.5f);
            input.setBackgroundResource(R.drawable.bg_rename);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
            input.setPadding(dp(10), dp(7), dp(10), dp(7));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ilp.leftMargin = dp(8);
            input.setLayoutParams(ilp);
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitRename(a.id);
                    return true;
                }
                return false;
            });
            row.addView(input);
            renameInput = input;

            row.addView(makeIconBtn(R.drawable.ic_check, 0xFFFFFFFF, R.drawable.bg_editbtn_ok,
                    getString(R.string.cd_rename), v -> commitRename(a.id)));
            row.addView(makeIconBtn(R.drawable.ic_close, COLOR_ICON, R.drawable.bg_editbtn,
                    getString(R.string.btn_cancel), v -> {
                        editId = -1;
                        renderAccounts();
                    }));
            row.post(() -> {
                input.requestFocus();
                input.selectAll();
            });
            return row;
        }

        // 常规态
        row.setBackgroundResource(a.id == store.getCurrentId()
                ? R.drawable.bg_row_active : R.drawable.bg_row);
        row.addView(makeTile(dp(40), 20, a.avatar));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = dp(11);
        info.setLayoutParams(ilp);

        TextView name = new TextView(this);
        name.setText(a.name);
        name.setTextColor(COLOR_TEXT);
        name.setTextSize(14.5f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);

        String status = getString(a.logged ? R.string.status_logged : R.string.status_not_logged);
        SpannableString ss = new SpannableString("●  " + status);
        ss.setSpan(new ForegroundColorSpan(a.logged ? 0xFF34C759 : 0xFF4A4F57),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView sub = new TextView(this);
        sub.setText(ss);
        sub.setTextColor(COLOR_MUTED);
        sub.setTextSize(11.5f);
        sub.setPadding(0, dp(3), 0, 0);
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(sub);

        row.addView(info);
        row.addView(makeIconBtn(R.drawable.ic_settings, COLOR_ICON, R.drawable.bg_rail_btn,
                getString(R.string.cd_config), v -> openConfig(a.id)));
        row.addView(makeIconBtn(R.drawable.ic_pencil, COLOR_ICON, R.drawable.bg_rail_btn,
                getString(R.string.cd_rename), v -> {
                    editId = a.id;
                    confirmDelId = -1;
                    renderAccounts();
                }));
        row.addView(makeIconBtn(R.drawable.ic_trash, COLOR_ICON, R.drawable.bg_rail_btn,
                getString(R.string.cd_delete), v -> {
                    confirmDelId = a.id;
                    editId = -1;
                    renderAccounts();
                }));

        row.setOnClickListener(v -> switchTo(a.id, true));
        return row;
    }

    private TextView makeTile(int sizePx, float textSp, String text) {
        TextView t = new TextView(this);
        t.setBackgroundResource(R.drawable.bg_mtile);
        t.setText(text == null || text.isEmpty() ? "m" : text);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        t.setTextColor(0xFF2F7CF6);
        t.setTextSize(textSp);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        return t;
    }

    private ImageButton makeIconBtn(int icon, int tint, int bg, String cd,
                                    View.OnClickListener onClick) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(tint));
        b.setBackgroundResource(bg);
        b.setContentDescription(cd);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView makeTextBtn(String text, boolean danger, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(danger ? R.drawable.bg_delbtn_ok : R.drawable.bg_editbtn);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------- 其他 ----------

    private void showWebViewUpgradeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.webview_need_update_title)
                .setMessage(R.string.webview_need_update_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.webview_update_btn, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.google.android.webview")));
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                    "https://play.google.com/store/apps/details?id=com.google.android.webview")));
                        } catch (Exception ignored) {
                        }
                    }
                    finish();
                })
                .setNegativeButton(R.string.btn_exit, (d, w) -> finish())
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** M3: 账号存储损坏/自愈提示（takeLoadNotice 取走即清，不重复弹） */
    private void checkStoreNotice() {
        String n = store.takeLoadNotice();
        if ("recovered".equals(n)) {
            toast(getString(R.string.toast_store_recovered));
        } else if ("corrupt".equals(n)) {
            toast(getString(R.string.toast_store_corrupt));
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}

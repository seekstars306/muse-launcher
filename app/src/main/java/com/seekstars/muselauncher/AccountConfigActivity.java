package com.seekstars.muselauncher;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** 账号配置页：头像 / 名称 / 代理 / 标签，代理测活 */
public class AccountConfigActivity extends AppCompatActivity {

    private static final String TEST_URL = "https://www.gstatic.com/generate_204";
    private static final long TEST_TIMEOUT_MS = 10000;
    /** Clash 默认端口：7890 混合(HTTP+SOCKS) / 7891 纯 HTTP / 7892 纯 SOCKS */
    private static final int[] CLASH_PORTS = {7890, 7891, 7892};
    private static final int PROBE_TIMEOUT_MS = 400;

    private AccountsStore store;
    private Account account;

    private TextView tile;
    private EditText avatarInput;
    private EditText nameInput;
    private EditText proxyInput;
    private EditText tagsInput;
    private TextView detectBtn;
    private TextView testBtn;
    private TextView testStatus;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private WebView testWebView;
    private boolean testing = false;
    private boolean detecting = false;
    /** proxy_save 调试模式：测活通过后自动入库（免触摸配置代理） */
    private boolean pendingSave = false;
    /** 测活时代理 URL 内嵌的账密（供 407 挑战应答） */
    private String[] testCreds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        long id = getIntent().getLongExtra("account_id", -1L);
        store = new AccountsStore(this);
        account = store.findById(id);
        if (account == null) {
            finish();
            return;
        }

        tile = findViewById(R.id.cfgTile);
        avatarInput = findViewById(R.id.cfgAvatar);
        nameInput = findViewById(R.id.cfgName);
        proxyInput = findViewById(R.id.cfgProxy);
        tagsInput = findViewById(R.id.cfgTags);
        detectBtn = findViewById(R.id.cfgProxyDetect);
        testBtn = findViewById(R.id.cfgProxyTest);
        testStatus = findViewById(R.id.cfgProxyStatus);

        findViewById(R.id.cfgBack).setOnClickListener(v -> finish());
        findViewById(R.id.cfgSave).setOnClickListener(v -> save());
        testBtn.setOnClickListener(v -> testProxy());
        detectBtn.setOnClickListener(v -> detectProxy());

        avatarInput.setText(account.avatar);
        nameInput.setText(account.name);
        proxyInput.setText(account.proxy);
        tagsInput.setText(TextUtils.join(", ", account.tags));
        updateTile();
        avatarInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateTile();
            }
        });

        if (!ProxyHelper.supported()) {
            testStatus.setText(R.string.config_proxy_unsupported);
            testBtn.setEnabled(false);
            detectBtn.setEnabled(false);
        }

        // 调试入口：预填代理 + 自动测活/自动保存（真机无法 adb 触摸，用于验证与配置代理）
        String preset = getIntent().getStringExtra("proxy_preset");
        if (preset != null) {
            proxyInput.setText(preset);
            if (getIntent().getBooleanExtra("proxy_save", false)) {
                pendingSave = true;
                proxyInput.post(this::testProxy);
            } else if (getIntent().getBooleanExtra("proxy_autotest", false)) {
                proxyInput.post(this::testProxy);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (testWebView != null) {
            testWebView.destroy();
            testWebView = null;
        }
        handler.removeCallbacksAndMessages(null);
        // 恢复当前账号的代理（测活时临时改过全局代理）
        Account cur = store.findById(store.getCurrentId());
        ProxyHelper.apply(cur != null ? cur.proxy : "");
        super.onDestroy();
    }

    private void updateTile() {
        String av = avatarInput.getText().toString().trim();
        if (av.length() > 2) av = av.substring(0, 2);
        tile.setText(av.isEmpty() ? "m" : av);
    }

    private void save() {
        persist();
        finish();
    }

    /** 读取输入并写入 store（不退出页面，供测活通过后自动入库复用） */
    private void persist() {
        String avatar = avatarInput.getText().toString().trim();
        if (avatar.isEmpty()) avatar = "m";
        if (avatar.length() > 2) avatar = avatar.substring(0, 2);
        account.avatar = avatar;

        String name = nameInput.getText().toString().trim();
        if (!name.isEmpty()) {
            account.name = name.length() > 16 ? name.substring(0, 16) : name;
        }

        account.proxy = proxyInput.getText().toString().trim();

        List<String> tags = new ArrayList<>();
        for (String t : tagsInput.getText().toString().split("[,，]")) {
            String tag = t.trim();
            if (!tag.isEmpty()) tags.add(tag.length() > 8 ? tag.substring(0, 8) : tag);
        }
        account.tags = tags;

        store.save();
        setResult(RESULT_OK);
    }

    // ---------- 代理测活 ----------

    private void testProxy() {
        if (testing) return;
        final String proxy = proxyInput.getText().toString().trim();
        if (proxy.isEmpty()) {
            testStatus.setText(R.string.config_proxy_empty);
            return;
        }
        testing = true;
        testBtn.setEnabled(false);
        testBtn.setText(R.string.config_proxy_testing);
        testStatus.setText("");

        // 临时应用被测代理
        ProxyHelper.apply(proxy);
        testCreds = parseProxyCreds(proxy);
        final long start = SystemClock.uptimeMillis();

        testWebView = new WebView(this);
        testWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                finishTest(true, null, SystemClock.uptimeMillis() - start);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    finishTest(false, String.valueOf(error.getDescription()), 0);
                }
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView v, HttpAuthHandler handler,
                                                  String host, String realm) {
                // 代理 407 挑战：用 URL 内嵌账密应答（ProxyConfig 不支持内嵌凭证时的兜底）
                if (testCreds != null) {
                    handler.proceed(testCreds[0], testCreds[1]);
                } else {
                    handler.cancel();
                }
            }
        });
        timeoutRunnable = () -> finishTest(false, null, 0);
        handler.postDelayed(timeoutRunnable, TEST_TIMEOUT_MS);
        testWebView.loadUrl(TEST_URL);
    }

    /** 从代理地址解析 [user, pass]，无凭证返回 null */
    private static String[] parseProxyCreds(String p) {
        int at = p.indexOf('@');
        if (at < 0) return null;
        String head = p.substring(0, at);
        int si = head.indexOf("://");
        String creds = si >= 0 ? head.substring(si + 3) : head;
        if (creds.isEmpty()) return null;
        int c = creds.indexOf(':');
        if (c < 0) return new String[]{creds, ""};
        return new String[]{creds.substring(0, c), creds.substring(c + 1)};
    }

    // ---------- Clash 端口探测 ----------

    private void detectProxy() {
        if (detecting) return;
        detecting = true;
        detectBtn.setEnabled(false);
        testStatus.setText(R.string.config_proxy_detecting);
        new Thread(() -> {
            int port = probeLocalProxy();
            runOnUiThread(() -> {
                detecting = false;
                detectBtn.setEnabled(true);
                if (port > 0) {
                    proxyInput.setText(proxyUrlForPort(port));
                    testStatus.setText(getString(R.string.config_proxy_detect_found, port));
                } else {
                    testStatus.setText(R.string.config_proxy_detect_none);
                }
            });
        }).start();
    }

    /** 探测本机 Clash 默认端口，返回首个在监听的端口，未找到返回 0 */
    private static int probeLocalProxy() {
        for (int p : CLASH_PORTS) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", p), PROBE_TIMEOUT_MS);
                return p;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String proxyUrlForPort(int port) {
        // 7892 是纯 SOCKS，其余按 HTTP 混合端口处理
        return (port == 7892 ? "socks5://" : "http://") + "127.0.0.1:" + port;
    }

    private void finishTest(boolean ok, String err, long ms) {
        if (!testing) return;
        testing = false;
        handler.removeCallbacks(timeoutRunnable);
        if (testWebView != null) {
            testWebView.destroy();
            testWebView = null;
        }
        testBtn.setEnabled(true);
        testBtn.setText(R.string.config_proxy_test);
        if (ok) {
            testStatus.setText(getString(R.string.config_proxy_ok) + "（" + ms + " ms）");
            if (pendingSave) {
                pendingSave = false;
                persist();
                testStatus.setText(getString(R.string.config_proxy_saved)
                        + " · " + getString(R.string.config_proxy_ok) + "（" + ms + " ms）");
            }
        } else if (err == null) {
            testStatus.setText(R.string.config_proxy_timeout);
        } else {
            testStatus.setText(getString(R.string.config_proxy_fail) + err);
        }
    }
}

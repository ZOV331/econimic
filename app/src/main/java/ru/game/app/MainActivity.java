package ru.game.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Приложение — «оболочка» вокруг игры: открывает интерфейс игры с сервера.
 * Весь дизайн и анимации живут на сервере (папка webapp), поэтому обновляются без новой версии APK.
 */
public class MainActivity extends Activity {
    static final String PREFS = "game";
    private static final int MIC_REQUEST = 7;

    SharedPreferences prefs;
    final Handler ui = new Handler(Looper.getMainLooper());

    private WebView web;
    private View splash;
    private TextView splashText;
    private TextView retry;
    private PermissionRequest pendingMic;
    private boolean loaded = false;
    private boolean failed = false;
    private String current = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Mono.density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        String bad = Guard.check(this);
        if (!bad.isEmpty()) {
            blocked(bad);   // подделанная копия / отладчик / программы взлома — игру не открываем
            return;
        }
        connect();
        checkUpdateAsync();
    }

    // ===================== ЭКРАН =====================
    @SuppressLint("SetJavaScriptEnabled")
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Mono.BG);

        web = new WebView(this);
        web.setBackgroundColor(Mono.BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // защита: страница игры не может читать файлы телефона и чужие данные
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setGeolocationEnabled(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(true);
        }
        WebView.setWebContentsDebuggingEnabled(false);   // нельзя подключиться к странице через chrome://inspect
        s.setUserAgentString(s.getUserAgentString() + " EkonomikaApp/1 RovariApp/" + myVersion());
        s.setCacheMode(WebSettings.LOAD_DEFAULT);          // файлы игры берутся из кэша, пока не изменились
        if (Build.VERSION.SDK_INT >= 23) {
            s.setOffscreenPreRaster(true);                  // плавнее прокрутка и анимации
        }
        if (Build.VERSION.SDK_INT >= 26) {
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (failed) {
                    return;   // страница ошибки — заставку не убираем
                }
                loaded = true;
                splash.animate().alpha(0f).setDuration(250).withEndAction(() -> splash.setVisibility(View.GONE)).start();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) {
                    failed = true;
                    showOffline();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u.toString().startsWith(current) || u.toString().startsWith(serverAddress())) {
                    return false;
                }
                String sc = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if (!sc.equals("https") && !sc.equals("http") && !sc.equals("tg")) {
                    return true;   // intent://, file:// и прочие опасные ссылки не открываем
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));  // t.me и другие ссылки — во внешнем приложении
                } catch (Exception ignored) {
                    // нечем открыть
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                ui.post(() -> handleMic(request));
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // заставка
        LinearLayout sp = new LinearLayout(this);
        sp.setOrientation(LinearLayout.VERTICAL);
        sp.setGravity(Gravity.CENTER);
        sp.setBackgroundColor(Mono.BG);
        sp.setClickable(true);
        final ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);  // пиксельная монета — та же, что иконка приложения
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setElevation(Mono.dp(12));
        sp.addView(logo, new LinearLayout.LayoutParams(Mono.dp(96), Mono.dp(96)));
        pulse(logo, true);
        TextView name = new TextView(this);
        name.setText(Config.APP_NAME);
        name.setTypeface(Mono.DISPLAY);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        name.setLetterSpacing(0.06f);
        name.setTextColor(Color.WHITE);
        name.setPadding(0, Mono.dp(22), 0, Mono.dp(6));
        sp.addView(name);
        splashText = new TextView(this);
        splashText.setText("Подключение к серверу…");
        splashText.setTextColor(Mono.MUTED);
        splashText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        splashText.setGravity(Gravity.CENTER);
        splashText.setPadding(Mono.dp(30), 0, Mono.dp(30), 0);
        sp.addView(splashText);
        retry = Mono.makeButton(this, "Повторить", Mono.BLUE, 16, 16);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> connect());
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Mono.dp(200), Mono.dp(52));
        rl.topMargin = Mono.dp(22);
        sp.addView(retry, rl);
        splash = sp;
        root.addView(sp, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        getWindow().setStatusBarColor(Mono.BG);
        getWindow().setNavigationBarColor(Mono.SURFACE);
    }

    /** Логотип на заставке мягко «дышит». */
    private void pulse(final View v, final boolean up) {
        v.animate().scaleX(up ? 1.08f : 1f).scaleY(up ? 1.08f : 1f).setDuration(700).withEndAction(() -> {
            if (splash == null || splash.getVisibility() == View.VISIBLE) {
                pulse(v, !up);
            }
        }).start();
    }

    // ===================== ПОДКЛЮЧЕНИЕ =====================
    String serverAddress() {
        String remote = prefs.getString("server_remote", "");
        if (!remote.isEmpty()) {
            return remote;
        }
        return Api.normalize(Config.DEFAULT_SERVER);
    }

    /** Сразу открывает игру по сохранённому адресу, а свежий адрес из server.txt проверяет фоном. */
    private void connect() {
        loaded = false;
        splash.setVisibility(View.VISIBLE);
        splash.setAlpha(1f);
        splashText.setText("Подключение к серверу…");
        retry.setVisibility(View.GONE);
        final String saved = serverAddress();
        open(saved);
        new Thread(() -> {
            if (Config.REMOTE_CONFIG.isEmpty()) {
                return;
            }
            String text = Api.fetchText(Config.REMOTE_CONFIG + "?t=" + System.currentTimeMillis());
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            final String url = Api.normalize(text.trim().split("\\s+")[0]);
            if (!url.startsWith("http")) {
                return;
            }
            prefs.edit().putString("server_remote", url).apply();
            if (!url.equals(current)) {
                ui.post(() -> open(url));   // адрес туннеля сменился — переходим на новый
            }
        }).start();
        ui.postDelayed(() -> {
            if (!loaded) {
                showOffline();
            }
        }, 25000);
    }

    private void open(String base) {
        current = base;
        failed = false;
        web.loadUrl(base + "/");
    }

    private void showOffline() {
        loaded = false;
        splash.setVisibility(View.VISIBLE);
        splash.setAlpha(1f);
        splashText.setText("Нет связи с сервером игры.\nПроверьте интернет или попробуйте чуть позже.");
        retry.setVisibility(View.VISIBLE);
    }

    /** Экран «запуск запрещён». */
    private void blocked(String why) {
        web.setVisibility(View.GONE);
        web.destroy();
        web = null;
        splash.setVisibility(View.VISIBLE);
        splashText.setText(why);
        retry.setText("Скачать ROVARI");
        retry.setVisibility(View.VISIBLE);
        retry.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.UPDATE_DOWNLOAD)));
            } catch (Exception ignored) {
                // нечем открыть
            }
        });
    }

    // ===================== МИКРОФОН ДЛЯ ГОЛОСОВЫХ =====================
    private void handleMic(PermissionRequest request) {
        boolean wantsAudio = false;
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                wantsAudio = true;
            }
        }
        if (!wantsAudio) {
            request.deny();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = request;
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
            return;
        }
        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == MIC_REQUEST && pendingMic != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingMic.deny();
            }
            pendingMic = null;
        }
    }

    // ===================== НАЗАД =====================
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && loaded) {
            web.evaluateJavascript("(window.appBack && window.appBack()) ? 'yes' : 'no'", v -> {
                if (v == null || !v.contains("yes")) {
                    finish();
                }
            });
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.onPause();
        }
    }

    // ===== Совместимость: старые нативные экраны (GameScreen, ChatPanel) больше не используются =====
    void setScreen(View v) {
        setContentView(v);
    }

    void logout() {
        connect();
    }

    void refreshServerAsync() {
        // адрес обновляется при подключении
    }

        // ===================== ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ =====================
    @SuppressWarnings("deprecation")
    int myVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    /** Спрашивает GitHub, нет ли версии новее; если есть — предлагает обновиться. */
    void checkUpdateAsync() {
        if (Config.UPDATE_CHECK.isEmpty()) {
            return;
        }
        final int mine = myVersion();
        new Thread(() -> {
            String json = Api.fetchText(Config.UPDATE_CHECK);
            if (json == null) {
                return;
            }
            int latest;
            try {
                String tag = new JSONObject(json).optString("tag_name", "");
                latest = Integer.parseInt(tag.replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                return;
            }
            if (latest <= mine) {
                return;
            }
            final int fl = latest;
            ui.post(() -> {
                if (isFinishing()) {
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Доступно обновление")
                        .setMessage("Вышла новая версия приложения (1." + fl + ").\n\n"
                                + "Нажмите «Обновить» — загрузится файл. Откройте его, чтобы установить поверх.")
                        .setPositiveButton("Обновить", (d, w) -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.UPDATE_DOWNLOAD)));
                            } catch (Exception ignored) {
                                // нечем открыть
                            }
                        })
                        .setNegativeButton("Позже", null)
                        .show();
            });
        }).start();
    }
}

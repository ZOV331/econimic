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
import android.widget.EditText;
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
    private TextView manual;
    private String lastError = "";
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
        ui.postDelayed(watchdog, 30000);
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
                attempts = 0;
                saveToken();
                splash.animate().alpha(0f).setDuration(250).withEndAction(() -> splash.setVisibility(View.GONE)).start();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) {
                    failed = true;
                    lastError = String.valueOf(error.getDescription());
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
        name.setGravity(Gravity.CENTER);
        sp.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        // свой адрес сервера — если автоматический не открывается (например, у провайдера заблокирован GitHub)
        manual = new TextView(this);
        manual.setText("Ввести адрес сервера");
        manual.setTextColor(Mono.MUTED);
        manual.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        manual.setGravity(Gravity.CENTER);
        manual.setPadding(Mono.dp(20), Mono.dp(18), Mono.dp(20), Mono.dp(18));
        manual.setVisibility(View.GONE);
        manual.setOnClickListener(v -> askAddress());
        sp.addView(manual, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        String own = prefs.getString("server_manual", "");
        if (!own.isEmpty()) {
            return own;
        }
        String remote = prefs.getString("server_remote", "");
        if (!remote.isEmpty()) {
            return remote;
        }
        return Api.normalize(Config.DEFAULT_SERVER);
    }

    /** Сразу открывает игру по сохранённому адресу, а свежий адрес из server.txt проверяет фоном. */
    private final Runnable loadTimeout = () -> {
        if (!loaded) {
            if (lastError.isEmpty()) {
                lastError = "сервер не ответил за 25 секунд";
            }
            showOffline();
        }
    };

    private final Runnable autoRetry = () -> {
        if (!loaded && !isFinishing()) {
            connect();
        }
    };

    private void connect() {
        loaded = false;
        lastError = "";
        splash.setVisibility(View.VISIBLE);
        splash.setAlpha(1f);
        splashText.setText("Подключение к серверу…");
        retry.setVisibility(View.GONE);
        manual.setVisibility(View.GONE);
        final String saved = serverAddress();
        open(saved);
        new Thread(() -> {
            final String url = Api.fetchServerUrl();
            if (url == null) {
                return;
            }
            prefs.edit().putString("server_remote", url).apply();
            if (!prefs.getString("server_manual", "").isEmpty()) {
                return;   // игрок сам указал адрес — его и используем
            }
            if (!url.equals(current)) {
                ui.post(() -> open(url));   // адрес туннеля сменился — переходим на новый
            }
        }).start();
        ui.removeCallbacks(loadTimeout);
        ui.postDelayed(loadTimeout, 25000);
    }

    private void open(String base) {
        current = base;
        failed = false;
        // токен входа переносим в новый адрес сервера: туннель сменил адрес — заходить заново не нужно
        String tok = prefs.getString("tok", "");
        web.loadUrl(base + "/" + (tok.isEmpty() ? "" : "#t=" + Uri.encode(tok)));
    }

    private int attempts = 0;

    /** Сохраняет токен входа из страницы (localStorage), чтобы не терять вход при смене адреса сервера. */
    private void saveToken() {
        if (web == null || !loaded) {
            return;
        }
        web.evaluateJavascript("(function(){try{return localStorage.getItem('token')||''}catch(e){return ''}})()", v -> {
            if (v == null) {
                return;
            }
            String t = v.replace("\"", "");
            if (t.equals("null")) {
                t = "";
            }
            prefs.edit().putString("tok", t).apply();
        });
    }

    /** Сторож связи: каждые 30 секунд сохраняет вход и проверяет, не сменился ли адрес сервера. */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            saveToken();
            if (!Config.REMOTE_CONFIG.isEmpty()) {
                new Thread(() -> {
                    final String url = Api.fetchServerUrl();
                    if (url == null || !prefs.getString("server_manual", "").isEmpty()) {
                        return;
                    }
                    if (!url.equals(current)) {
                        prefs.edit().putString("server_remote", url).apply();
                        ui.post(() -> {
                            saveToken();
                            ui.postDelayed(() -> open(url), 400);
                        });
                    }
                }).start();
            }
            ui.postDelayed(this, 30000);
        }
    };

    private void showOffline() {
        loaded = false;
        // переподключаемся сами: 3, 6, 9 … секунд (не реже раза в 20 секунд), каждый раз со свежим адресом
        attempts++;
        ui.removeCallbacks(autoRetry);
        ui.postDelayed(autoRetry, Math.min(20000, attempts * 3000));
        splash.setVisibility(View.VISIBLE);
        splash.setAlpha(1f);
        String host = Uri.parse(current).getHost();
        splashText.setText("Нет связи с сервером игры.\nПереподключаюсь автоматически…"
                + "\n\nАдрес: " + (host == null ? current : host)
                + (lastError.isEmpty() ? "" : "\nОшибка: " + lastError)
                + "\n\nЕсли включён VPN — попробуйте выключить его (или наоборот включить).");
        retry.setVisibility(View.VISIBLE);
        manual.setVisibility(View.VISIBLE);
    }

    /** Окно «свой адрес сервера». Пустое поле — снова брать адрес автоматически. */
    private void askAddress() {
        final EditText in = new EditText(this);
        in.setHint("https://….trycloudflare.com");
        in.setSingleLine(true);
        in.setText(prefs.getString("server_manual", ""));
        int p = Mono.dp(20);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(p, Mono.dp(8), p, 0);
        box.addView(in);
        new AlertDialog.Builder(this)
                .setTitle("Адрес сервера игры")
                .setMessage("Спросите адрес у администратора игры. Оставьте поле пустым, чтобы приложение находило адрес само.")
                .setView(box)
                .setPositiveButton("Подключиться", (d, w) -> {
                    String v = Api.normalize(in.getText().toString());
                    prefs.edit().putString("server_manual", v.startsWith("http") ? v : "").apply();
                    attempts = 0;
                    connect();
                })
                .setNegativeButton("Отмена", null)
                .show();
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
    /** Микрофон (голосовые) и камера (видеокружки): спрашиваем разрешение Android и выдаём странице только их. */
    private void handleMic(PermissionRequest request) {
        java.util.List<String> want = new java.util.ArrayList<>();
        java.util.List<String> perms = new java.util.ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                want.add(r);
                perms.add(android.Manifest.permission.RECORD_AUDIO);
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                want.add(r);
                perms.add(android.Manifest.permission.CAMERA);
            }
        }
        if (want.isEmpty()) {
            request.deny();
            return;
        }
        pendingRes = want.toArray(new String[0]);
        if (Build.VERSION.SDK_INT >= 23) {
            java.util.List<String> need = new java.util.ArrayList<>();
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    need.add(p);
                }
            }
            if (!need.isEmpty()) {
                pendingMic = request;
                requestPermissions(need.toArray(new String[0]), MIC_REQUEST);
                return;
            }
        }
        request.grant(pendingRes);
    }

    private String[] pendingRes = new String[0];

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == MIC_REQUEST && pendingMic != null) {
            boolean all = results.length > 0;
            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    all = false;
                }
            }
            if (all) {
                pendingMic.grant(pendingRes);
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

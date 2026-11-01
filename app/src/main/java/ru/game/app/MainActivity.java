package ru.game.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/** Экран входа/регистрации и переключение на игру. */
public class MainActivity extends Activity {
    static final String PREFS = "game";

    SharedPreferences prefs;
    final Handler ui = new Handler(Looper.getMainLooper());
    GameScreen game;

    private boolean registerMode = false;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Mono.density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        refreshServerAsync();
        checkUpdateAsync();
        if (prefs.getString("token", null) != null) {
            openGame(null);
        } else {
            showLogin();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) {
            game.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (game != null) {
            game.stop();
        }
    }

    void setScreen(View v) {
        v.setBackgroundColor(Mono.BG);
        setContentView(v);
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
                                + "Нажмите «Обновить» — загрузится файл. Откройте его, чтобы установить поверх. "
                                + "Аккаунт и прогресс сохранятся.")
                        .setPositiveButton("Обновить", (d, w) -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.UPDATE_DOWNLOAD)));
                            } catch (Exception e) {
                                android.widget.Toast.makeText(this, "Не удалось открыть загрузку",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Позже", null)
                        .show();
            });
        }).start();
    }

    // ===================== ИГРА =====================
    /** Из «ref_123», ссылки t.me/...?start=ref_123 или просто «123» достаёт число. */
    static String refCode(String s) {
        String t = s == null ? "" : s.trim();
        int i = t.lastIndexOf("ref_");
        if (i >= 0) {
            t = t.substring(i + 4);
        }
        StringBuilder d = new StringBuilder();
        for (int k = 0; k < t.length() && Character.isDigit(t.charAt(k)); k++) {
            d.append(t.charAt(k));
        }
        return d.toString();
    }

    String serverAddress() {
        String remote = prefs.getString("server_remote", "");
        if (!remote.isEmpty()) {
            return remote;
        }
        if (!Config.DEFAULT_SERVER.isEmpty()) {
            return Api.normalize(Config.DEFAULT_SERVER);
        }
        return prefs.getString("server", "");
    }

    private long lastRefresh = 0;

    /** Читает актуальный адрес сервера из server.txt; если он сменился — переподключается. */
    void refreshServerAsync() {
        if (Config.REMOTE_CONFIG.isEmpty() || System.currentTimeMillis() - lastRefresh < 30000) {
            return;
        }
        lastRefresh = System.currentTimeMillis();
        new Thread(() -> {
            String text = Api.fetchText(Config.REMOTE_CONFIG + "?t=" + System.currentTimeMillis());
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            final String url = Api.normalize(text.trim().split("\\s+")[0]);
            if (!url.startsWith("http")) {
                return;
            }
            ui.post(() -> {
                if (url.equals(serverAddress())) {
                    return;
                }
                prefs.edit().putString("server_remote", url).apply();
                if (game != null) {
                    game.stop();
                    openGame(null);
                }
            });
        }).start();
    }

    void openGame(String firstCommand) {
        game = new GameScreen(this, serverAddress(), prefs.getString("token", ""));
        game.rebuild();
        game.start();
        if (firstCommand != null) {
            game.send(firstCommand);
        }
    }

    void logout() {
        if (game != null) {
            game.stop();
            final String server = game.server;
            final String token = game.token;
            new Thread(() -> {
                try {
                    Api.call(server, "POST", "/api/logout", new JSONObject(), token, 8000);
                } catch (Api.ApiError ignored) {
                    // токен всё равно забываем
                }
            }).start();
            game = null;
        }
        prefs.edit().remove("token").remove("menu").apply();
        showLogin();
    }

    // ===================== ВХОД =====================
    void showLogin() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Mono.dp(26), Mono.dp(40), Mono.dp(26), Mono.dp(36));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // логотип: синий квадрат с буквой
        TextView logo = new TextView(this);
        logo.setText("Э");
        logo.setTypeface(Mono.DISPLAY);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        logo.setTextColor(Mono.TEXT);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Mono.blueGradient(18));
        logo.setElevation(Mono.dp(10));
        col.addView(logo, new LinearLayout.LayoutParams(Mono.dp(64), Mono.dp(64)));

        TextView title = new TextView(this);
        title.setText(Config.APP_NAME);
        title.setTypeface(Mono.DISPLAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        title.setLetterSpacing(0.04f);
        title.setTextColor(Mono.TEXT);
        LinearLayout.LayoutParams tLp = wrapLp();
        tLp.topMargin = Mono.dp(22);
        col.addView(title, tLp);

        TextView sub = new TextView(this);
        sub.setText("Работа, бизнес, биржа, банды — в одном приложении.");
        sub.setTextColor(Mono.MUTED);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sLp = wrapLp();
        sLp.topMargin = Mono.dp(4);
        sLp.bottomMargin = Mono.dp(28);
        col.addView(sub, sLp);

        // переключатель Вход / Регистрация
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackground(Mono.rounded(Mono.SURFACE, 16, Mono.LINE, 1));
        tabs.setPadding(Mono.dp(4), Mono.dp(4), Mono.dp(4), Mono.dp(4));
        TextView tabIn = tab("Вход", !registerMode);
        TextView tabReg = tab("Регистрация", registerMode);
        tabIn.setOnClickListener(v -> {
            registerMode = false;
            showLogin();
        });
        tabReg.setOnClickListener(v -> {
            registerMode = true;
            showLogin();
        });
        tabs.addView(tabIn, new LinearLayout.LayoutParams(0, Mono.dp(42), 1));
        tabs.addView(tabReg, new LinearLayout.LayoutParams(0, Mono.dp(42), 1));
        col.addView(tabs);

        final boolean fixedServer = !Config.DEFAULT_SERVER.isEmpty();
        final EditText server = fixedServer ? null : field(col, "Адрес сервера", "http://192.168.1.5:8901",
                prefs.getString("server", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText login = field(col, "Логин", "латиница, цифры, _", prefs.getString("login", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final EditText pass = field(col, "Пароль", "минимум 4 символа", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText name = registerMode
                ? field(col, "Имя в игре", "как вас увидят другие", "", InputType.TYPE_CLASS_TEXT)
                : null;
        final EditText ref = registerMode
                ? field(col, "Код друга (необязательно)", "ID пригласившего или его ссылка", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                : null;

        final TextView error = new TextView(this);
        error.setTextColor(0xFFFF6B6B);
        error.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams eLp = wrapLp();
        eLp.topMargin = Mono.dp(14);
        col.addView(error, eLp);

        final String goText = registerMode ? "Создать аккаунт" : "Войти";
        final TextView go = Mono.makeButton(this, goText, Mono.BLUE, 17, 16);
        go.setElevation(Mono.dp(6));
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(56));
        gLp.topMargin = Mono.dp(14);
        col.addView(go, gLp);

        TextView note = new TextView(this);
        note.setText(fixedServer
                ? (registerMode ? "Уже есть аккаунт? Нажмите «Вход»." : "Нет аккаунта? Нажмите «Регистрация» — это бесплатно.")
                : "Адрес сервера даёт владелец игры.");
        note.setTextColor(Mono.MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nLp.topMargin = Mono.dp(18);
        col.addView(note, nLp);

        Runnable submit = () -> {
            if (busy) {
                return;
            }
            final String base = server == null ? serverAddress() : Api.normalize(server.getText().toString());
            final String l = login.getText().toString().trim();
            final String p = pass.getText().toString();
            final String n = name == null ? "" : name.getText().toString().trim();
            final String refCode = ref == null ? "" : refCode(ref.getText().toString());
            if (base.isEmpty() || l.isEmpty() || p.isEmpty()) {
                error.setText(server == null ? "Введите логин и пароль." : "Заполните адрес сервера, логин и пароль.");
                return;
            }
            busy = true;
            error.setText("");
            go.setText("Подключение…");
            final boolean reg = registerMode;
            new Thread(() -> {
                String errText = null;
                JSONObject res = null;
                try {
                    JSONObject b = new JSONObject();
                    b.put("login", l);
                    b.put("password", p);
                    if (reg) {
                        b.put("name", n);
                    }
                    res = Api.call(base, "POST", reg ? "/api/register" : "/api/login", b, null, 15000);
                } catch (Api.ApiError e) {
                    errText = e.getMessage();
                } catch (JSONException e) {
                    errText = "Ошибка данных";
                }
                final String fe = errText;
                final JSONObject fr = res;
                ui.post(() -> {
                    busy = false;
                    if (fe != null || fr == null) {
                        error.setText(fe == null ? "Ошибка" : fe);
                        refreshServerAsync();
                        go.setText(goText);
                        return;
                    }
                    prefs.edit()
                            .putString("server", base)
                            .putString("login", l)
                            .putString("token", fr.optString("token"))
                            .apply();
                    openGame(reg ? (refCode.isEmpty() ? "/start" : "/start ref_" + refCode) : null);
                });
            }).start();
        };
        go.setOnClickListener(v -> submit.run());
        EditText last = ref != null ? ref : pass;
        last.setImeOptions(EditorInfo.IME_ACTION_GO);
        last.setOnEditorActionListener((v, id, ev) -> {
            submit.run();
            return true;
        });

        game = null;
        setScreen(sv);
    }

    private TextView tab(String text, boolean active) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Mono.MEDIUM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(active ? Mono.TEXT : Mono.MUTED);
        t.setClickable(true);
        if (active) {
            t.setBackground(Mono.blueGradient(12));
        }
        return t;
    }

    private LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private EditText field(LinearLayout col, String label, String hint, String value, int type) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Mono.MUTED);
        l.setTypeface(Mono.MEDIUM);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams lLp = wrapLp();
        lLp.topMargin = Mono.dp(16);
        lLp.bottomMargin = Mono.dp(7);
        lLp.leftMargin = Mono.dp(4);
        col.addView(l, lLp);

        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Mono.TEXT);
        e.setHintTextColor(0xFF555D70);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        final GradientDrawable normal = Mono.rounded(Mono.SURFACE2, 14, Mono.LINE, 1);
        final GradientDrawable focused = Mono.rounded(Mono.SURFACE2, 14, Mono.BLUE_C, 1.5f);
        e.setBackground(normal);
        e.setOnFocusChangeListener((v, has) -> e.setBackground(has ? focused : normal));
        e.setPadding(Mono.dp(16), 0, Mono.dp(16), 0);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        GameScreen.cursor(e);
        col.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(52)));
        return e;
    }
}

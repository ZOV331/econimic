package ru.game.app;

import android.app.Activity;
import android.content.SharedPreferences;
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
        Mono.dark = prefs.getBoolean("dark", false);
        if (prefs.getString("token", null) != null) {
            openGame(false);
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
        v.setBackgroundColor(Mono.bg());
        Mono.grayscale(v);
        setContentView(v);
    }

    void toggleTheme() {
        Mono.dark = !Mono.dark;
        prefs.edit().putBoolean("dark", Mono.dark).apply();
        if (game != null) {
            game.rebuild();
        } else {
            showLogin();
        }
    }

    // ===================== ИГРА =====================
    void openGame(boolean sendStart) {
        String server = prefs.getString("server", "");
        String token = prefs.getString("token", "");
        game = new GameScreen(this, server, token);
        game.rebuild();
        game.start();
        if (sendStart) {
            game.send("/start");
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
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Mono.dp(28), Mono.dp(36), Mono.dp(28), Mono.dp(36));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END);
        TextView theme = Mono.makeButton(this, "◐", false, 18);
        theme.setOnClickListener(v -> toggleTheme());
        top.addView(theme, new LinearLayout.LayoutParams(Mono.dp(44), Mono.dp(44)));
        col.addView(top);

        View bar = new View(this);
        bar.setBackgroundColor(Mono.fg());
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(Mono.dp(56), Mono.dp(8));
        barLp.topMargin = Mono.dp(24);
        col.addView(bar, barLp);

        TextView title = new TextView(this);
        title.setText(Config.APP_NAME);
        title.setTypeface(Mono.DISPLAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setLetterSpacing(0.06f);
        title.setTextColor(Mono.fg());
        LinearLayout.LayoutParams tLp = wrapLp();
        tLp.topMargin = Mono.dp(14);
        col.addView(title, tLp);

        TextView sub = new TextView(this);
        sub.setText("Работа, бизнес, биржа, банды — в одном приложении.");
        sub.setTextColor(Mono.muted());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams sLp = wrapLp();
        sLp.bottomMargin = Mono.dp(28);
        col.addView(sub, sLp);

        LinearLayout tabs = new LinearLayout(this);
        TextView tabIn = Mono.makeButton(this, "ВХОД", !registerMode, 14);
        TextView tabReg = Mono.makeButton(this, "РЕГИСТРАЦИЯ", registerMode, 14);
        tabIn.setLetterSpacing(0.1f);
        tabReg.setLetterSpacing(0.1f);
        tabIn.setOnClickListener(v -> {
            registerMode = false;
            showLogin();
        });
        tabReg.setOnClickListener(v -> {
            registerMode = true;
            showLogin();
        });
        tabs.addView(tabIn, new LinearLayout.LayoutParams(0, Mono.dp(46), 1));
        tabs.addView(tabReg, new LinearLayout.LayoutParams(0, Mono.dp(46), 1));
        col.addView(tabs);

        String savedServer = prefs.getString("server", "");
        if (savedServer.isEmpty()) {
            savedServer = Config.DEFAULT_SERVER;
        }
        final EditText server = field(col, "АДРЕС СЕРВЕРА", "http://192.168.1.5:8901", savedServer,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText login = field(col, "ЛОГИН", "латиница, цифры, _", prefs.getString("login", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final EditText pass = field(col, "ПАРОЛЬ", "", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText name = registerMode
                ? field(col, "ИМЯ В ИГРЕ", "как вас увидят другие", "", InputType.TYPE_CLASS_TEXT)
                : null;

        final TextView error = new TextView(this);
        error.setTextColor(Mono.fg());
        error.setTypeface(Mono.MEDIUM);
        error.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams eLp = wrapLp();
        eLp.topMargin = Mono.dp(14);
        col.addView(error, eLp);

        final TextView go = Mono.makeButton(this, registerMode ? "СОЗДАТЬ АККАУНТ" : "ВОЙТИ", true, 16);
        go.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(54));
        gLp.topMargin = Mono.dp(14);
        col.addView(go, gLp);

        TextView note = new TextView(this);
        note.setText("Адрес сервера даёт владелец игры. Если сервер у вас дома, телефон должен быть в той же Wi-Fi сети.");
        note.setTextColor(Mono.muted());
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams nLp = wrapLp();
        nLp.topMargin = Mono.dp(18);
        col.addView(note, nLp);

        Runnable submit = () -> {
            if (busy) {
                return;
            }
            final String base = Api.normalize(server.getText().toString());
            final String l = login.getText().toString().trim();
            final String p = pass.getText().toString();
            final String n = name == null ? "" : name.getText().toString().trim();
            if (base.isEmpty() || l.isEmpty() || p.isEmpty()) {
                error.setText("Заполните адрес сервера, логин и пароль.");
                return;
            }
            busy = true;
            error.setText("");
            go.setText("…");
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
                        go.setText(reg ? "СОЗДАТЬ АККАУНТ" : "ВОЙТИ");
                        return;
                    }
                    prefs.edit()
                            .putString("server", base)
                            .putString("login", l)
                            .putString("token", fr.optString("token"))
                            .apply();
                    openGame(reg);
                });
            }).start();
        };
        go.setOnClickListener(v -> submit.run());
        (name != null ? name : pass).setImeOptions(EditorInfo.IME_ACTION_GO);
        (name != null ? name : pass).setOnEditorActionListener((v, id, ev) -> {
            submit.run();
            return true;
        });

        game = null;
        setScreen(sv);
    }

    private LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private EditText field(LinearLayout col, String label, String hint, String value, int type) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Mono.fg());
        l.setTypeface(Mono.MEDIUM);
        l.setLetterSpacing(0.12f);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams lLp = wrapLp();
        lLp.topMargin = Mono.dp(18);
        lLp.bottomMargin = Mono.dp(6);
        col.addView(l, lLp);

        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Mono.fg());
        e.setHintTextColor(Mono.muted());
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setBackground(Mono.box(Mono.bg(), Mono.fg(), 1.5f));
        e.setPadding(Mono.dp(14), 0, Mono.dp(14), 0);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        GameScreen.cursor(e);
        col.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(50)));
        return e;
    }
}

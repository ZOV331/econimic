package ru.game.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Игровой экран: лента сообщений бота, кнопки, нижнее меню и поле ввода. */
public class GameScreen {
    private static final int MAX_VIEWS = 80;

    final MainActivity act;
    final String server;
    final String token;

    private final TreeMap<Integer, JSONObject> msgs = new TreeMap<>();
    private final Map<Integer, View> views = new HashMap<>();
    private JSONArray menu;

    private volatile boolean running = false;
    private volatile int gen = 0;
    private volatile String epoch = null;
    private volatile int seq = 0;
    private final ExecutorService actions = Executors.newSingleThreadExecutor();

    private LinearLayout list;
    private ScrollView scroll;
    private TextView status;
    private TextView emptyHint;
    private LinearLayout chips;
    private HorizontalScrollView chipsScroll;
    private LinearLayout grid;
    private EditText input;
    private boolean gridOpen = false;

    GameScreen(MainActivity act, String server, String token) {
        this.act = act;
        this.server = server;
        this.token = token;
        String saved = act.prefs.getString("menu", null);
        if (saved != null) {
            try {
                menu = new JSONArray(saved);
            } catch (JSONException ignored) {
                menu = null;
            }
        }
    }

    // ===================== ОФОРМЛЕНИЕ =====================
    void rebuild() {
        Context c = act;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);

        // --- шапка ---
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xFF000000);
        top.setPadding(Mono.dp(16), 0, Mono.dp(6), 0);

        TextView title = new TextView(c);
        title.setText(Config.APP_NAME);
        title.setTypeface(Mono.DISPLAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setLetterSpacing(0.08f);
        title.setTextColor(0xFFFFFFFF);
        top.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(c);
        status.setTextColor(0xFFFFFFFF);
        status.setAlpha(0.6f);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(Mono.dp(10), 0, 0, 0);
        top.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        top.addView(topIcon("◐", v -> act.toggleTheme()));
        TextView exit = topIcon("ВЫЙТИ", v -> new AlertDialog.Builder(act)
                .setMessage("Выйти из аккаунта?")
                .setPositiveButton("Выйти", (d, w) -> act.logout())
                .setNegativeButton("Отмена", null)
                .show());
        exit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        exit.setLetterSpacing(0.12f);
        exit.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mono.dp(46)));
        exit.setPadding(Mono.dp(10), 0, Mono.dp(10), 0);
        top.addView(exit);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(54)));
        if (Mono.dark) {
            root.addView(line(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(1)));
        }

        // --- лента ---
        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Mono.dp(12), Mono.dp(12), Mono.dp(12), Mono.dp(6));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        emptyHint = new TextView(c);
        emptyHint.setText("Откройте раздел в меню внизу ↓");
        emptyHint.setTextColor(Mono.muted());
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        emptyHint.setPadding(0, Mono.dp(60), 0, Mono.dp(60));

        // --- меню ---
        root.addView(line(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(1.5f)));
        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Mono.dp(8), Mono.dp(8), Mono.dp(8), 0);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        chipsScroll = new HorizontalScrollView(c);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Mono.dp(8), Mono.dp(8), Mono.dp(8), 0);
        chipsScroll.addView(chips);
        root.addView(chipsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- ввод ---
        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(Mono.dp(8), Mono.dp(8), Mono.dp(8), Mono.dp(8));

        TextView menuBtn = Mono.makeButton(c, "☰", false, 20);
        menuBtn.setOnClickListener(v -> {
            gridOpen = !gridOpen;
            if (gridOpen) {
                hideKeyboard();
            }
            renderMenu();
        });
        bottom.addView(menuBtn, new LinearLayout.LayoutParams(Mono.dp(48), Mono.dp(48)));

        input = new EditText(c);
        input.setSingleLine(true);
        input.setHint("Сообщение или ответ…");
        input.setTextColor(Mono.fg());
        input.setHintTextColor(Mono.muted());
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setBackground(Mono.box(Mono.bg(), Mono.fg(), 1.5f));
        input.setPadding(Mono.dp(12), 0, Mono.dp(12), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        cursor(input);
        input.setOnEditorActionListener((v, id, ev) -> {
            sendInput();
            return true;
        });
        input.setOnFocusChangeListener((v, has) -> {
            if (has && gridOpen) {
                gridOpen = false;
                renderMenu();
            }
        });
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(0, Mono.dp(48), 1);
        inLp.leftMargin = Mono.dp(8);
        inLp.rightMargin = Mono.dp(8);
        bottom.addView(input, inLp);

        TextView sendBtn = Mono.makeButton(c, "→", true, 20);
        sendBtn.setOnClickListener(v -> sendInput());
        bottom.addView(sendBtn, new LinearLayout.LayoutParams(Mono.dp(48), Mono.dp(48)));
        root.addView(bottom);

        act.setScreen(root);

        views.clear();
        for (JSONObject m : msgs.values()) {
            View v = buildMessage(m);
            views.put(m.optInt("id"), v);
            list.addView(v);
        }
        updateEmpty();
        renderMenu();
        scrollToBottom();
    }

    private View line() {
        View v = new View(act);
        v.setBackgroundColor(Mono.fg());
        return v;
    }

    private TextView topIcon(String text, View.OnClickListener l) {
        TextView b = new TextView(act);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(Mono.dp(46), Mono.dp(46)));
        return b;
    }

    static void cursor(EditText e) {
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(Mono.fg());
            d.setSize(Mono.dp(2), Mono.dp(20));
            e.setTextCursorDrawable(d);
        }
    }

    // ===================== СООБЩЕНИЯ =====================
    private View buildMessage(final JSONObject m) {
        Context c = act;
        final int id = m.optInt("id");
        if (m.optBoolean("out")) {
            TextView t = new TextView(c);
            t.setText(Mono.html(m.optString("text")));
            t.setTextColor(Mono.bg());
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setBackground(Mono.box(Mono.fg(), Mono.fg(), 0));
            t.setPadding(Mono.dp(14), Mono.dp(9), Mono.dp(14), Mono.dp(9));
            t.setMaxWidth((int) (act.getResources().getDisplayMetrics().widthPixels * 0.78f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.END;
            lp.bottomMargin = Mono.dp(10);
            lp.leftMargin = Mono.dp(48);
            t.setLayoutParams(lp);
            return t;
        }

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Mono.box(Mono.bg(), Mono.fg(), 1.5f));
        int pad = Mono.dp(1.5f);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = Mono.dp(12);
        card.setLayoutParams(cardLp);

        String photo = m.isNull("photo") ? "" : m.optString("photo", "");
        if (!photo.isEmpty()) {
            ImageView iv = new ImageView(c);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMinimumHeight(Mono.dp(200));
            iv.setBackgroundColor(Mono.soft());
            card.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            String url = photo.startsWith("http") ? photo : server + photo;
            Images.load(url, iv, () -> {
                if (!msgs.isEmpty() && msgs.lastKey() == id) {
                    scrollToBottom();
                }
            });
        }

        JSONObject dice = m.optJSONObject("dice");
        if (dice != null) {
            TextView d = new TextView(c);
            d.setText(dice.optString("emoji", "🎲"));
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
            d.setGravity(Gravity.CENTER);
            d.setPadding(0, Mono.dp(16), 0, Mono.dp(16));
            card.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            d.setScaleX(0.4f);
            d.setScaleY(0.4f);
            d.animate().scaleX(1f).scaleY(1f).rotationBy(720f).setDuration(1400).start();
        }

        String text = m.optString("text", "");
        if (!text.isEmpty()) {
            TextView t = new TextView(c);
            t.setText(Mono.html(text));
            t.setTextColor(Mono.fg());
            t.setLinkTextColor(Mono.fg());
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTypeface(Mono.BODY);
            t.setLineSpacing(0, 1.18f);
            t.setPadding(Mono.dp(14), Mono.dp(12), Mono.dp(14), Mono.dp(12));
            if (text.contains("<a ")) {
                t.setMovementMethod(LinkMovementMethod.getInstance());
            }
            card.addView(t);
        }

        JSONArray rows = m.optJSONArray("buttons");
        if (rows != null && rows.length() > 0) {
            if (!text.isEmpty() || dice != null || !photo.isEmpty()) {
                View sep = line();
                sep.setAlpha(0.15f);
                card.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(1)));
            }
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Mono.dp(6), Mono.dp(6), Mono.dp(6), Mono.dp(6));
            for (int r = 0; r < rows.length(); r++) {
                JSONArray row = rows.optJSONArray(r);
                if (row == null) {
                    continue;
                }
                LinearLayout rowView = new LinearLayout(c);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < row.length(); i++) {
                    JSONObject b = row.optJSONObject(i);
                    if (b == null) {
                        continue;
                    }
                    rowView.addView(inlineButton(id, b, row.length()));
                }
                box.addView(rowView);
            }
            card.addView(box);
        }
        return card;
    }

    private TextView inlineButton(final int msgId, final JSONObject b, int inRow) {
        final TextView v = Mono.makeButton(act, b.optString("text"), false, inRow >= 4 ? 16 : 14);
        v.setMaxLines(2);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.setMinHeight(Mono.dp(inRow >= 4 ? 50 : 44));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = Mono.dp(3);
        lp.setMargins(m, m, m, m);
        v.setLayoutParams(lp);
        final String url = b.optString("url", "");
        final String data = b.optString("data", "");
        v.setOnClickListener(x -> {
            if (!url.isEmpty()) {
                try {
                    act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    toast("Не удалось открыть ссылку");
                }
                return;
            }
            v.setAlpha(0.45f);
            v.postDelayed(() -> v.setAlpha(1f), 1500);
            press(msgId, data);
        });
        return v;
    }

    private void upsert(JSONObject m) {
        int id = m.optInt("id");
        boolean isNew = !msgs.containsKey(id);
        msgs.put(id, m);
        View nv = buildMessage(m);
        View old = views.get(id);
        if (old != null) {
            int idx = list.indexOfChild(old);
            list.removeView(old);
            list.addView(nv, Math.max(0, idx));
        } else {
            int idx = msgs.headMap(id).size();
            list.addView(nv, Math.min(idx, list.getChildCount()));
        }
        views.put(id, nv);
        while (msgs.size() > MAX_VIEWS) {
            remove(msgs.firstKey());
        }
        updateEmpty();
        if (isNew) {
            scrollToBottom();
        }
    }

    private void remove(int id) {
        msgs.remove(id);
        View v = views.remove(id);
        if (v != null) {
            list.removeView(v);
        }
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = msgs.isEmpty();
        if (empty && emptyHint.getParent() == null) {
            list.addView(emptyHint);
        } else if (!empty && emptyHint.getParent() != null) {
            list.removeView(emptyHint);
        }
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.scrollTo(0, list.getBottom()));
    }

    // ===================== МЕНЮ =====================
    private void setMenu(JSONArray rows) {
        menu = rows;
        if (rows == null) {
            act.prefs.edit().remove("menu").apply();
        } else {
            act.prefs.edit().putString("menu", rows.toString()).apply();
        }
        renderMenu();
    }

    private List<String> menuItems() {
        List<String> out = new ArrayList<>();
        if (menu == null) {
            return out;
        }
        for (int r = 0; r < menu.length(); r++) {
            JSONArray row = menu.optJSONArray(r);
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                out.add(row.optString(i));
            }
        }
        return out;
    }

    private void renderMenu() {
        chips.removeAllViews();
        grid.removeAllViews();
        boolean has = menu != null && menu.length() > 0;
        if (!has) {
            gridOpen = false;
        }
        chipsScroll.setVisibility(has && !gridOpen ? View.VISIBLE : View.GONE);
        grid.setVisibility(has && gridOpen ? View.VISIBLE : View.GONE);
        if (!has) {
            return;
        }
        if (gridOpen) {
            for (int r = 0; r < menu.length(); r++) {
                JSONArray row = menu.optJSONArray(r);
                if (row == null) {
                    continue;
                }
                LinearLayout rv = new LinearLayout(act);
                for (int i = 0; i < row.length(); i++) {
                    rv.addView(menuButton(row.optString(i), true));
                }
                grid.addView(rv);
            }
        } else {
            for (String s : menuItems()) {
                chips.addView(menuButton(s, false));
            }
        }
    }

    private TextView menuButton(final String text, boolean inGrid) {
        TextView b = Mono.makeButton(act, text, false, 14);
        b.setSingleLine(true);
        LinearLayout.LayoutParams lp = inGrid
                ? new LinearLayout.LayoutParams(0, Mono.dp(42), 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mono.dp(38));
        int m = Mono.dp(3);
        lp.setMargins(m, m, m, m);
        b.setLayoutParams(lp);
        b.setPadding(Mono.dp(12), 0, Mono.dp(12), 0);
        b.setOnClickListener(v -> {
            if (gridOpen) {
                gridOpen = false;
                renderMenu();
            }
            send(text);
        });
        return b;
    }

    // ===================== ДЕЙСТВИЯ =====================
    private void sendInput() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) {
            return;
        }
        input.setText("");
        send(t);
    }

    void send(final String text) {
        actions.execute(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("text", text);
                Api.call(server, "POST", "/api/send", b, token, 15000);
            } catch (Api.ApiError e) {
                act.ui.post(() -> toast(e.getMessage()));
            } catch (JSONException ignored) {
                // не бывает
            }
        });
    }

    private void press(final int msgId, final String data) {
        actions.execute(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("id", msgId);
                b.put("data", data);
                Api.call(server, "POST", "/api/press", b, token, 15000);
            } catch (Api.ApiError e) {
                act.ui.post(() -> toast(e.getMessage()));
            } catch (JSONException ignored) {
                // не бывает
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }

    private void alert(String s) {
        new AlertDialog.Builder(act).setMessage(s).setPositiveButton("OK", null).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && input != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
        }
    }

    // ===================== СВЯЗЬ С СЕРВЕРОМ =====================
    void start() {
        if (running) {
            return;
        }
        running = true;
        final int my = ++gen;
        new Thread(() -> loop(my)).start();
    }

    void stop() {
        running = false;
        gen++;
    }

    private void loop(final int my) {
        while (running && my == gen) {
            try {
                final JSONObject r;
                if (epoch == null) {
                    r = Api.call(server, "GET", "/api/state", null, token, 15000);
                } else {
                    r = Api.call(server, "GET", "/api/poll?after=" + seq + "&epoch=" + epoch, null, token, 40000);
                }
                epoch = r.optString("epoch", null);
                seq = r.optInt("seq", 0);
                act.ui.post(() -> {
                    if (my == gen) {
                        setStatus("");
                        handle(r);
                    }
                });
            } catch (Api.ApiError e) {
                if (e.code == 401) {
                    act.ui.post(() -> {
                        if (my == gen) {
                            toast("Сессия устарела — войдите снова");
                            act.logout();
                        }
                    });
                    return;
                }
                act.ui.post(() -> {
                    if (my == gen) {
                        setStatus("нет связи…");
                    }
                });
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }
    }

    private void setStatus(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    private void handle(JSONObject r) {
        JSONArray all = r.optJSONArray("messages");
        if (all != null) {  // полное состояние
            msgs.clear();
            for (int i = 0; i < all.length(); i++) {
                JSONObject m = all.optJSONObject(i);
                if (m != null) {
                    msgs.put(m.optInt("id"), m);
                }
            }
            JSONArray mn = r.optJSONArray("menu");
            if (mn != null) {
                menu = mn;
                act.prefs.edit().putString("menu", mn.toString()).apply();
            }
            list.removeAllViews();
            views.clear();
            for (JSONObject m : msgs.values()) {
                View v = buildMessage(m);
                views.put(m.optInt("id"), v);
                list.addView(v);
            }
            updateEmpty();
            renderMenu();
            scrollToBottom();
            return;
        }
        JSONArray evs = r.optJSONArray("events");
        if (evs == null) {
            return;
        }
        for (int i = 0; i < evs.length(); i++) {
            JSONObject ev = evs.optJSONObject(i);
            if (ev == null) {
                continue;
            }
            switch (ev.optString("t")) {
                case "msg":
                    JSONObject m = ev.optJSONObject("m");
                    if (m != null) {
                        upsert(m);
                    }
                    break;
                case "del":
                    remove(ev.optInt("id"));
                    break;
                case "alert":
                    alert(ev.optString("text"));
                    break;
                case "toast":
                    toast(ev.optString("text"));
                    break;
                case "menu":
                    setMenu(ev.optJSONArray("rows"));
                    break;
                default:
                    break;
            }
        }
    }
}

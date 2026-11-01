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
    private TextView menuBtn;

    private final ChatPanel chat;
    private boolean chatTab = false;
    private TextView tabGame;
    private TextView tabChat;
    private View gameArea;
    private View chatArea;
    private boolean linked = false;

    GameScreen(MainActivity act, String server, String token) {
        this.act = act;
        this.server = server;
        this.token = token;
        this.chat = new ChatPanel(this, act);
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
        top.setPadding(Mono.dp(18), 0, Mono.dp(12), 0);

        View dot = new View(c);
        dot.setBackground(Mono.blueGradient(3));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Mono.dp(12), Mono.dp(12));
        dotLp.rightMargin = Mono.dp(10);
        top.addView(dot, dotLp);

        TextView title = new TextView(c);
        title.setText(Config.APP_NAME);
        title.setTypeface(Mono.DISPLAY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setLetterSpacing(0.06f);
        title.setTextColor(Mono.TEXT);
        top.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(c);
        status.setTextColor(Mono.MUTED);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(Mono.dp(10), 0, 0, 0);
        top.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView more = Mono.makeButton(c, "⋯", Mono.SOFT, 18, 17);
        more.setOnClickListener(v -> showMenu());
        more.setPadding(Mono.dp(14), 0, Mono.dp(14), 0);
        top.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mono.dp(34)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(58)));
        root.addView(line(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Mono.dp(1)));

        // --- вкладки Игра / Чат ---
        LinearLayout tabs = new LinearLayout(c);
        tabs.setBackground(Mono.rounded(Mono.SURFACE, 16, Mono.LINE, 1));
        tabs.setPadding(Mono.dp(4), Mono.dp(4), Mono.dp(4), Mono.dp(4));
        tabGame = tabButton("🎮 Игра");
        tabChat = tabButton("💬 Чат");
        tabGame.setOnClickListener(v -> setChatTab(false));
        tabChat.setOnClickListener(v -> setChatTab(true));
        tabs.addView(tabGame, new LinearLayout.LayoutParams(0, Mono.dp(38), 1));
        tabs.addView(tabChat, new LinearLayout.LayoutParams(0, Mono.dp(38), 1));
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.setMargins(Mono.dp(12), Mono.dp(10), Mono.dp(12), 0);
        root.addView(tabs, tabsLp);

        LinearLayout gameBox = new LinearLayout(c);
        gameBox.setOrientation(LinearLayout.VERTICAL);
        gameArea = gameBox;

        // --- лента ---
        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Mono.dp(14), Mono.dp(14), Mono.dp(14), Mono.dp(6));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        gameBox.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        emptyHint = new TextView(c);
        emptyHint.setText("Откройте раздел в меню внизу");
        emptyHint.setTextColor(Mono.MUTED);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        emptyHint.setPadding(0, Mono.dp(80), 0, Mono.dp(80));

        // --- нижняя панель ---
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Mono.SURFACE);
        float r = Mono.dp(22);
        pbg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(pbg);
        panel.setPadding(0, Mono.dp(6), 0, 0);

        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Mono.dp(10), Mono.dp(6), Mono.dp(10), 0);
        panel.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        chipsScroll = new HorizontalScrollView(c);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Mono.dp(10), Mono.dp(6), Mono.dp(10), 0);
        chipsScroll.addView(chips);
        panel.addView(chipsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(Mono.dp(10), Mono.dp(8), Mono.dp(10), Mono.dp(12));

        menuBtn = Mono.makeButton(c, "☰", Mono.SOFT, 20, 24);
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
        input.setTextColor(Mono.TEXT);
        input.setHintTextColor(Mono.MUTED);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setBackground(Mono.rounded(Mono.SURFACE2, 24, Mono.LINE, 1));
        input.setPadding(Mono.dp(18), 0, Mono.dp(18), 0);
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

        TextView sendBtn = Mono.makeButton(c, "➤", Mono.BLUE, 18, 24);
        sendBtn.setOnClickListener(v -> sendInput());
        bottom.addView(sendBtn, new LinearLayout.LayoutParams(Mono.dp(48), Mono.dp(48)));
        panel.addView(bottom);
        gameBox.addView(panel);
        root.addView(gameBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        chatArea = chat.build();
        root.addView(chatArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        applyTab();

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

    private TextView tabButton(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Mono.MEDIUM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setClickable(true);
        return t;
    }

    private void setChatTab(boolean on) {
        chatTab = on;
        hideKeyboard();
        applyTab();
        if (on) {
            chat.onShow();
        }
    }

    private void applyTab() {
        gameArea.setVisibility(chatTab ? View.GONE : View.VISIBLE);
        chatArea.setVisibility(chatTab ? View.VISIBLE : View.GONE);
        tabGame.setBackground(chatTab ? null : Mono.blueGradient(12));
        tabChat.setBackground(chatTab ? Mono.blueGradient(12) : null);
        tabGame.setTextColor(chatTab ? Mono.MUTED : Mono.TEXT);
        tabChat.setTextColor(chatTab ? Mono.TEXT : Mono.MUTED);
        updateChatBadge();
    }

    boolean chatVisible() {
        return chatTab;
    }

    void updateChatBadge() {
        if (tabChat == null) {
            return;
        }
        int n = chat.unreadTotal();
        tabChat.setText(n > 0 ? "💬 Чат  •  " + n : "💬 Чат");
    }

    // ===================== МЕНЮ «⋯» и привязка Telegram =====================
    private void showMenu() {
        final String linkItem = linked ? "Отвязать Telegram" : "Привязать Telegram";
        new AlertDialog.Builder(act)
                .setItems(new CharSequence[]{linkItem, "Выйти из аккаунта"}, (d, which) -> {
                    if (which == 0) {
                        if (linked) {
                            confirmUnlink();
                        } else {
                            confirmLink();
                        }
                    } else {
                        new AlertDialog.Builder(act)
                                .setMessage("Выйти из аккаунта?")
                                .setPositiveButton("Выйти", (d2, w) -> act.logout())
                                .setNegativeButton("Отмена", null)
                                .show();
                    }
                })
                .show();
    }

    private void confirmLink() {
        new AlertDialog.Builder(act)
                .setTitle("Привязать Telegram")
                .setMessage("Если вы уже играли в боте в Telegram, после привязки в приложении будет ваш "
                        + "Telegram-персонаж со всем прогрессом. Играть можно и там, и здесь.\n\n"
                        + "Персонаж этого аккаунта приложения сохранится — к нему можно вернуться, отвязав Telegram.")
                .setPositiveButton("Получить код", (d, w) -> startLink())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void startLink() {
        actions.execute(() -> {
            try {
                final JSONObject r = Api.call(server, "POST", "/api/link/start", new JSONObject(), token, 15000);
                act.ui.post(() -> showLinkCode(r.optString("code"), r.isNull("bot") ? "" : r.optString("bot", "")));
            } catch (Api.ApiError e) {
                act.ui.post(() -> toast(e.getMessage()));
            }
        });
    }

    private void showLinkCode(final String code, final String bot) {
        final String cmd = "/link " + code;
        AlertDialog.Builder b = new AlertDialog.Builder(act)
                .setTitle("Код привязки")
                .setMessage("Отправьте боту игры в Telegram сообщение:\n\n" + cmd
                        + "\n\nКод действует 10 минут. После этого приложение переключится само.")
                .setNegativeButton("Копировать", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            act.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("code", cmd));
                        toast("Скопировано");
                    }
                })
                .setNeutralButton("Закрыть", null);
        if (!bot.isEmpty()) {
            b.setPositiveButton("Открыть Telegram", (d, w) -> {
                try {
                    act.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/" + bot + "?start=link_" + code)));
                } catch (Exception e) {
                    toast("Не удалось открыть Telegram");
                }
            });
        }
        b.show();
    }

    private void confirmUnlink() {
        new AlertDialog.Builder(act)
                .setMessage("Отвязать Telegram? В приложении вернётся персонаж этого аккаунта приложения.")
                .setPositiveButton("Отвязать", (d, w) -> actions.execute(() -> {
                    try {
                        Api.call(server, "POST", "/api/link/remove", new JSONObject(), token, 15000);
                    } catch (Api.ApiError e) {
                        act.ui.post(() -> toast(e.getMessage()));
                    }
                }))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private View line() {
        View v = new View(act);
        v.setBackgroundColor(Mono.LINE);
        return v;
    }

    static void cursor(EditText e) {
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(Mono.BLUE_HI);
            d.setSize(Mono.dp(2), Mono.dp(20));
            e.setTextCursorDrawable(d);
        }
    }

    /** Кнопки «назад/отмена» — приглушённые, остальные — яркие синие. */
    private static int styleFor(String text) {
        String t = text.toLowerCase();
        if (t.contains("⬅") || t.contains("←") || t.contains("назад") || t.contains("отмена")
                || t.contains("к списку") || t.contains("❌") || t.contains("закрыть")
                || t.equals("⬜") || t.equals("❓")) {
            return Mono.SOFT;
        }
        return Mono.BLUE;
    }

    // ===================== СООБЩЕНИЯ =====================
    private View buildMessage(final JSONObject m) {
        Context c = act;
        final int id = m.optInt("id");
        if (m.optBoolean("out")) {
            TextView t = new TextView(c);
            t.setText(Mono.html(m.optString("text")));
            t.setTextColor(Mono.TEXT);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            GradientDrawable bub = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Mono.BLUE_HI, Mono.BLUE_C});
            float br = Mono.dp(18);
            bub.setCornerRadii(new float[]{br, br, br, br, Mono.dp(4), Mono.dp(4), br, br});
            t.setBackground(bub);
            t.setPadding(Mono.dp(16), Mono.dp(10), Mono.dp(16), Mono.dp(10));
            t.setMaxWidth((int) (act.getResources().getDisplayMetrics().widthPixels * 0.78f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.END;
            lp.bottomMargin = Mono.dp(12);
            lp.leftMargin = Mono.dp(48);
            t.setLayoutParams(lp);
            return t;
        }

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Mono.rounded(Mono.SURFACE, 20, Mono.LINE, 1));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = Mono.dp(14);
        card.setLayoutParams(cardLp);

        String photo = m.isNull("photo") ? "" : m.optString("photo", "");
        if (!photo.isEmpty()) {
            ImageView iv = new ImageView(c);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMinimumHeight(Mono.dp(200));
            iv.setBackgroundColor(Mono.SURFACE2);
            card.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            String url = photo.startsWith("http") ? photo : server + photo;
            Images.load(url, iv, m.optBoolean("anim") || url.endsWith(".gif"), () -> {
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
            d.setPadding(0, Mono.dp(18), 0, Mono.dp(18));
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
            t.setTextColor(Mono.TEXT);
            t.setLinkTextColor(Mono.BLUE_HI);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTypeface(Mono.BODY);
            t.setLineSpacing(0, 1.25f);
            t.setPadding(Mono.dp(18), Mono.dp(16), Mono.dp(18), Mono.dp(14));
            if (text.contains("<a ")) {
                t.setMovementMethod(LinkMovementMethod.getInstance());
            }
            card.addView(t);
        }

        JSONArray rows = m.optJSONArray("buttons");
        if (rows != null && rows.length() > 0) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            int top = text.isEmpty() ? Mono.dp(10) : 0;
            box.setPadding(Mono.dp(10), top, Mono.dp(10), Mono.dp(12));
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
        String label = b.optString("text");
        final TextView v = Mono.makeButton(act, label, styleFor(label), inRow >= 4 ? 16 : 14, 14);
        v.setMaxLines(2);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.setMinHeight(Mono.dp(inRow >= 4 ? 52 : 46));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int mg = Mono.dp(3);
        lp.setMargins(mg, mg, mg, mg);
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
            v.setAlpha(0.5f);
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
            nv.setAlpha(0f);
            nv.setTranslationY(Mono.dp(14));
            nv.animate().alpha(1f).translationY(0f).setDuration(220).start();
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
        scroll.post(() -> scroll.smoothScrollTo(0, list.getBottom()));
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
        if (menuBtn != null) {
            menuBtn.setBackground(Mono.button(gridOpen ? Mono.BLUE : Mono.SOFT, 24));
        }
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
        boolean home = text.contains("Меню");
        TextView b = Mono.makeButton(act, text, home ? Mono.BLUE : Mono.SOFT, 14, inGrid ? 14 : 19);
        b.setSingleLine(true);
        LinearLayout.LayoutParams lp = inGrid
                ? new LinearLayout.LayoutParams(0, Mono.dp(46), 1)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mono.dp(38));
        int mg = Mono.dp(3);
        lp.setMargins(mg, mg, mg, mg);
        b.setLayoutParams(lp);
        b.setPadding(Mono.dp(14), 0, Mono.dp(14), 0);
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
                        act.refreshServerAsync();
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
            linked = r.optBoolean("linked");
            chat.admin = r.optBoolean("admin");
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
                case "chat":
                    JSONObject cm = ev.optJSONObject("m");
                    if (cm != null) {
                        chat.onChat(cm);
                    }
                    break;
                case "chatdel":
                    chat.onDelete(ev.optLong("id"));
                    break;
                case "relink":
                    toast(ev.optString("text", "Аккаунт обновлён"));
                    chat.reset();
                    if (chatTab) {
                        chat.onShow();
                    }
                    epoch = null;  // заново загрузить состояние (другой персонаж)
                    break;
                default:
                    break;
            }
        }
    }
}

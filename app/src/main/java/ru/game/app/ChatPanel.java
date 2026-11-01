package ru.game.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Вкладка «Чат»: общий чат и личные сообщения между игроками. */
public class ChatPanel {
    private static final String GLOBAL = "global";
    private static final int KEEP = 200;

    private final GameScreen g;
    private final MainActivity act;

    private String room = GLOBAL;
    private final Map<String, List<JSONObject>> rooms = new HashMap<>();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final List<String> dialogs = new ArrayList<>();
    private boolean dialogsLoaded = false;
    boolean admin = false;

    private LinearLayout roomsBar;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private TextView title;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    ChatPanel(GameScreen g, MainActivity act) {
        this.g = g;
        this.act = act;
    }

    void reset() {
        rooms.clear();
        unread.clear();
        names.clear();
        dialogs.clear();
        dialogsLoaded = false;
        room = GLOBAL;
    }

    int unreadTotal() {
        int n = 0;
        for (int v : unread.values()) {
            n += v;
        }
        return n;
    }

    // ===================== ВИД =====================
    View build() {
        Context c = act;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        roomsBar = new LinearLayout(c);
        roomsBar.setPadding(Mono.dp(10), Mono.dp(8), Mono.dp(10), Mono.dp(8));
        hs.addView(roomsBar);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        title = new TextView(c);
        title.setTextColor(Mono.MUTED);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setPadding(Mono.dp(18), 0, Mono.dp(18), Mono.dp(4));
        root.addView(title);

        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Mono.dp(12), Mono.dp(6), Mono.dp(12), Mono.dp(6));
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout panel = new LinearLayout(c);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Mono.SURFACE);
        float r = Mono.dp(22);
        pbg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(pbg);
        panel.setPadding(Mono.dp(10), Mono.dp(10), Mono.dp(10), Mono.dp(12));

        input = new EditText(c);
        input.setHint("Сообщение…");
        input.setTextColor(Mono.TEXT);
        input.setHintTextColor(Mono.MUTED);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setBackground(Mono.rounded(Mono.SURFACE2, 24, Mono.LINE, 1));
        input.setPadding(Mono.dp(18), Mono.dp(12), Mono.dp(18), Mono.dp(12));
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        GameScreen.cursor(input);
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        inLp.rightMargin = Mono.dp(8);
        panel.addView(input, inLp);

        TextView send = Mono.makeButton(c, "➤", Mono.BLUE, 18, 24);
        send.setOnClickListener(v -> sendInput());
        panel.addView(send, new LinearLayout.LayoutParams(Mono.dp(48), Mono.dp(48)));
        root.addView(panel);

        renderRooms();
        renderRoom();
        return root;
    }

    /** Вкладку открыли — подгружаем диалоги и историю текущей комнаты. */
    void onShow() {
        if (!dialogsLoaded) {
            loadDialogs();
        }
        if (!rooms.containsKey(room)) {
            loadHistory(room);
        }
        unread.remove(room);
        g.updateChatBadge();
        renderRooms();
    }

    private void renderRooms() {
        if (roomsBar == null) {
            return;
        }
        roomsBar.removeAllViews();
        roomsBar.addView(roomChip(GLOBAL, "🌐 Общий"));
        for (String r : dialogs) {
            roomsBar.addView(roomChip(r, "👤 " + names.getOrDefault(r, "Игрок")));
        }
        TextView plus = Mono.makeButton(act, "＋ Написать", Mono.SOFT, 14, 19);
        plus.setOnClickListener(v -> askNewDialog());
        roomsBar.addView(plus, chipLp());
        if (title != null) {
            title.setText(GLOBAL.equals(room)
                    ? "Общий чат — видят все игроки. Нажмите на имя, чтобы написать лично."
                    : "Личные сообщения с игроком " + names.getOrDefault(room, ""));
        }
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Mono.dp(38));
        lp.rightMargin = Mono.dp(6);
        return lp;
    }

    private TextView roomChip(final String r, String label) {
        int n = unread.getOrDefault(r, 0);
        TextView b = Mono.makeButton(act, n > 0 ? label + "  " + n : label, r.equals(room) ? Mono.BLUE : Mono.SOFT, 14, 19);
        b.setSingleLine(true);
        b.setPadding(Mono.dp(14), 0, Mono.dp(14), 0);
        b.setLayoutParams(chipLp());
        b.setOnClickListener(v -> openRoom(r));
        return b;
    }

    void openRoom(String r) {
        room = r;
        unread.remove(r);
        g.updateChatBadge();
        renderRooms();
        renderRoom();
        if (!rooms.containsKey(r)) {
            loadHistory(r);
        }
    }

    private void renderRoom() {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        List<JSONObject> l = rooms.get(room);
        if (l == null || l.isEmpty()) {
            TextView e = new TextView(act);
            e.setText(l == null ? "Загрузка…" : "Сообщений пока нет — напишите первым!");
            e.setTextColor(Mono.MUTED);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, Mono.dp(60), 0, Mono.dp(60));
            list.addView(e);
            return;
        }
        for (JSONObject m : l) {
            list.addView(bubble(m));
        }
        scrollBottom();
    }

    private void scrollBottom() {
        scroll.post(() -> scroll.scrollTo(0, list.getBottom()));
    }

    private View bubble(final JSONObject m) {
        boolean mine = m.optBoolean("mine");
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.bottomMargin = Mono.dp(8);
        if (mine) {
            lp.leftMargin = Mono.dp(48);
        } else {
            lp.rightMargin = Mono.dp(48);
        }
        box.setLayoutParams(lp);

        if (!mine && GLOBAL.equals(room)) {
            TextView name = new TextView(act);
            name.setText(m.optString("name", "Игрок"));
            name.setTextColor(Mono.BLUE_HI);
            name.setTypeface(Mono.MEDIUM);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            name.setPadding(Mono.dp(6), 0, 0, Mono.dp(3));
            final long from = m.optLong("from");
            final String nm = m.optString("name", "Игрок");
            name.setOnClickListener(v -> openDm(from, nm));
            box.addView(name);
        }

        TextView t = new TextView(act);
        t.setText(m.optString("text"));
        t.setTextColor(Mono.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(Mono.dp(14), Mono.dp(9), Mono.dp(14), Mono.dp(9));
        t.setMaxWidth((int) (act.getResources().getDisplayMetrics().widthPixels * 0.75f));
        float r = Mono.dp(18);
        float s = Mono.dp(4);
        GradientDrawable bg;
        if (mine) {
            bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Mono.BLUE_HI, Mono.BLUE_C});
            bg.setCornerRadii(new float[]{r, r, r, r, s, s, r, r});
        } else {
            bg = new GradientDrawable();
            bg.setColor(Mono.SURFACE2);
            bg.setCornerRadii(new float[]{r, r, r, r, r, r, s, s});
        }
        t.setBackground(bg);
        box.addView(t);

        TextView time = new TextView(act);
        time.setText(timeFmt.format(new Date(m.optLong("ts") * 1000L)));
        time.setTextColor(Mono.MUTED);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        time.setPadding(Mono.dp(6), Mono.dp(2), Mono.dp(6), 0);
        time.setGravity(mine ? Gravity.END : Gravity.START);
        box.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (mine || admin) {
            final long id = m.optLong("id");
            t.setOnLongClickListener(v -> {
                new AlertDialog.Builder(act)
                        .setMessage("Удалить сообщение?")
                        .setPositiveButton("Удалить", (d, w) -> delete(id))
                        .setNegativeButton("Отмена", null)
                        .show();
                return true;
            });
        }
        return box;
    }

    // ===================== СОБЫТИЯ =====================
    void onChat(JSONObject m) {
        String r = m.optString("room", GLOBAL);
        if (r.startsWith("dm:") && !dialogs.contains(r)) {
            dialogs.add(0, r);
            names.put(r, m.optString("name", "Игрок"));
        }
        List<JSONObject> l = rooms.get(r);
        if (l != null) {
            l.add(m);
            while (l.size() > KEEP) {
                l.remove(0);
            }
        }
        boolean visible = g.chatVisible() && r.equals(room);
        if (visible) {
            if (l != null) {
                if (l.size() == 1) {
                    renderRoom();
                } else {
                    list.addView(bubble(m));
                    scrollBottom();
                }
            }
        } else if (!m.optBoolean("mine")) {
            unread.put(r, unread.getOrDefault(r, 0) + 1);
            g.updateChatBadge();
        }
        renderRooms();
    }

    void onDelete(long id) {
        for (List<JSONObject> l : rooms.values()) {
            for (int i = l.size() - 1; i >= 0; i--) {
                if (l.get(i).optLong("id") == id) {
                    l.remove(i);
                }
            }
        }
        renderRoom();
    }

    // ===================== СЕТЬ =====================
    private void loadHistory(final String r) {
        new Thread(() -> {
            try {
                JSONObject res = Api.call(g.server, "GET", "/api/chat/history?room=" + URLEncoder.encode(r, "UTF-8"),
                        null, g.token, 15000);
                JSONArray arr = res.optJSONArray("messages");
                final List<JSONObject> l = new ArrayList<>();
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m != null) {
                        l.add(m);
                    }
                }
                act.ui.post(() -> {
                    rooms.put(r, l);
                    if (r.equals(room)) {
                        renderRoom();
                    }
                });
            } catch (Exception e) {
                act.ui.post(() -> toast("Не удалось загрузить чат"));
            }
        }).start();
    }

    private void loadDialogs() {
        new Thread(() -> {
            try {
                JSONObject res = Api.call(g.server, "GET", "/api/chat/dialogs", null, g.token, 15000);
                final JSONArray arr = res.optJSONArray("dialogs");
                act.ui.post(() -> {
                    dialogsLoaded = true;
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject d = arr.optJSONObject(i);
                        if (d == null) {
                            continue;
                        }
                        String r = d.optString("room");
                        if (!dialogs.contains(r)) {
                            dialogs.add(r);
                        }
                        names.put(r, d.optString("name", "Игрок"));
                    }
                    renderRooms();
                });
            } catch (Api.ApiError e) {
                // не страшно — список появится позже
            }
        }).start();
    }

    private void sendInput() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        final String r = room;
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("room", r);
                b.put("text", text);
                Api.call(g.server, "POST", "/api/chat/send", b, g.token, 15000);
            } catch (Api.ApiError e) {
                act.ui.post(() -> {
                    toast(e.getMessage());
                    if (input.getText().length() == 0) {
                        input.setText(text);
                    }
                });
            } catch (JSONException ignored) {
                // не бывает
            }
        }).start();
    }

    private void delete(final long id) {
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("id", id);
                Api.call(g.server, "POST", "/api/chat/delete", b, g.token, 15000);
            } catch (Api.ApiError e) {
                act.ui.post(() -> toast(e.getMessage()));
            } catch (JSONException ignored) {
                // не бывает
            }
        }).start();
    }

    private void openDm(long peer, String name) {
        String r = "dm:" + peer;
        if (!dialogs.contains(r)) {
            dialogs.add(0, r);
        }
        names.put(r, name);
        openRoom(r);
    }

    private void askNewDialog() {
        final EditText e = new EditText(act);
        e.setHint("логин, @username или ID");
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout wrap = new LinearLayout(act);
        wrap.setPadding(Mono.dp(22), Mono.dp(8), Mono.dp(22), 0);
        wrap.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(act)
                .setTitle("Написать игроку")
                .setView(wrap)
                .setPositiveButton("Найти", (d, w) -> find(e.getText().toString().trim()))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void find(final String q) {
        if (q.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("q", q);
                final JSONObject res = Api.call(g.server, "POST", "/api/chat/find", b, g.token, 15000);
                act.ui.post(() -> openDm(res.optLong("peer"), res.optString("name", "Игрок")));
            } catch (Api.ApiError e) {
                act.ui.post(() -> toast(e.getMessage()));
            } catch (JSONException ignored) {
                // не бывает
            }
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }
}

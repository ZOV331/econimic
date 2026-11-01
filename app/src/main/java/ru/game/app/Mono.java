package ru.game.app;

import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Чёрно-белая палитра и общие элементы оформления. */
public final class Mono {
    public static boolean dark = false;
    public static float density = 1f;

    public static final Typeface DISPLAY = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    public static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private Mono() {
    }

    public static int bg() {
        return dark ? 0xFF000000 : 0xFFFFFFFF;
    }

    public static int fg() {
        return dark ? 0xFFFFFFFF : 0xFF000000;
    }

    public static int muted() {
        return dark ? 0xFF8A8A8A : 0xFF707070;
    }

    public static int soft() {
        return dark ? 0xFF161616 : 0xFFF0F0F0;
    }

    public static int dp(float v) {
        return Math.round(v * density);
    }

    public static GradientDrawable box(int fill, int stroke, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        if (strokeDp > 0) {
            d.setStroke(Math.max(1, dp(strokeDp)), stroke);
        }
        return d;
    }

    /** Кнопка: обводка или заливка; при нажатии цвета меняются местами. */
    public static Drawable button(boolean filled) {
        StateListDrawable s = new StateListDrawable();
        Drawable normal = filled ? box(fg(), fg(), 1.5f) : box(bg(), fg(), 1.5f);
        Drawable pressed = filled ? box(bg(), fg(), 1.5f) : box(fg(), fg(), 1.5f);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static ColorStateList buttonText(boolean filled) {
        int normal = filled ? bg() : fg();
        int pressed = filled ? fg() : bg();
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{pressed, normal});
    }

    public static TextView makeButton(android.content.Context ctx, String text, boolean filled, float sizeSp) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        b.setTypeface(MEDIUM);
        b.setTextColor(buttonText(filled));
        b.setBackground(button(filled));
        b.setClickable(true);
        b.setFocusable(true);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        return b;
    }

    /** Фильтр «всё чёрно-белое»: фото и эмодзи тоже становятся серыми. */
    public static void grayscale(View root) {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        root.setLayerType(View.LAYER_TYPE_HARDWARE, p);
    }

    /** HTML от бота (b, i, u, s, a, code…) -> текст для TextView. */
    @SuppressWarnings("deprecation")
    public static CharSequence html(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String h = s.replace("\r", "")
                .replace("<tg-spoiler>", "").replace("</tg-spoiler>", "")
                .replace("<code>", "<tt>").replace("</code>", "</tt>")
                .replace("<pre>", "<tt>").replace("</pre>", "</tt>")
                .replace("\n", "<br>");
        CharSequence cs = Html.fromHtml(h, Html.FROM_HTML_MODE_LEGACY);
        SpannableStringBuilder b = new SpannableStringBuilder(cs);
        int end = b.length();
        while (end > 0 && Character.isWhitespace(b.charAt(end - 1))) {
            end--;
        }
        b.delete(end, b.length());
        return b;
    }
}

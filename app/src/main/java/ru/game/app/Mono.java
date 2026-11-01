package ru.game.app;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Тёмная тема: чёрный фон, белый текст, синие кнопки. */
public final class Mono {
    public static float density = 1f;

    /** Стили кнопок. */
    public static final int BLUE = 1;   // яркая синяя (градиент)
    public static final int SOFT = 2;   // приглушённая синяя (назад, отмена, второстепенное)

    public static final Typeface DISPLAY = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    public static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    // ---- палитра ----
    public static final int BG = 0xFF07080C;        // фон
    public static final int SURFACE = 0xFF10131B;   // карточки
    public static final int SURFACE2 = 0xFF181C27;  // поля ввода, подложки
    public static final int LINE = 0xFF242A3A;      // тонкие линии
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF8C95AB;
    public static final int BLUE_HI = 0xFF4F80FF;
    public static final int BLUE_C = 0xFF2B5CFF;
    public static final int BLUE_DEEP = 0xFF1B3ECC;
    public static final int BLUE_SOFT = 0xFF14204A;
    public static final int BLUE_SOFT_LINE = 0xFF2A3F86;

    private Mono() {
    }

    public static int dp(float v) {
        return Math.round(v * density);
    }

    public static GradientDrawable rounded(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            d.setStroke(Math.max(1, dp(strokeDp)), stroke);
        }
        return d;
    }

    public static GradientDrawable blueGradient(float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{BLUE_HI, BLUE_C});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public static Drawable button(int style, float radiusDp) {
        Drawable normal;
        Drawable pressed;
        if (style == BLUE) {
            normal = blueGradient(radiusDp);
            pressed = rounded(BLUE_DEEP, radiusDp, BLUE_DEEP, 0);
        } else {
            normal = rounded(BLUE_SOFT, radiusDp, BLUE_SOFT_LINE, 1);
            pressed = rounded(BLUE_C, radiusDp, BLUE_C, 1);
        }
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static ColorStateList buttonText() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{TEXT, TEXT});
    }

    public static TextView makeButton(android.content.Context ctx, String text, int style, float sizeSp, float radiusDp) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        b.setTypeface(MEDIUM);
        b.setTextColor(buttonText());
        b.setBackground(button(style, radiusDp));
        b.setClickable(true);
        b.setFocusable(true);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        return b;
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

package ru.game.app;

import android.app.Activity;
import android.content.Context;
import android.os.PowerManager;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;

/**
 * Мост между страницей игры (webapp) и Android для звонков (WebRTC в WebView).
 *
 * Когда игрок принимает звонок, страница вызывает RovariCall.active(true):
 * • экран не гаснет (FLAG_KEEP_SCREEN_ON);
 * • система не «усыпляет» сеть и микрофон (PARTIAL_WAKE_LOCK) — иначе звук
 *   обрывается через несколько минут разговора.
 * При завершении звонка страница вызывает RovariCall.active(false).
 *
 * Сам звонок (микрофон, камера, звук) делает страница через WebRTC —
 * разрешения на них уже выданы в MainActivity.handleMic().
 */
public final class CallKeep {
    private final Activity act;
    private PowerManager.WakeLock wake;

    public CallKeep(Activity a) {
        act = a;
    }

    /** Вызывается из JavaScript страницы игры. true — звонок начался, false — закончился. */
    @JavascriptInterface
    public void active(final boolean on) {
        act.runOnUiThread(() -> set(on));
    }

    /** Аварийное снятие (страница может не успеть вызвать active(false)). */
    public void release() {
        act.runOnUiThread(() -> set(false));
    }

    private void set(boolean on) {
        try {
            if (on) {
                act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (wake == null) {
                    PowerManager pm = (PowerManager) act.getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Rovari:Call");
                        wake.setReferenceCounted(false);
                        wake.acquire(4 * 60 * 60 * 1000L);   // страховка: не дольше 4 часов
                    }
                }
            } else {
                act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (wake != null) {
                    wake.release();
                    wake = null;
                }
            }
        } catch (Exception ignored) {
            // звонок всё равно работает, просто без режима «не засыпать»
        }
    }
}

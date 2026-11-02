package ru.game.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;

import java.io.File;
import java.security.MessageDigest;

/**
 * Защита приложения от подделки.
 * • Подпись: если APK разобрали, изменили и пересобрали — подпись другая, и приложение не запустится.
 * • Отладка: не даём подключать отладчик к выпущенной версии.
 * • Инструменты взлома (Frida, Xposed): при их обнаружении приложение не открывает игру.
 * Деньги, ставки и награды в любом случае считает сервер — изменить их на телефоне нельзя.
 */
final class Guard {

    /** Пустая строка — всё в порядке; иначе — причина, по которой запуск запрещён. */
    static String check(Context ctx) {
        try {
            if (!signatureOk(ctx)) {
                return "Эта копия приложения изменена. Скачайте официальную версию ROVARI.";
            }
            boolean debuggable = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (debuggable || Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return "Запуск под отладчиком запрещён.";
            }
            if (hookTools()) {
                return "На устройстве найдены программы для взлома приложений. Отключите их и перезапустите игру.";
            }
        } catch (Throwable t) {
            return "Не удалось проверить приложение. Переустановите его.";
        }
        return "";
    }

    @SuppressWarnings("deprecation")
    private static boolean signatureOk(Context ctx) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi.signingInfo == null || pi.signingInfo.hasMultipleSigners()) {
                return false;
            }
            sigs = pi.signingInfo.getSigningCertificateHistory();
        } else {
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
            sigs = pi.signatures;
        }
        if (sigs == null || sigs.length == 0) {
            return false;
        }
        String want = S.sig();
        for (Signature s : sigs) {
            if (want.equals(sha256(s.toByteArray()))) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : h) {
            sb.append(String.format("%02X", b & 0xff));
        }
        return sb.toString();
    }

    private static boolean hookTools() {
        // Xposed / LSPosed встраиваются в процесс приложения
        String[] classes = {"de.robv.android.xposed.XposedBridge", "de.robv.android.xposed.XC_MethodHook"};
        for (String c : classes) {
            try {
                Class.forName(c);
                return true;
            } catch (ClassNotFoundException ignored) {
                // нет — хорошо
            }
        }
        // Frida: сервер в стандартных местах и её библиотеки в памяти процесса
        String[] files = {"/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server"};
        for (String f : files) {
            if (new File(f).exists()) {
                return true;
            }
        }
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("frida") || line.contains("libgadget") || line.contains("XposedBridge")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // нет доступа — пропускаем
        }
        return false;
    }

    private Guard() {
    }
}

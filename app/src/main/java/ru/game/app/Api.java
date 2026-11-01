package ru.game.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Запросы к серверу игры (JSON по HTTP). */
public final class Api {
    private Api() {
    }

    public static final class ApiError extends Exception {
        private static final long serialVersionUID = 1L;
        public final int code;

        public ApiError(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static String normalize(String server) {
        String s = server == null ? "" : server.trim();
        if (s.isEmpty()) {
            return s;
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static JSONObject call(String base, String method, String path, JSONObject body,
                                  String token, int readTimeoutMs) throws ApiError {
        HttpURLConnection c = null;
        try {
            URL url = new URL(base + path);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestMethod(method);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = c.getOutputStream();
                os.write(bytes);
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = is == null ? "" : readAll(is);
            JSONObject o;
            try {
                o = new JSONObject(text);
            } catch (JSONException e) {
                o = new JSONObject();
            }
            if (code >= 400 || !o.optBoolean("ok", false)) {
                throw new ApiError(code, o.optString("error", "Ошибка сервера (" + code + ")"));
            }
            return o;
        } catch (IOException e) {
            throw new ApiError(0, "Нет связи с сервером");
        } catch (IllegalArgumentException e) {
            throw new ApiError(0, "Неверный адрес сервера");
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        is.close();
        return out.toByteArray();
    }

    static String readAll(InputStream is) throws IOException {
        return new String(readBytes(is), StandardCharsets.UTF_8);
    }
}

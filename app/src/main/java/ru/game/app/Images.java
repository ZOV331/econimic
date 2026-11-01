package ru.game.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Загрузка и кэш картинок. */
public final class Images {
    private static final int MAX_SIDE = 1080;
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 6)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Images() {
    }

    public static void load(final String url, final ImageView view, final Runnable onLoaded) {
        view.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            done(view);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            final Bitmap bm = fetch(url);
            if (bm == null) {
                return;
            }
            CACHE.put(url, bm);
            UI.post(() -> {
                if (url.equals(view.getTag())) {
                    view.setImageBitmap(bm);
                    done(view);
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                }
            });
        });
    }

    private static void done(ImageView view) {
        view.setMinimumHeight(0);
        view.setBackground(null);
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) {
                return null;
            }
            byte[] data = Api.readBytes(c.getInputStream());
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / sample > MAX_SIDE || o.outHeight / sample > MAX_SIDE * 2) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}

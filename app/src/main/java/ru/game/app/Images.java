package ru.game.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Загрузка и кэш картинок; GIF анимируются на Android 9+. */
public final class Images {
    private static final int MAX_SIDE = 1080;
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 6)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static final LruCache<String, byte[]> GIFS = new LruCache<String, byte[]>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return value.length;
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Images() {
    }

    public static void load(final String url, final ImageView view, boolean animated, final Runnable onLoaded) {
        view.setTag(url);
        if (animated && Build.VERSION.SDK_INT >= 28) {
            loadAnimated(url, view, onLoaded);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            setPixel(view, cached);
            done(view);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            byte[] data = download(url);
            final Bitmap bm = data == null ? null : decode(data);
            if (bm == null) {
                return;
            }
            CACHE.put(url, bm);
            UI.post(() -> {
                if (url.equals(view.getTag())) {
                    setPixel(view, bm);
                    done(view);
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                }
            });
        });
    }

    private static void loadAnimated(final String url, final ImageView view, final Runnable onLoaded) {
        view.setImageDrawable(null);
        POOL.execute(() -> {
            byte[] data = GIFS.get(url);
            if (data == null) {
                data = download(url);
                if (data == null) {
                    return;
                }
                GIFS.put(url, data);
            }
            Drawable d = null;
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)));
                }
            } catch (Exception e) {
                d = null;
            }
            final Drawable fd = d;
            final Bitmap fallback = fd == null ? decode(data) : null;
            UI.post(() -> {
                if (!url.equals(view.getTag())) {
                    return;
                }
                if (fd != null) {
                    fd.setFilterBitmap(false);
                    view.setImageDrawable(fd);
                    if (Build.VERSION.SDK_INT >= 28 && fd instanceof AnimatedImageDrawable) {
                        ((AnimatedImageDrawable) fd).start();
                    }
                } else if (fallback != null) {
                    setPixel(view, fallback);
                } else {
                    return;
                }
                done(view);
                if (onLoaded != null) {
                    onLoaded.run();
                }
            });
        });
    }

    /** Пиксель-арт: увеличиваем без сглаживания, чтобы пиксели оставались чёткими. */
    private static void setPixel(ImageView view, Bitmap bm) {
        BitmapDrawable d = new BitmapDrawable(view.getResources(), bm);
        d.setFilterBitmap(false);
        d.setAntiAlias(false);
        view.setImageDrawable(d);
    }

    private static void done(ImageView view) {
        view.setMinimumHeight(0);
        view.setBackground(null);
    }

    private static Bitmap decode(byte[] data) {
        try {
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
        }
    }

    private static byte[] download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) {
                return null;
            }
            return Api.readBytes(c.getInputStream());
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}

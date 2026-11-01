package ru.game.app;

/** Настройки приложения — меняйте здесь. */
public final class Config {
    /** Название в шапке. */
    public static final String APP_NAME = "ЭКОНОМИКА";

    /**
     * Адрес сервера по умолчанию (подставится на экране входа).
     * Например: "http://192.168.1.5:8901" или "http://ваш-сервер.ru:8901".
     * Оставьте пустым, чтобы игрок вводил адрес сам.
     */
    public static final String DEFAULT_SERVER = "https://dealt-bathrooms-matt-cgi.trycloudflare.com";

    /**
     * Файл с актуальным адресом сервера. Приложение читает его при запуске и когда пропадает связь.
     * Адрес туннеля сменился — поменяйте одну строку в server.txt на GitHub, пересобирать APK не нужно.
     */
    public static final String REMOTE_CONFIG = "https://raw.githubusercontent.com/ZOV331/econimic/main/server.txt";

    private Config() {
    }
}

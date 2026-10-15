package fr.lostaria.hytalenode.utils;

public final class HttpUtils {

    public static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static String toWebSocketUrl(String url) {
        if (url.startsWith("https://")) return "wss://" + url.substring("https://".length());
        if (url.startsWith("http://")) return "ws://" + url.substring("http://".length());
        return url;
    }
}

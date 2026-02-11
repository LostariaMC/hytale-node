package fr.lostaria.hytalenode.utils;

public final class HttpUtils {

    public static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}

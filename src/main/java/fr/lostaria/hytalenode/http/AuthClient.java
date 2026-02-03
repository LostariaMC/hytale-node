package fr.lostaria.hytalenode.http;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class AuthClient {

    private final HttpClient client;
    private final String baseUrl;
    private final Path deviceTokenPath;

    public AuthClient(String authUrl, String deviceTokenFilePath) {
        this.baseUrl = stripTrailingSlash(authUrl);
        this.deviceTokenPath = Path.of(deviceTokenFilePath);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String fetchJwt() throws Exception {
        String deviceToken = readDeviceTokenFile(deviceTokenPath);
        String encoded = URLEncoder.encode(deviceToken, StandardCharsets.UTF_8);

        URI uri = URI.create(baseUrl + "/token?deviceToken=" + encoded);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 200 || res.statusCode() == 201) {
            String jwt = res.body() == null ? "" : res.body().trim();
            if (jwt.isBlank()) {
                throw new IllegalStateException("Auth returned empty body");
            }
            return jwt;
        }

        throw new IllegalStateException("Auth failed: status=" + res.statusCode() + " body=" + res.body());
    }

    private static String readDeviceTokenFile(Path p) throws Exception {
        if (!Files.exists(p)) {
            throw new IllegalStateException("deviceToken file not found: " + p.toAbsolutePath());
        }
        String s = Files.readString(p, StandardCharsets.UTF_8).trim();
        if (s.isBlank()) {
            throw new IllegalStateException("deviceToken file is empty: " + p.toAbsolutePath());
        }
        return s;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

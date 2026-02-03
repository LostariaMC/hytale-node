package fr.lostaria.hytalenode.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.model.NodeModel;
import fr.lostaria.hytalenode.model.RegisterNodeRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ManagerClient {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public ManagerClient(String managerUrl) {
        this.baseUrl = stripTrailingSlash(managerUrl);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    public NodeModel register(RegisterNodeRequest req, String jwt) throws Exception {
        URI uri = URI.create(baseUrl + "/nodes");
        String json = mapper.writeValueAsString(req);

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + jwt)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = client.send(httpReq, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 201 || res.statusCode() == 200) {
            return mapper.readValue(res.body(), NodeModel.class);
        }

        throw new IllegalStateException("Register failed: status=" + res.statusCode() + " body=" + res.body());
    }

    public String pollMessage(String nodeId, int timeoutSeconds, String jwt) throws Exception {
        URI uri = URI.create(baseUrl + "/messages/" + nodeId + "?timeoutSeconds=" + timeoutSeconds);

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds + 5L))
                .header("Authorization", "Bearer " + jwt)
                .GET()
                .build();

        HttpResponse<String> res = client.send(httpReq, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 204) return null;
        if (res.statusCode() == 200) return res.body();

        if (res.statusCode() == 401) {
            throw new UnauthorizedException("Unauthorized (token expired?)");
        }

        throw new IllegalStateException("Poll failed: status=" + res.statusCode() + " body=" + res.body());
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String msg) { super(msg); }
    }
}

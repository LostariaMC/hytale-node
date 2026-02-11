package fr.lostaria.hytalenode.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.model.MessageEnvelope;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static fr.lostaria.hytalenode.utils.HttpUtils.clamp;
import static fr.lostaria.hytalenode.utils.HttpUtils.stripTrailingSlash;

public class PubsubClient {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public PubsubClient(String pubsubUrl) {
        this.baseUrl = stripTrailingSlash(pubsubUrl);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    public MessageEnvelope pollMessage(String consumer, int timeoutSeconds, String jwt) throws Exception {
        int t = clamp(timeoutSeconds, 1, 30);
        URI uri = URI.create(baseUrl + "/messages/" + consumer + "?timeoutSeconds=" + t);

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(t + 5L))
                .header("Authorization", "Bearer " + jwt)
                .GET()
                .build();

        HttpResponse<String> res = client.send(httpReq, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 204) return null;

        if (res.statusCode() == 200) {
            return mapper.readValue(res.body(), MessageEnvelope.class);
        }

        if (res.statusCode() == 401) {
            throw new UnauthorizedException("Unauthorized (token expired?)");
        }

        throw new IllegalStateException("Poll failed: status=" + res.statusCode() + " body=" + res.body());
    }
}

package fr.lostaria.hytalenode.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.model.NodeModel;
import fr.lostaria.hytalenode.model.RegisterNodeRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static fr.lostaria.hytalenode.utils.HttpUtils.stripTrailingSlash;

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

        System.out.println(uri);
        System.out.println(json);
        System.out.println(jwt);

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

        if (res.statusCode() == 401) {
            String body = res.body();
            throw new UnauthorizedException("Unauthorized: status=401 body=" + body);
        }

        throw new IllegalStateException("Register failed: status=" + res.statusCode() + " body=" + res.body());
    }
}

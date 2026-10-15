package fr.lostaria.hytalenode.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.http.UnauthorizedException;
import fr.lostaria.hytalenode.model.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static fr.lostaria.hytalenode.utils.HttpUtils.stripTrailingSlash;
import static fr.lostaria.hytalenode.utils.HttpUtils.toWebSocketUrl;

public class PubsubClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PubsubClient.class);

    private static final long PING_INTERVAL_SECONDS = 30;

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String wsBaseUrl;

    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pubsub-ping");
        t.setDaemon(true);
        return t;
    });

    public PubsubClient(String pubsubUrl) {
        this.wsBaseUrl = toWebSocketUrl(stripTrailingSlash(pubsubUrl));
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    public void listen(String consumer, String jwt, Consumer<MessageEnvelope> onMessage) throws Exception {
        URI uri = URI.create(wsBaseUrl + "/ws/" + consumer);
        CompletableFuture<Void> closed = new CompletableFuture<>();

        WebSocket ws;
        try {
            ws = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + jwt)
                    .buildAsync(uri, new Listener(onMessage, closed))
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof WebSocketHandshakeException h && h.getResponse().statusCode() == 401) {
                throw new UnauthorizedException("Unauthorized (token expired?)");
            }
            throw e;
        }

        LOGGER.info("PUBSUB connected consumer={}", consumer);

        ScheduledFuture<?> ping = pinger.scheduleAtFixedRate(() ->
                ws.sendPing(ByteBuffer.allocate(0))
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(err -> {
                            closed.completeExceptionally(err);
                            return null;
                        }),
                PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS
        );

        try {
            closed.join();
        } catch (CompletionException e) {
            throw new IllegalStateException("Pubsub connection lost: " + e.getCause(), e.getCause());
        } finally {
            ping.cancel(false);
            ws.abort();
        }
    }

    private final class Listener implements WebSocket.Listener {

        private final Consumer<MessageEnvelope> onMessage;
        private final CompletableFuture<Void> closed;
        private final StringBuilder buffer = new StringBuilder();

        private Listener(Consumer<MessageEnvelope> onMessage, CompletableFuture<Void> closed) {
            this.onMessage = onMessage;
            this.closed = closed;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String json = buffer.toString();
                buffer.setLength(0);
                try {
                    onMessage.accept(mapper.readValue(json, MessageEnvelope.class));
                } catch (Exception e) {
                    LOGGER.warn("Invalid pubsub message: {}", e.toString());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            LOGGER.warn("PUBSUB closed status={} reason={}", statusCode, reason);
            closed.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            closed.completeExceptionally(error);
        }
    }
}

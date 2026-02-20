package fr.lostaria.hytalenode.service.command;

import fr.lostaria.hytalenode.model.MessageEnvelope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageRouter {

    private final Map<String, MessageHandler> handlers = new HashMap<>();

    public MessageRouter(List<MessageHandler> handlerList) {
        for (MessageHandler h : handlerList) {
            handlers.put(h.type(), h);
        }
    }

    public void route(MessageEnvelope message) throws Exception {
        MessageHandler handler = handlers.get(message.type());
        if (handler != null) {
            handler.handle(message);
        }
    }
}

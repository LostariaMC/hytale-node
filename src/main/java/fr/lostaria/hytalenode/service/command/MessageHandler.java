package fr.lostaria.hytalenode.service.command;

import fr.lostaria.hytalenode.model.MessageEnvelope;

public interface MessageHandler {
    String type();
    void handle(MessageEnvelope message) throws Exception;
}

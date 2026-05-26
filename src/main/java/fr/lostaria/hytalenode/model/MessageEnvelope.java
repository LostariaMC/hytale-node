package fr.lostaria.hytalenode.model;

import com.fasterxml.jackson.databind.JsonNode;

public record MessageEnvelope(
        String consumer,
        String type,
        String producer,
        JsonNode payload
) {}

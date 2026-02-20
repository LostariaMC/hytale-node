package fr.lostaria.hytalenode.model.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeleteServerPayload(String serverId, int port) {}

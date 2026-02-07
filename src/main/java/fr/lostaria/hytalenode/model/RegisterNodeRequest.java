package fr.lostaria.hytalenode.model;

public record RegisterNodeRequest(String ip, int portRangeStart, int portRangeEnd) {}

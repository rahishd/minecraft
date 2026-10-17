package com.cydev.mod.government.election;

import java.util.UUID;

/** A person standing for mayor. */
public record Candidate(UUID playerId, String name, String slogan) {
}

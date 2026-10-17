package com.cydev.mod.government.council;

import java.util.UUID;

public record Appointment(UUID playerId, String name, Role role) {
}

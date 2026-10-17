package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;
import java.util.UUID;

/** Who is performing an action. wardId is set for ward staff and applicants' own ward. */
public record Actor(UUID id, String name, Position position, UUID wardId) {
}

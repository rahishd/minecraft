package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;
import java.util.UUID;

/** One immutable line of the audit log. */
public record AuditEntry(UUID actorId, String actorName, Position role, String action, UUID recordId,
						 CaseStatus oldStatus, CaseStatus newStatus, long timestamp, String reason) {
}

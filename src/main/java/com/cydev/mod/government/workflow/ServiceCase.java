package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A citizen service request handled at a ward counter. */
public class ServiceCase {
	public final UUID id = UUID.randomUUID();
	public final UUID applicantId;
	public final String applicantName;
	public final UUID wardId;
	public final ServiceType type;
	public final List<String> evidence = new ArrayList<>();
	public final long created;

	public CaseStatus status = CaseStatus.DRAFT;
	/** Status to return to after "more information required". */
	public CaseStatus returnStatus;
	public Position assignedRole = Position.RESIDENT;
	public long updated;
	/** Audit history of this case. */
	public final List<AuditEntry> history = new ArrayList<>();

	public ServiceCase(UUID applicantId, String applicantName, UUID wardId, ServiceType type, long now) {
		this.applicantId = applicantId;
		this.applicantName = applicantName;
		this.wardId = wardId;
		this.type = type;
		this.created = now;
		this.updated = now;
	}
}

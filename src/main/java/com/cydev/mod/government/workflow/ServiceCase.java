package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A citizen service request handled at a ward counter. */
public class ServiceCase {
	public final UUID id;
	public final int caseNo;
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
	/** Resident satisfaction rating 1-5 after the case ends; 0 = not rated. */
	public int rating;
	/** Audit history of this case. */
	public final List<AuditEntry> history = new ArrayList<>();

	public ServiceCase(UUID id, int caseNo, UUID applicantId, String applicantName, UUID wardId,
					   ServiceType type, long now) {
		this.id = id;
		this.caseNo = caseNo;
		this.applicantId = applicantId;
		this.applicantName = applicantName;
		this.wardId = wardId;
		this.type = type;
		this.created = now;
		this.updated = now;
	}

	public ServiceCase(UUID applicantId, String applicantName, UUID wardId, ServiceType type, long now) {
		this(UUID.randomUUID(), 0, applicantId, applicantName, wardId, type, now);
	}

	public boolean isEnded() {
		return status == CaseStatus.ISSUED || status == CaseStatus.CLOSED
			|| status == CaseStatus.REJECTED || status == CaseStatus.COMPLETED;
	}
}

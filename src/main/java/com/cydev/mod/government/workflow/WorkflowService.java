package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;
import java.util.List;
import java.util.Objects;

/**
 * Status transitions for citizen service cases.
 * Every method returns an error message, or null on success, and records an
 * audit entry on success. The Mayor has no special power here: services are
 * completed by ward staff or the responsible department.
 */
public class WorkflowService {
	private final AuthorityRules rules;
	private final List<AuditEntry> globalLog;

	public WorkflowService(AuthorityRules rules, List<AuditEntry> globalLog) {
		this.rules = rules;
		this.globalLog = globalLog;
	}

	public String move(ServiceCase c, Actor a, CaseStatus to, String reason, long now) {
		String err = check(c, a, to, reason);
		if (err != null) {
			return err;
		}
		CaseStatus old = c.status;
		if (to == CaseStatus.MORE_INFO_REQUIRED) {
			c.returnStatus = old;
		}
		c.status = to;
		c.assignedRole = assignedFor(c, to);
		c.updated = now;
		AuditEntry entry = new AuditEntry(a.id(), a.name(), a.position(), "STATUS_CHANGE", c.id, old, to, now, reason);
		c.history.add(entry);
		globalLog.add(entry);
		return null;
	}

	private Position assignedFor(ServiceCase c, CaseStatus to) {
		Position dept = rules.get(c.type).department();
		return switch (to) {
			case SUBMITTED, WARD_REVIEW, ISSUED -> Position.WARD_SECRETARY;
			case REFERRED, DEPARTMENT_REVIEW, APPROVED -> dept;
			case MORE_INFO_REQUIRED, DRAFT -> Position.RESIDENT;
			default -> c.assignedRole;
		};
	}

	private String check(ServiceCase c, Actor a, CaseStatus to, String reason) {
		AuthorityRules.Rule rule = rules.get(c.type);
		boolean applicant = a.id().equals(c.applicantId);
		boolean wardStaff = (a.position() == Position.WARD_SECRETARY || a.position() == Position.WARD_CHAIR)
			&& Objects.equals(a.wardId(), c.wardId);
		boolean deptOfficer = a.position() == rule.department();
		boolean cao = a.position() == Position.CAO;
		boolean hasReason = reason != null && !reason.isBlank();

		// Conflict of interest: you cannot process your own case as staff.
		boolean actingAsApplicant = c.status == CaseStatus.DRAFT || c.status == CaseStatus.MORE_INFO_REQUIRED
			|| to == CaseStatus.CLOSED;
		if (applicant && !actingAsApplicant) {
			return "Conflict of interest: you cannot process your own case.";
		}

		switch (c.status) {
			case DRAFT:
				if (to == CaseStatus.SUBMITTED && applicant) {
					return null;
				}
				return "Only the applicant can submit a draft.";
			case SUBMITTED:
				if (to == CaseStatus.WARD_REVIEW && wardStaff) {
					return null;
				}
				return "Only staff of this ward can start the review.";
			case WARD_REVIEW:
				if (!wardStaff) {
					return "Only staff of this ward can act on this case.";
				}
				switch (to) {
					case ISSUED:
						if (!rule.wardCanIssue()) {
							return "This service is outside ward authority; refer it to the municipality.";
						}
						if (c.evidence.isEmpty()) {
							return "Required documents are missing; return the case for more information.";
						}
						return null;
					case REFERRED:
						return hasReason ? null : "A reason is required to refer a case.";
					case MORE_INFO_REQUIRED:
					case REJECTED:
						return hasReason ? null : "A reason is required.";
					default:
						return "Invalid transition " + c.status + " -> " + to;
				}
			case REFERRED:
				if (to == CaseStatus.DEPARTMENT_REVIEW && (deptOfficer || cao)) {
					return null;
				}
				return "Only the " + rule.department() + " or the CAO can accept this referral.";
			case DEPARTMENT_REVIEW:
				if (!deptOfficer) {
					return "Only the " + rule.department() + " can decide this case.";
				}
				switch (to) {
					case APPROVED:
						return null;
					case REJECTED:
					case DEFERRED:
					case MORE_INFO_REQUIRED:
						return hasReason ? null : "A reason is required.";
					default:
						return "Invalid transition " + c.status + " -> " + to;
				}
			case APPROVED:
				if (to == CaseStatus.ISSUED && deptOfficer) {
					return null;
				}
				return "Only the " + rule.department() + " can issue an approved case.";
			case DEFERRED:
				if (to == CaseStatus.DEPARTMENT_REVIEW && deptOfficer) {
					return null;
				}
				if (to == CaseStatus.CLOSED && (applicant || wardStaff || deptOfficer)) {
					return null;
				}
				return "Invalid transition " + c.status + " -> " + to;
			case MORE_INFO_REQUIRED:
				if (applicant && to == c.returnStatus) {
					return null;
				}
				return "Only the applicant can resubmit, back to " + c.returnStatus + ".";
			case ISSUED:
			case REJECTED:
				if (to == CaseStatus.CLOSED && (applicant || wardStaff || deptOfficer || cao)) {
					return null;
				}
				return "Invalid transition " + c.status + " -> " + to;
			default:
				return "Case is " + c.status + "; no further action possible.";
		}
	}
}

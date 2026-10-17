package com.cydev.mod.government.project;

import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.government.workflow.AuditEntry;
import com.cydev.mod.government.workflow.CaseStatus;
import java.util.List;
import java.util.Objects;

/**
 * Ward project pipeline:
 * Draft -> Submitted -> Technical Review -> Budget Review -> Approved ->
 * Procurement -> In Progress -> Inspection -> Completed -> Closed.
 * Nobody can skip technical review, the budget check or procurement - not even
 * the Mayor, who only gives the final approval once both checks are clear.
 */
public class ProjectService {
	private final List<AuditEntry> globalLog;

	public ProjectService(List<AuditEntry> globalLog) {
		this.globalLog = globalLog;
	}

	/** Returns an error message, or null on success. */
	public String move(ProjectProposal p, Actor a, CaseStatus to, String reason, long now) {
		String err = check(p, a, to, reason);
		if (err != null) {
			return err;
		}
		CaseStatus old = p.status;
		apply(p, to);
		p.status = to;
		AuditEntry entry = new AuditEntry(a.id(), a.name(), a.position(), "PROJECT_STATUS", p.id, old, to, now, reason);
		p.history.add(entry);
		globalLog.add(entry);
		return null;
	}

	private void apply(ProjectProposal p, CaseStatus to) {
		switch (to) {
			case MORE_INFO_REQUIRED -> p.returnStatus = p.status;
			case TECHNICAL_REVIEW -> p.technicallyCleared = false;
			case BUDGET_REVIEW -> p.budgetCleared = false;
			case APPROVED -> p.budgetLine.committed += p.estimate;
			case COMPLETED -> {
				p.budgetLine.committed -= p.estimate;
				p.budgetLine.spent += p.estimate;
				p.progressPercent = 100;
			}
			case REJECTED, DEFERRED -> {
				if (p.status == CaseStatus.APPROVED || p.status == CaseStatus.PROCUREMENT) {
					p.budgetLine.committed -= p.estimate;
				}
			}
			default -> {
			}
		}
	}

	/** Sets the contractor when procurement is done. */
	public String assignContractor(ProjectProposal p, Actor a, String contractor) {
		if (a.position() != Position.ADMIN_OFFICER) {
			return "Only the Administration Officer can run procurement.";
		}
		if (p.status != CaseStatus.PROCUREMENT) {
			return "Project is not in procurement.";
		}
		if (contractor == null || contractor.isBlank()) {
			return "A contractor is required.";
		}
		p.contractor = contractor;
		return null;
	}

	private String check(ProjectProposal p, Actor a, CaseStatus to, String reason) {
		boolean hasReason = reason != null && !reason.isBlank();
		boolean wardChair = a.position() == Position.WARD_CHAIR && Objects.equals(a.wardId(), p.wardId);
		switch (p.status) {
			case DRAFT:
				return to == CaseStatus.SUBMITTED && wardChair ? null : "Only the Ward Chairperson can submit a proposal.";
			case SUBMITTED:
				return to == CaseStatus.TECHNICAL_REVIEW && a.position() == Position.ENGINEER
					? null : "Only the Engineer can start technical review.";
			case TECHNICAL_REVIEW:
				if (a.position() != Position.ENGINEER) {
					return "Only the Engineer can decide technical review.";
				}
				switch (to) {
					case BUDGET_REVIEW:
						p.technicallyCleared = true;
						return null;
					case MORE_INFO_REQUIRED:
					case REJECTED:
						return hasReason ? null : "A reason is required.";
					default:
						return "Invalid transition " + p.status + " -> " + to;
				}
			case BUDGET_REVIEW:
				if (to == CaseStatus.APPROVED) {
					if (a.position() != Position.MAYOR) {
						return "Final approval is given by the Municipal Executive (chaired by the Mayor).";
					}
					if (!p.technicallyCleared) {
						return "Cannot approve: technical review not cleared.";
					}
					if (!p.budgetCleared) {
						return "Cannot approve: the Accounts Officer has not cleared the budget.";
					}
					if (p.budgetLine.available() < p.estimate) {
						return "Cannot approve: insufficient funds in " + p.budgetLine.owner + ".";
					}
					return null;
				}
				if (to == CaseStatus.REJECTED || to == CaseStatus.DEFERRED) {
					if (a.position() != Position.MAYOR) {
						return "Only the Municipal Executive can reject or defer a project.";
					}
					return hasReason ? null : "A reason is required.";
				}
				if (to == CaseStatus.MORE_INFO_REQUIRED && a.position() == Position.ACCOUNTS_OFFICER) {
					return hasReason ? null : "A reason is required.";
				}
				return "Invalid transition " + p.status + " -> " + to;
			case MORE_INFO_REQUIRED:
				return wardChair && to == p.returnStatus ? null : "Only the Ward Chairperson can revise, back to " + p.returnStatus + ".";
			case DEFERRED:
				return wardChair && to == CaseStatus.SUBMITTED ? null : "Only the Ward Chairperson can resubmit a deferred project.";
			case APPROVED:
				return to == CaseStatus.PROCUREMENT && a.position() == Position.ADMIN_OFFICER
					? null : "Only the Administration Officer starts procurement.";
			case PROCUREMENT:
				if (to == CaseStatus.IN_PROGRESS && a.position() == Position.ADMIN_OFFICER) {
					return p.contractor == null ? "Assign a contractor first." : null;
				}
				return "Invalid transition " + p.status + " -> " + to;
			case IN_PROGRESS:
				return to == CaseStatus.INSPECTION && (a.position() == Position.CONTRACTOR || a.position() == Position.ENGINEER)
					? null : "Only the contractor or Engineer can request inspection.";
			case INSPECTION:
				if (a.position() != Position.INSPECTOR) {
					return "Only the Inspector can sign off.";
				}
				if (to == CaseStatus.COMPLETED) {
					return null;
				}
				if (to == CaseStatus.IN_PROGRESS) {
					return hasReason ? null : "A reason is required to send work back.";
				}
				return "Invalid transition " + p.status + " -> " + to;
			case COMPLETED:
				return to == CaseStatus.CLOSED && (wardChair || a.position() == Position.ADMIN_OFFICER)
					? null : "Only the Ward Chairperson or Administration Officer can close the project.";
			default:
				return "Project is " + p.status + "; no further action possible.";
		}
	}

	/** The Accounts Officer checks funds. Insufficient funds defer the project with a recorded reason. */
	public String budgetCheck(ProjectProposal p, Actor a, long now) {
		if (a.position() != Position.ACCOUNTS_OFFICER) {
			return "Only the Accounts Officer can check the budget.";
		}
		if (p.status != CaseStatus.BUDGET_REVIEW) {
			return "Project is not in budget review.";
		}
		if (p.budgetLine.available() < p.estimate) {
			AuditEntry entry = new AuditEntry(a.id(), a.name(), a.position(), "PROJECT_STATUS", p.id,
				p.status, CaseStatus.DEFERRED, now, "Insufficient budget in " + p.budgetLine.owner);
			p.status = CaseStatus.DEFERRED;
			p.history.add(entry);
			globalLog.add(entry);
			return "Insufficient budget: project deferred.";
		}
		p.budgetCleared = true;
		AuditEntry entry = new AuditEntry(a.id(), a.name(), a.position(), "BUDGET_CLEARED", p.id,
			p.status, p.status, now, "Funds available");
		p.history.add(entry);
		globalLog.add(entry);
		return null;
	}
}

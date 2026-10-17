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
		if (p.banned) {
			return "This project is banned: " + p.orderReason;
		}
		if (p.stopped) {
			return "Work is stopped by order: " + p.orderReason;
		}
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

	private static boolean workUnderway(CaseStatus s) {
		return s == CaseStatus.APPROVED || s == CaseStatus.PROCUREMENT
			|| s == CaseStatus.IN_PROGRESS || s == CaseStatus.INSPECTION;
	}

	private void record(ProjectProposal p, Actor a, String action, CaseStatus old, CaseStatus now2, long now, String reason) {
		AuditEntry entry = new AuditEntry(a.id(), a.name(), a.position(), action, p.id, old, now2, now, reason);
		p.history.add(entry);
		globalLog.add(entry);
	}

	/**
	 * Stop order: pauses approved work in the issuer's area. The Mayor and the
	 * Inspector may stop any project; a Ward Chairperson only in their own ward
	 * (urgent safety). A reason is mandatory.
	 */
	public String stop(ProjectProposal p, Actor a, String reason, long now) {
		boolean ownWardChair = a.position() == Position.WARD_CHAIR && Objects.equals(a.wardId(), p.wardId);
		if (a.position() != Position.MAYOR && a.position() != Position.INSPECTOR && !ownWardChair) {
			return "Only the Mayor, the Inspector or the ward's Chairperson can stop works.";
		}
		if (reason == null || reason.isBlank()) {
			return "A reason is required for a stop order.";
		}
		if (p.banned || p.stopped) {
			return "Project is already stopped or banned.";
		}
		if (!workUnderway(p.status)) {
			return "There is no approved work to stop.";
		}
		p.stopped = true;
		p.orderReason = reason;
		record(p, a, "STOP_ORDER", p.status, p.status, now, reason);
		return null;
	}

	/** Lifts a stop order. Only the Mayor or the Inspector; bans cannot be lifted. */
	public String lift(ProjectProposal p, Actor a, String reason, long now) {
		if (a.position() != Position.MAYOR && a.position() != Position.INSPECTOR) {
			return "Only the Mayor or the Inspector can lift a stop order.";
		}
		if (p.banned) {
			return "A banned project cannot be resumed.";
		}
		if (!p.stopped) {
			return "Project is not stopped.";
		}
		p.stopped = false;
		p.orderReason = null;
		record(p, a, "STOP_LIFTED", p.status, p.status, now, reason);
		return null;
	}

	/** Permanent ban by the Mayor. Releases committed funds; spent money is not refunded. */
	public String ban(ProjectProposal p, Actor a, String reason, long now) {
		if (a.position() != Position.MAYOR) {
			return "Only the Mayor can ban a project.";
		}
		if (reason == null || reason.isBlank()) {
			return "A reason is required for a ban.";
		}
		if (p.banned || p.status == CaseStatus.COMPLETED || p.status == CaseStatus.CLOSED) {
			return "Project is already banned or completed.";
		}
		CaseStatus old = p.status;
		if (workUnderway(old)) {
			p.budgetLine.committed -= p.estimate;
		}
		p.banned = true;
		p.stopped = false;
		p.orderReason = reason;
		p.status = CaseStatus.REJECTED;
		record(p, a, "BAN", old, CaseStatus.REJECTED, now, reason);
		return null;
	}

	/** The contractor or Engineer reports progress while work is underway. */
	public String setProgress(ProjectProposal p, Actor a, int percent, long now) {
		if (a.position() != Position.CONTRACTOR && a.position() != Position.ENGINEER) {
			return "Only the contractor or Engineer can report progress.";
		}
		if (p.banned || p.stopped) {
			return "Work is stopped: " + p.orderReason;
		}
		if (p.status != CaseStatus.IN_PROGRESS) {
			return "Project is not in progress.";
		}
		if (percent < 0 || percent > 99) {
			return "Progress must be 0-99; request inspection to complete.";
		}
		p.progressPercent = Math.max(p.progressPercent, percent);
		record(p, a, "PROGRESS", p.status, p.status, now, percent + "%");
		return null;
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
					if (!p.budgetLine.assemblyApproved) {
						return "Cannot approve: the budget line is not yet passed by the Municipal Assembly.";
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
		if (!p.budgetLine.assemblyApproved) {
			return "The budget line " + p.budgetLine.owner + " is not passed by the Municipal Assembly.";
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

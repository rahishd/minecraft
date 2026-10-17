package com.cydev.mod.government.project;

import com.cydev.mod.government.workflow.AuditEntry;
import com.cydev.mod.government.workflow.CaseStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A ward development proposal (road, drainage, streetlight, water point...). */
public class ProjectProposal {
	public final UUID id;
	public final int projectNo;
	public final UUID wardId;
	public String title;
	public String location;
	public int priority;
	public long estimate;
	public int beneficiaries;
	/** May be null only while loading; linked right after. */
	public BudgetLine budgetLine;

	public CaseStatus status = CaseStatus.DRAFT;
	public CaseStatus returnStatus;
	public boolean technicallyCleared;
	public boolean budgetCleared;
	public String contractor;
	public int progressPercent;
	/** Stop order: work is paused until lifted. */
	public boolean stopped;
	/** Ban: permanently prohibited. */
	public boolean banned;
	public String orderReason;
	public final List<AuditEntry> history = new ArrayList<>();

	public ProjectProposal(UUID id, int projectNo, UUID wardId, String title, String location, int priority,
						   long estimate, int beneficiaries, BudgetLine budgetLine) {
		this.id = id;
		this.projectNo = projectNo;
		this.wardId = wardId;
		this.title = title;
		this.location = location;
		this.priority = priority;
		this.estimate = estimate;
		this.beneficiaries = beneficiaries;
		this.budgetLine = budgetLine;
	}

	public ProjectProposal(int projectNo, UUID wardId, String title, String location, int priority,
						   long estimate, int beneficiaries, BudgetLine budgetLine) {
		this(UUID.randomUUID(), projectNo, wardId, title, location, priority, estimate, beneficiaries, budgetLine);
	}
}

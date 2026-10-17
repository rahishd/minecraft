package com.cydev.mod.government.workflow;

/** Status of a service case or project. Not every record uses every status. */
public enum CaseStatus {
	DRAFT,
	SUBMITTED,
	WARD_REVIEW,
	REFERRED,
	DEPARTMENT_REVIEW,
	MORE_INFO_REQUIRED,
	TECHNICAL_REVIEW,
	BUDGET_REVIEW,
	APPROVED,
	REJECTED,
	DEFERRED,
	PROCUREMENT,
	IN_PROGRESS,
	INSPECTION,
	ISSUED,
	COMPLETED,
	CLOSED
}

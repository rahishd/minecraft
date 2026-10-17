package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;

/** Citizen services with default authority. Defaults live in {@link AuthorityRules} and are configurable. */
public enum ServiceType {
	/** Simple ward-level services the ward may issue directly. */
	RESIDENCE_CERTIFICATE(true, Position.ADMIN_OFFICER),
	RECOMMENDATION_LETTER(true, Position.ADMIN_OFFICER),
	BIRTH_REGISTRATION(true, Position.ADMIN_OFFICER),
	/** Dispute resolution; handled by the Judicial Committee chaired by the Deputy Mayor. */
	DISPUTE_MEDIATION(false, Position.DEPUTY_MAYOR),
	/** Need municipal technical / revenue review. */
	BUILDING_PERMIT(false, Position.ENGINEER),
	WATER_PIPELINE_PERMIT(false, Position.ENGINEER),
	ROAD_REPAIR_COMPLAINT(false, Position.ENGINEER),
	BUSINESS_REGISTRATION(false, Position.REVENUE_OFFICER);

	public final boolean defaultWardCanIssue;
	public final Position defaultDepartment;

	ServiceType(boolean wardCanIssue, Position department) {
		this.defaultWardCanIssue = wardCanIssue;
		this.defaultDepartment = department;
	}
}

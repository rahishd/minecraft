package com.cydev.mod.government.council;

/**
 * Roles in the municipal / ward administration (gameplay abstraction of
 * Nepal's local government; not legally authoritative).
 */
public enum Position {
	RESIDENT(Level.NONE, false),
	/** Elected political leader; chairs the Municipal Executive. */
	MAYOR(Level.MUNICIPAL, false),
	/** Elected; oversight / dispute resolution. Not a universal approver. */
	DEPUTY_MAYOR(Level.MUNICIPAL, false),
	/** Administrative lead. Not elected. */
	CAO(Level.MUNICIPAL, true),
	ADMIN_OFFICER(Level.MUNICIPAL, true),
	ENGINEER(Level.MUNICIPAL, true),
	REVENUE_OFFICER(Level.MUNICIPAL, true),
	ACCOUNTS_OFFICER(Level.MUNICIPAL, true),
	INSPECTOR(Level.MUNICIPAL, true),
	/** Elected head of a ward. */
	WARD_CHAIR(Level.WARD, false),
	WARD_SECRETARY(Level.WARD, true),
	COMMITTEE_MEMBER(Level.WARD, true),
	CONTRACTOR(Level.MUNICIPAL, true);

	public enum Level { NONE, MUNICIPAL, WARD }

	private final Level level;
	private final boolean appointable;

	Position(Level level, boolean appointable) {
		this.level = level;
		this.appointable = appointable;
	}

	public Level level() {
		return level;
	}

	/** True for staff posts that are appointed rather than elected. */
	public boolean appointable() {
		return appointable;
	}
}

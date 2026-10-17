package com.cydev.mod.government.mayor;

import java.util.concurrent.TimeUnit;

/** Fixed rules about the mayor's term and election phases (real-life time). */
public final class MayorTerm {
	/** 24 x 30 days = 1 real-life month. */
	public static final long TERM_MS = TimeUnit.DAYS.toMillis(30);
	public static final long NOMINATION_MS = TimeUnit.DAYS.toMillis(2);
	public static final long VOTING_MS = TimeUnit.DAYS.toMillis(2);

	private MayorTerm() {
	}
}

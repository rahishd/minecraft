package com.cydev.mod.government.project;

import java.util.UUID;

/** Municipal budget allocation. Wards have no independent budget; they draw on municipal lines. */
public class BudgetLine {
	public final UUID id = UUID.randomUUID();
	public final int fiscalYear;
	/** Department or fund name, e.g. "Roads & Drainage". */
	public final String owner;
	public long allocated;
	public long committed;
	public long spent;
	public String fundingSource;

	public BudgetLine(int fiscalYear, String owner, long allocated, String fundingSource) {
		this.fiscalYear = fiscalYear;
		this.owner = owner;
		this.allocated = allocated;
		this.fundingSource = fundingSource;
	}

	public long available() {
		return allocated - committed - spent;
	}
}

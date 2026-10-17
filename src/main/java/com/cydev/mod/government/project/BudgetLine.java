package com.cydev.mod.government.project;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Municipal budget allocation. Wards have no independent budget; they draw on municipal lines. */
public class BudgetLine {
	public final UUID id;
	public final int lineNo;
	public final int fiscalYear;
	/** Department or fund name, e.g. "Roads & Drainage". */
	public final String owner;
	public long allocated;
	public long committed;
	public long spent;
	public String fundingSource;
	/** Set once the Municipal Assembly passes this line. Projects cannot be funded before. */
	public boolean assemblyApproved;
	public final List<UUID> assemblyYes = new ArrayList<>();
	public final List<UUID> assemblyNo = new ArrayList<>();

	public BudgetLine(UUID id, int lineNo, int fiscalYear, String owner, long allocated, String fundingSource) {
		this.id = id;
		this.lineNo = lineNo;
		this.fiscalYear = fiscalYear;
		this.owner = owner;
		this.allocated = allocated;
		this.fundingSource = fundingSource;
	}

	public BudgetLine(int lineNo, int fiscalYear, String owner, long allocated, String fundingSource) {
		this(UUID.randomUUID(), lineNo, fiscalYear, owner, allocated, fundingSource);
	}

	public long available() {
		return allocated - committed - spent;
	}
}

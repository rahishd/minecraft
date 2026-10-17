package com.cydev.mod.sim.city;

import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.project.ProjectService;
import com.cydev.mod.government.workflow.AuditEntry;
import com.cydev.mod.government.workflow.AuthorityRules;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.government.workflow.WorkflowService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything the administration of one city has recorded: cases, projects, budget and audit log. */
public class CityRecords {
	public final UUID cityId;
	public int nextCaseNo = 1;
	public int nextProjectNo = 1;
	public int nextBudgetNo = 1;
	public final List<BudgetLine> budgetLines = new ArrayList<>();
	public final List<ServiceCase> cases = new ArrayList<>();
	public final List<ProjectProposal> projects = new ArrayList<>();
	public final List<AuditEntry> auditLog = new ArrayList<>();
	/** Service authority rules. Defaults; not persisted yet. */
	public final AuthorityRules rules = new AuthorityRules();

	public CityRecords(UUID cityId) {
		this.cityId = cityId;
	}

	public WorkflowService workflow() {
		return new WorkflowService(rules, auditLog);
	}

	public ProjectService projectService() {
		return new ProjectService(auditLog);
	}

	public ServiceCase findCase(int no) {
		for (ServiceCase c : cases) {
			if (c.caseNo == no) {
				return c;
			}
		}
		return null;
	}

	public ProjectProposal findProject(int no) {
		for (ProjectProposal p : projects) {
			if (p.projectNo == no) {
				return p;
			}
		}
		return null;
	}

	public BudgetLine findLine(int no) {
		for (BudgetLine b : budgetLines) {
			if (b.lineNo == no) {
				return b;
			}
		}
		return null;
	}

	public BudgetLine addLine(int fiscalYear, String owner, long amount, String source) {
		BudgetLine line = new BudgetLine(nextBudgetNo++, fiscalYear, owner, amount, source);
		budgetLines.add(line);
		return line;
	}
}

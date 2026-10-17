package com.cydev.mod.government.mayor;

import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.workflow.CaseStatus;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.sim.city.CityRecords;
import java.util.concurrent.TimeUnit;

/**
 * Public performance score of the administration (shown for the Mayor's term).
 * Built only from outcomes: resident ratings, resolved cases, timeliness,
 * delivered projects. There is no input for shortcuts, so they cannot raise it.
 */
public final class ScoreService {
	/** A case still open after this long counts as overdue. */
	public static final long CASE_SLA_MS = TimeUnit.DAYS.toMillis(7);

	private ScoreService() {
	}

	public record Score(int overall, int satisfaction, int resolvedPercent, int timelinessPercent,
						int deliveryPercent, int budgetUsedPercent, int casesTotal, int casesOpen,
						int casesOverdue, int projectsCompleted, int projectsActive, double avgRating) {
	}

	public static Score compute(CityRecords r, long now) {
		int total = r.cases.size();
		int ended = 0;
		int open = 0;
		int overdue = 0;
		int ratings = 0;
		int ratingSum = 0;
		for (ServiceCase c : r.cases) {
			if (c.status == CaseStatus.DRAFT) {
				total--;
				continue;
			}
			if (c.isEnded() && c.status != CaseStatus.REJECTED) {
				ended++;
			}
			if (!c.isEnded() && c.status != CaseStatus.CLOSED) {
				open++;
				if (now - c.created > CASE_SLA_MS) {
					overdue++;
				}
			}
			if (c.rating > 0) {
				ratings++;
				ratingSum += c.rating;
			}
		}
		int completed = 0;
		int active = 0;
		int failed = 0;
		for (ProjectProposal p : r.projects) {
			switch (p.status) {
				case COMPLETED, CLOSED -> completed++;
				case APPROVED, PROCUREMENT, IN_PROGRESS, INSPECTION -> active++;
				case REJECTED -> failed++;
				default -> {
				}
			}
		}
		long allocated = 0;
		long spent = 0;
		for (BudgetLine b : r.budgetLines) {
			if (b.assemblyApproved) {
				allocated += b.allocated;
				spent += b.spent;
			}
		}

		double avg = ratings == 0 ? 0 : (double) ratingSum / ratings;
		int satisfaction = ratings == 0 ? 50 : (int) Math.round((avg - 1) / 4 * 100);
		int resolved = total == 0 ? 50 : percent(ended, total);
		int timeliness = open == 0 ? 100 : percent(open - overdue, open);
		int delivery = completed + failed == 0 ? 50 : percent(completed, completed + failed);
		int budgetUsed = allocated == 0 ? 0 : percent(spent, allocated);

		int overall = (int) Math.round(satisfaction * 0.35 + resolved * 0.25 + timeliness * 0.20 + delivery * 0.20);
		return new Score(overall, satisfaction, resolved, timeliness, delivery, budgetUsed,
			total, open, overdue, completed, active, avg);
	}

	private static int percent(long part, long whole) {
		return (int) Math.max(0, Math.min(100, Math.round(100.0 * part / whole)));
	}
}

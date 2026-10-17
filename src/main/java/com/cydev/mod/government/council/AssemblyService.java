package com.cydev.mod.government.council;

import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Ward;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Municipal Assembly: the Mayor, Deputy Mayor and all Ward Chairpersons
 * (a simplification of the real assembly). A budget line becomes usable once a
 * majority of members vote yes.
 */
public final class AssemblyService {
	private AssemblyService() {
	}

	public static Set<UUID> members(City city) {
		Set<UUID> m = new LinkedHashSet<>();
		if (city.mayorId != null) {
			m.add(city.mayorId);
		}
		if (city.deputyId != null) {
			m.add(city.deputyId);
		}
		for (Ward w : city.wards) {
			if (w.chairId != null) {
				m.add(w.chairId);
			}
		}
		return m;
	}

	/** Returns an error message, or null on success. */
	public static String vote(City city, BudgetLine line, UUID voter, boolean yes) {
		Set<UUID> members = members(city);
		if (!members.contains(voter)) {
			return "Only Assembly members (Mayor, Deputy Mayor, Ward Chairpersons) can vote.";
		}
		if (line.assemblyApproved) {
			return "This budget line is already approved.";
		}
		if (line.assemblyYes.contains(voter) || line.assemblyNo.contains(voter)) {
			return "You have already voted on this budget line.";
		}
		(yes ? line.assemblyYes : line.assemblyNo).add(voter);
		if (line.assemblyYes.size() * 2 > members.size()) {
			line.assemblyApproved = true;
		}
		return null;
	}
}

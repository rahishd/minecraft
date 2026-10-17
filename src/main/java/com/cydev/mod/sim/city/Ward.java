package com.cydev.mod.sim.city;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.election.Candidate;
import com.cydev.mod.government.election.ElectedOffice;
import com.cydev.mod.government.election.Election;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A ward: a local service office inside one municipality. Not a separate
 * government and has no independent municipal budget.
 */
public class Ward implements ElectedOffice {
	public final UUID id;
	public final int number;
	public String name;
	public int population;

	public UUID chairId;
	public String chairName;
	public long termStart;
	public long termEnd;

	public Election election;
	/** Ward secretary and committee members. */
	public final List<Appointment> appointments = new ArrayList<>();

	public Ward(UUID id, int number, String name) {
		this.id = id;
		this.number = number;
		this.name = name;
	}

	@Override
	public String officeName() {
		return "Ward " + number + " Chairperson";
	}

	@Override
	public boolean hasHolder() {
		return chairId != null;
	}

	@Override
	public boolean isHolder(UUID playerId) {
		return chairId != null && chairId.equals(playerId);
	}

	@Override
	public long termEnd() {
		return termEnd;
	}

	@Override
	public Election election() {
		return election;
	}

	@Override
	public void setElection(Election election) {
		this.election = election;
	}

	@Override
	public void install(Candidate winner, Candidate runnerUp, long start, long end) {
		chairId = winner.playerId();
		chairName = winner.name();
		termStart = start;
		termEnd = end;
		appointments.clear();
	}
}

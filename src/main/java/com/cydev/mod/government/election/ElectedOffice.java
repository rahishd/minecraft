package com.cydev.mod.government.election;

import java.util.UUID;

/** An office filled by election: the municipality's mayor or a ward chair. */
public interface ElectedOffice {
	String officeName();

	boolean hasHolder();

	boolean isHolder(UUID playerId);

	long termEnd();

	Election election();

	void setElection(Election election);

	/** Seats the winner. runnerUp may be null (only the mayoralty uses it for the deputy). */
	void install(Candidate winner, Candidate runnerUp, long start, long end);
}

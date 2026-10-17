package com.cydev.mod.government.election;

public enum ElectionState {
	/** Anyone can file candidacy. */
	NOMINATION,
	/** Candidacy closed; residents vote. */
	VOTING,
	/** Finished; winner (if any) has taken office. */
	CLOSED
}

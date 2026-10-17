package com.cydev.mod.government.election;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One election for one city. Times are real-life epoch milliseconds. */
public class Election {
	public ElectionState state = ElectionState.NOMINATION;
	public long nominationEnd;
	public long votingEnd;
	public final List<Candidate> candidates = new ArrayList<>();
	/** voter -> candidate; one vote per voter. */
	public final Map<UUID, UUID> votes = new LinkedHashMap<>();

	public Candidate candidate(UUID playerId) {
		for (Candidate c : candidates) {
			if (c.playerId().equals(playerId)) {
				return c;
			}
		}
		return null;
	}

	public int votesFor(UUID playerId) {
		int n = 0;
		for (UUID v : votes.values()) {
			if (v.equals(playerId)) {
				n++;
			}
		}
		return n;
	}
}

package com.cydev.mod.government.election;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Role;
import com.cydev.mod.government.mayor.MayorTerm;
import com.cydev.mod.sim.city.City;
import java.util.UUID;

/** Election rules. Methods return an error message, or null on success. */
public final class ElectionService {
	private ElectionService() {
	}

	public static boolean isActive(City city) {
		return city.election != null && city.election.state != ElectionState.CLOSED;
	}

	public static String start(City city, long now, boolean force) {
		if (isActive(city)) {
			return "An election is already running in " + city.name + ".";
		}
		if (!force && city.hasMayor() && now < city.termEnd) {
			return "The current mayor's term has not ended yet.";
		}
		Election e = new Election();
		e.nominationEnd = now + MayorTerm.NOMINATION_MS;
		e.votingEnd = e.nominationEnd + MayorTerm.VOTING_MS;
		city.election = e;
		return null;
	}

	public static String addCandidate(City city, UUID id, String name, String slogan) {
		if (!isActive(city) || city.election.state != ElectionState.NOMINATION) {
			return "Candidacy is not open in " + city.name + ".";
		}
		if (city.election.candidate(id) != null) {
			return "You are already a candidate.";
		}
		city.election.candidates.add(new Candidate(id, name, slogan));
		return null;
	}

	public static String vote(City city, UUID voter, UUID candidateId) {
		if (!isActive(city) || city.election.state != ElectionState.VOTING) {
			return "Voting is not open in " + city.name + ".";
		}
		if (city.election.candidate(candidateId) == null) {
			return "That player is not a candidate.";
		}
		if (city.election.votes.containsKey(voter)) {
			return "You have already voted.";
		}
		city.election.votes.put(voter, candidateId);
		return null;
	}

	/**
	 * Advances the election phases by real time. Returns a message to announce
	 * when something happened, otherwise null.
	 */
	public static String tick(City city, long now) {
		Election e = city.election;
		if (e == null || e.state == ElectionState.CLOSED) {
			return null;
		}
		if (e.state == ElectionState.NOMINATION && now >= e.nominationEnd) {
			if (e.candidates.isEmpty()) {
				e.state = ElectionState.CLOSED;
				return "Election in " + city.name + " cancelled: no candidates.";
			}
			e.state = ElectionState.VOTING;
			return "Voting is now open in " + city.name + "!";
		}
		if (e.state == ElectionState.VOTING && now >= e.votingEnd) {
			return close(city, now);
		}
		return null;
	}

	/** Ends voting now and installs the winner. Ties go to the earlier candidate. */
	public static String close(City city, long now) {
		Election e = city.election;
		e.state = ElectionState.CLOSED;
		Candidate winner = null;
		int best = 0;
		for (Candidate c : e.candidates) {
			int v = e.votesFor(c.playerId());
			if (v > best) {
				best = v;
				winner = c;
			}
		}
		if (winner == null) {
			return "Election in " + city.name + " ended with no votes; no new mayor.";
		}
		city.mayorId = winner.playerId();
		city.mayorName = winner.name();
		city.termStart = now;
		city.termEnd = now + MayorTerm.TERM_MS;
		city.appointments.clear();
		return winner.name() + " won the election and is the new Mayor of " + city.name
			+ " with " + best + " vote(s).";
	}

	public static String appoint(City city, UUID mayor, UUID target, String targetName, Role role) {
		if (!city.isMayor(mayor)) {
			return "Only the Mayor of " + city.name + " can appoint staff.";
		}
		city.appointments.removeIf(a -> a.playerId().equals(target));
		city.appointments.add(new Appointment(target, targetName, role));
		return null;
	}
}

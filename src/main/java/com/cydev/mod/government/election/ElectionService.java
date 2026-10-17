package com.cydev.mod.government.election;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.mayor.MayorTerm;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Ward;
import java.util.UUID;

/** Election and appointment rules. Methods return an error message, or null on success. */
public final class ElectionService {
	private ElectionService() {
	}

	public static boolean isActive(ElectedOffice office) {
		Election e = office.election();
		return e != null && e.state != ElectionState.CLOSED;
	}

	public static String start(ElectedOffice office, long now, boolean force) {
		if (isActive(office)) {
			return "An election is already running for " + office.officeName() + ".";
		}
		if (!force && office.hasHolder() && now < office.termEnd()) {
			return "The current term has not ended yet.";
		}
		Election e = new Election();
		e.nominationEnd = now + MayorTerm.NOMINATION_MS;
		e.votingEnd = e.nominationEnd + MayorTerm.VOTING_MS;
		office.setElection(e);
		return null;
	}

	public static String addCandidate(ElectedOffice office, UUID id, String name, String slogan) {
		if (!isActive(office) || office.election().state != ElectionState.NOMINATION) {
			return "Candidacy is not open for " + office.officeName() + ".";
		}
		if (office.election().candidate(id) != null) {
			return "You are already a candidate.";
		}
		office.election().candidates.add(new Candidate(id, name, slogan));
		return null;
	}

	public static String vote(ElectedOffice office, UUID voter, UUID candidateId) {
		if (!isActive(office) || office.election().state != ElectionState.VOTING) {
			return "Voting is not open for " + office.officeName() + ".";
		}
		if (office.election().candidate(candidateId) == null) {
			return "That player is not a candidate.";
		}
		if (office.election().votes.containsKey(voter)) {
			return "You have already voted.";
		}
		office.election().votes.put(voter, candidateId);
		return null;
	}

	/** Advances the phases by real time. Returns an announcement, or null. */
	public static String tick(ElectedOffice office, long now) {
		Election e = office.election();
		if (e == null || e.state == ElectionState.CLOSED) {
			return null;
		}
		if (e.state == ElectionState.NOMINATION && now >= e.nominationEnd) {
			if (e.candidates.isEmpty()) {
				e.state = ElectionState.CLOSED;
				return "Election for " + office.officeName() + " cancelled: no candidates.";
			}
			e.state = ElectionState.VOTING;
			return "Voting is now open for " + office.officeName() + "!";
		}
		if (e.state == ElectionState.VOTING && now >= e.votingEnd) {
			return close(office, now);
		}
		return null;
	}

	/**
	 * Ends the election now and seats the winner. Ties go to the earlier
	 * candidate. For the mayoralty the runner-up (with votes) becomes Deputy
	 * Mayor - a gameplay simplification, configurable later.
	 */
	public static String close(ElectedOffice office, long now) {
		Election e = office.election();
		e.state = ElectionState.CLOSED;
		Candidate winner = null;
		Candidate second = null;
		int best = 0;
		int secondBest = 0;
		for (Candidate c : e.candidates) {
			int v = e.votesFor(c.playerId());
			if (v > best) {
				second = winner;
				secondBest = best;
				winner = c;
				best = v;
			} else if (v > secondBest) {
				second = c;
				secondBest = v;
			}
		}
		if (winner == null) {
			return "Election for " + office.officeName() + " ended with no votes; the seat stays empty.";
		}
		office.install(winner, second, now, now + MayorTerm.TERM_MS);
		return winner.name() + " won the election for " + office.officeName() + " with " + best + " vote(s).";
	}

	/** Mayor appoints municipal staff (CAO, department officers, inspector). */
	public static String appointMunicipal(City city, UUID mayorId, UUID target, String targetName, Position position) {
		if (!city.isMayor(mayorId)) {
			return "Only the Mayor of " + city.name + " can appoint municipal staff.";
		}
		if (!position.appointable() || position.level() != Position.Level.MUNICIPAL) {
			return position + " is not a municipal staff post.";
		}
		city.appointments.removeIf(a -> a.playerId().equals(target) || a.position() == position && position == Position.CAO);
		city.appointments.add(new Appointment(target, targetName, position));
		return null;
	}

	/** Ward Chairperson appoints ward secretary and committee members. */
	public static String appointWard(Ward ward, UUID chairId, UUID target, String targetName, Position position) {
		if (!ward.isHolder(chairId)) {
			return "Only the Ward " + ward.number + " Chairperson can appoint ward staff.";
		}
		if (!position.appointable() || position.level() != Position.Level.WARD) {
			return position + " is not a ward staff post.";
		}
		ward.appointments.removeIf(a -> a.playerId().equals(target) || a.position() == position && position == Position.WARD_SECRETARY);
		ward.appointments.add(new Appointment(target, targetName, position));
		return null;
	}
}

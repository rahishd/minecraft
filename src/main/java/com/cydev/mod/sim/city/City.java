package com.cydev.mod.sim.city;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.election.Candidate;
import com.cydev.mod.government.election.ElectedOffice;
import com.cydev.mod.government.election.Election;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure-Java municipality record. No Minecraft types so it stays testable. */
public class City implements ElectedOffice {
	public final UUID id;
	public String name;
	public long treasury;

	public UUID mayorId;
	public String mayorName;
	public UUID deputyId;
	public String deputyName;
	/** Real-life epoch ms; 0 when there is no mayor. */
	public long termStart;
	public long termEnd;

	public Election election;
	/** Municipal staff: CAO, engineers, accounts, etc. */
	public final List<Appointment> appointments = new ArrayList<>();
	public final List<Ward> wards = new ArrayList<>();

	public City(UUID id, String name) {
		this.id = id;
		this.name = name;
	}

	public boolean hasMayor() {
		return mayorId != null;
	}

	public boolean isMayor(UUID playerId) {
		return mayorId != null && mayorId.equals(playerId);
	}

	public Ward ward(int number) {
		for (Ward w : wards) {
			if (w.number == number) {
				return w;
			}
		}
		return null;
	}

	public Ward wardById(UUID wardId) {
		for (Ward w : wards) {
			if (w.id.equals(wardId)) {
				return w;
			}
		}
		return null;
	}

	@Override
	public String officeName() {
		return "Mayor of " + name;
	}

	@Override
	public boolean hasHolder() {
		return hasMayor();
	}

	@Override
	public boolean isHolder(UUID playerId) {
		return isMayor(playerId);
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
		mayorId = winner.playerId();
		mayorName = winner.name();
		deputyId = runnerUp == null ? null : runnerUp.playerId();
		deputyName = runnerUp == null ? null : runnerUp.name();
		termStart = start;
		termEnd = end;
		appointments.clear();
	}
}

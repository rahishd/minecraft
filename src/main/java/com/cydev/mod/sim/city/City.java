package com.cydev.mod.sim.city;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.election.Election;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure-Java city record. No Minecraft types so it stays testable. */
public class City {
	public final UUID id;
	public String name;
	public long treasury;

	public UUID mayorId;
	public String mayorName;
	/** Real-life epoch ms; 0 when there is no mayor. */
	public long termStart;
	public long termEnd;

	public Election election;
	public final List<Appointment> appointments = new ArrayList<>();

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
}

package com.cydev.mod.expansion.federation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A union of cities whose mayors coordinate expansion and shared offices. */
public class Federation {
	/** A pending request to add a city. */
	public static class Proposal {
		public final UUID cityId;
		/** Mayors (of member cities) who voted yes. */
		public final List<UUID> yes = new ArrayList<>();
		/** The proposed city's own mayor must also accept. */
		public boolean accepted;

		public Proposal(UUID cityId) {
			this.cityId = cityId;
		}
	}

	public final UUID id;
	public final String name;
	public final List<UUID> members = new ArrayList<>();
	public final List<Proposal> proposals = new ArrayList<>();

	public Federation(UUID id, String name) {
		this.id = id;
		this.name = name;
	}

	public Proposal proposal(UUID cityId) {
		for (Proposal p : proposals) {
			if (p.cityId.equals(cityId)) {
				return p;
			}
		}
		return null;
	}
}

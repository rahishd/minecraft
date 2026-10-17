package com.cydev.mod.government.council;

import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Ward;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Works out which official roles a player holds in a city. */
public final class ActorResolver {
	private ActorResolver() {
	}

	/**
	 * Every role the player holds, most relevant first. A resident actor is
	 * always last so ordinary citizens can still file and track cases.
	 * wardId is the ward the action concerns (for ward staff checks); may be null.
	 */
	public static List<Actor> rolesOf(City city, UUID playerId, String name, UUID wardId) {
		List<Actor> out = new ArrayList<>();
		List<Ward> ordered = new ArrayList<>(city.wards);
		// Roles in the ward being acted on come first.
		ordered.sort((x, y) -> Boolean.compare(!x.id.equals(wardId), !y.id.equals(wardId)));
		for (Ward w : ordered) {
			if (w.isHolder(playerId)) {
				out.add(new Actor(playerId, name, Position.WARD_CHAIR, w.id));
			}
			for (Appointment a : w.appointments) {
				if (a.playerId().equals(playerId)) {
					out.add(new Actor(playerId, name, a.position(), w.id));
				}
			}
		}
		if (city.isMayor(playerId)) {
			out.add(new Actor(playerId, name, Position.MAYOR, null));
		}
		if (city.deputyId != null && city.deputyId.equals(playerId)) {
			out.add(new Actor(playerId, name, Position.DEPUTY_MAYOR, null));
		}
		for (Appointment a : city.appointments) {
			if (a.playerId().equals(playerId)) {
				out.add(new Actor(playerId, name, a.position(), null));
			}
		}
		out.add(new Actor(playerId, name, Position.RESIDENT, wardId));
		return out;
	}
}

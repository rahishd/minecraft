package com.cydev.mod.world.persistence;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Role;
import com.cydev.mod.government.election.Candidate;
import com.cydev.mod.government.election.Election;
import com.cydev.mod.government.election.ElectionState;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

/** Saves the city registry with the world (data/cydev_cities.dat). */
public class CityData extends PersistentState {
	private static final Codec<Candidate> CANDIDATE_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(Candidate::playerId),
		Codec.STRING.fieldOf("name").forGetter(Candidate::name),
		Codec.STRING.fieldOf("slogan").forGetter(Candidate::slogan)
	).apply(i, Candidate::new));

	private static final Codec<Election> ELECTION_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("state").forGetter(e -> e.state.name()),
		Codec.LONG.fieldOf("nominationEnd").forGetter(e -> e.nominationEnd),
		Codec.LONG.fieldOf("votingEnd").forGetter(e -> e.votingEnd),
		CANDIDATE_CODEC.listOf().fieldOf("candidates").forGetter(e -> e.candidates),
		Codec.unboundedMap(Uuids.STRING_CODEC, Uuids.STRING_CODEC).fieldOf("votes").forGetter(e -> e.votes)
	).apply(i, (state, nomEnd, voteEnd, candidates, votes) -> {
		Election e = new Election();
		e.state = ElectionState.valueOf(state);
		e.nominationEnd = nomEnd;
		e.votingEnd = voteEnd;
		e.candidates.addAll(candidates);
		e.votes.putAll(votes);
		return e;
	}));

	private static final Codec<Appointment> APPOINTMENT_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(Appointment::playerId),
		Codec.STRING.fieldOf("name").forGetter(Appointment::name),
		Codec.STRING.fieldOf("role").forGetter(a -> a.role().name())
	).apply(i, (id, name, role) -> new Appointment(id, name, Role.valueOf(role))));

	private static final Codec<City> CITY_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(c -> c.id),
		Codec.STRING.fieldOf("name").forGetter(c -> c.name),
		Codec.LONG.fieldOf("treasury").forGetter(c -> c.treasury),
		Uuids.CODEC.optionalFieldOf("mayor").forGetter(c -> Optional.ofNullable(c.mayorId)),
		Codec.STRING.optionalFieldOf("mayorName").forGetter(c -> Optional.ofNullable(c.mayorName)),
		Codec.LONG.optionalFieldOf("termStart", 0L).forGetter(c -> c.termStart),
		Codec.LONG.optionalFieldOf("termEnd", 0L).forGetter(c -> c.termEnd),
		ELECTION_CODEC.optionalFieldOf("election").forGetter(c -> Optional.ofNullable(c.election)),
		APPOINTMENT_CODEC.listOf().optionalFieldOf("appointments", List.of()).forGetter(c -> c.appointments)
	).apply(i, (id, name, treasury, mayor, mayorName, termStart, termEnd, election, appointments) -> {
		City city = new City(id, name);
		city.treasury = treasury;
		city.mayorId = mayor.orElse(null);
		city.mayorName = mayorName.orElse(null);
		city.termStart = termStart;
		city.termEnd = termEnd;
		city.election = election.orElse(null);
		city.appointments.addAll(appointments);
		return city;
	}));

	private static final Codec<CityData> CODEC = CITY_CODEC.listOf()
		.xmap(list -> {
			CityData data = new CityData();
			list.forEach(c -> data.registry.all().put(c.id, c));
			return data;
		}, data -> new ArrayList<>(data.registry.all().values()))
		.fieldOf("cities").codec();

	private static final PersistentStateType<CityData> TYPE =
		new PersistentStateType<>(CydevMod.MOD_ID + "_cities", CityData::new, CODEC, null);

	private final CityRegistry registry = new CityRegistry();

	public CityRegistry registry() {
		return registry;
	}

	public City findByName(String name) {
		for (City c : registry.all().values()) {
			if (c.name.equalsIgnoreCase(name)) {
				return c;
			}
		}
		return null;
	}

	public static CityData get(MinecraftServer server) {
		return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
	}
}

package com.cydev.mod.world.persistence;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

/** Saves the city registry with the world (data/cydev_cities.dat). */
public class CityData extends PersistentState {
	private static final Codec<City> CITY_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(c -> c.id),
		Codec.STRING.fieldOf("name").forGetter(c -> c.name),
		Uuids.CODEC.optionalFieldOf("mayor").forGetter(c -> Optional.ofNullable(c.mayorId)),
		Codec.LONG.fieldOf("treasury").forGetter(c -> c.treasury)
	).apply(i, (id, name, mayor, treasury) -> {
		City city = new City(id, name);
		city.mayorId = mayor.orElse(null);
		city.treasury = treasury;
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

	public static CityData get(MinecraftServer server) {
		return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
	}
}

package com.cydev.mod.world.persistence;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.zone.ZoneMap;
import com.cydev.mod.sim.zone.ZonePlot;
import com.cydev.mod.sim.zone.ZoneRegistry;
import com.cydev.mod.sim.zone.ZoneType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

/** Saves the zoning plots of every city (data/cydev_zones.dat). */
public class ZoneData extends PersistentState {
	private static final Codec<ZonePlot> PLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(ZonePlot::id),
		Codec.INT.fieldOf("minX").forGetter(p -> p.area().minX()),
		Codec.INT.fieldOf("minZ").forGetter(p -> p.area().minZ()),
		Codec.INT.fieldOf("maxX").forGetter(p -> p.area().maxX()),
		Codec.INT.fieldOf("maxZ").forGetter(p -> p.area().maxZ()),
		Codec.STRING.fieldOf("type").forGetter(p -> p.type().name()),
		Uuids.CODEC.fieldOf("by").forGetter(ZonePlot::zonedBy),
		Codec.LONG.fieldOf("at").forGetter(ZonePlot::zonedAt)
	).apply(i, (id, x1, z1, x2, z2, type, by, at) ->
		new ZonePlot(id, new Rect(x1, z1, x2, z2), ZoneType.valueOf(type), by, at)));

	private static final Codec<ZoneMap> MAP_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("city").forGetter(m -> m.cityId),
		PLOT_CODEC.listOf().fieldOf("plots").forGetter(m -> m.plots)
	).apply(i, (city, plots) -> {
		ZoneMap m = new ZoneMap(city);
		m.plots.addAll(plots);
		return m;
	}));

	private static final Codec<ZoneData> CODEC = MAP_CODEC.listOf()
		.xmap(list -> {
			ZoneData data = new ZoneData();
			list.forEach(m -> data.registry.all().put(m.cityId, m));
			return data;
		}, data -> new ArrayList<>(data.registry.all().values()))
		.fieldOf("zones").codec();

	private static final PersistentStateType<ZoneData> TYPE =
		new PersistentStateType<>(CydevMod.MOD_ID + "_zones", ZoneData::new, CODEC, null);

	private final ZoneRegistry registry = new ZoneRegistry();

	public ZoneRegistry registry() {
		return registry;
	}

	public static ZoneData get(MinecraftServer server) {
		return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
	}
}

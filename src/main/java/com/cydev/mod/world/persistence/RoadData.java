package com.cydev.mod.world.persistence;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.network.road.RoadKind;
import com.cydev.mod.network.road.RoadNetwork;
import com.cydev.mod.network.road.RoadPos;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

/**
 * Saves the road network (data/cydev_roads.dat) so it is ready after a
 * restart without scanning chunks. Each kind maps to a list of packed block
 * positions.
 */
public class RoadData extends PersistentState {
	private static final Codec<RoadData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())
		.xmap(byKind -> {
			RoadData data = new RoadData();
			byKind.forEach((name, packed) -> {
				RoadKind kind = RoadKind.parse(name);
				if (kind != null) {
					packed.forEach(l -> data.network.add(RoadPos.unpack(l), kind));
				}
			});
			return data;
		}, data -> {
			Map<RoadKind, List<Long>> grouped = new EnumMap<>(RoadKind.class);
			data.network.tiles().forEach((pos, kind) ->
				grouped.computeIfAbsent(kind, k -> new ArrayList<>()).add(pos.pack()));
			Map<String, List<Long>> out = new java.util.TreeMap<>();
			grouped.forEach((kind, packed) -> out.put(kind.name(), packed));
			return out;
		})
		.fieldOf("tiles").codec();

	private static final PersistentStateType<RoadData> TYPE =
		new PersistentStateType<>(CydevMod.MOD_ID + "_roads", RoadData::new, CODEC, null);

	private final RoadNetwork network = new RoadNetwork();

	public RoadNetwork network() {
		return network;
	}

	public static RoadData get(MinecraftServer server) {
		return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
	}
}

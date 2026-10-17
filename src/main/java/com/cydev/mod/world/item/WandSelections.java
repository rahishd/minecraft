package com.cydev.mod.world.item;

import com.cydev.mod.expansion.newcity.ExpansionService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.zone.ZonePlot;
import com.cydev.mod.sim.zone.ZoneType;
import com.cydev.mod.sim.zone.ZoningService;
import com.cydev.mod.world.persistence.CityData;
import com.cydev.mod.world.persistence.ZoneData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * What each player has selected with the Zoning Wand. Kept in memory only: a
 * selection is a few clicks and is not worth saving across restarts.
 */
public final class WandSelections {
	public static final class Selection {
		public BlockPos cornerA;
		public BlockPos cornerB;
		public ZoneType type = ZoneType.RESIDENTIAL;

		/** The selected x/z rectangle, or null until both corners are set. */
		public Rect area() {
			if (cornerA == null || cornerB == null) {
				return null;
			}
			return Rect.of(cornerA.getX(), cornerA.getZ(), cornerB.getX(), cornerB.getZ());
		}
	}

	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

	private WandSelections() {
	}

	public static Selection of(UUID playerId) {
		return SELECTIONS.computeIfAbsent(playerId, id -> new Selection());
	}

	public static void forget(UUID playerId) {
		SELECTIONS.remove(playerId);
	}

	public static void setCorner(ServerPlayerEntity player, BlockPos pos, boolean first) {
		Selection s = of(player.getUuid());
		if (first) {
			s.cornerA = pos.toImmutable();
		} else {
			s.cornerB = pos.toImmutable();
		}
		if (player.getEntityWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				12, 0.3, 0.2, 0.3, 0.0);
		}
		Rect area = s.area();
		player.sendMessage(Text.literal((first ? "Corner 1" : "Corner 2") + " set at x=" + pos.getX() + " z=" + pos.getZ()
			+ (area == null ? "" : " - selection " + width(area) + " x " + depth(area) + " blocks")
			+ " - zone type: " + s.type.label()), true);
	}

	public static void cycleType(ServerPlayerEntity player) {
		setType(player, of(player.getUuid()).type.next());
	}

	public static void setType(ServerPlayerEntity player, ZoneType type) {
		of(player.getUuid()).type = type;
		player.sendMessage(Text.literal("Zone type: " + type.label()), true);
	}

	/** Zones the selection as the wand's current type and tells the player how it went. */
	public static void apply(ServerPlayerEntity player) {
		Selection s = of(player.getUuid());
		Rect area = s.area();
		if (area == null) {
			tell(player, "Select two corners first: left-click one corner and right-click the other with the Zoning Wand.");
			return;
		}
		MinecraftServer server = player.getEntityWorld().getServer();
		CityData cities = CityData.get(server);
		ZoneData zones = ZoneData.get(server);
		ZonePlot[] plot = new ZonePlot[1];
		String err = ZoningService.zone(cities.registry(), zones.registry(), player.getUuid(), area, s.type,
			System.currentTimeMillis(), plot);
		if (err != null) {
			tell(player, err);
			return;
		}
		zones.markDirty();
		City city = ExpansionService.cityAt(cities.registry(), area.minX(), area.minZ());
		tell(player, "Zoned " + area.area() + " blocks as " + s.type.label() + " in " + city.name + ".");
	}

	/** Removes zoning inside the selection. */
	public static void clear(ServerPlayerEntity player) {
		Rect area = of(player.getUuid()).area();
		if (area == null) {
			tell(player, "Select two corners first: left-click one corner and right-click the other with the Zoning Wand.");
			return;
		}
		MinecraftServer server = player.getEntityWorld().getServer();
		CityData cities = CityData.get(server);
		ZoneData zones = ZoneData.get(server);
		String err = ZoningService.clear(cities.registry(), zones.registry(), player.getUuid(), area);
		if (err != null) {
			tell(player, err);
			return;
		}
		zones.markDirty();
		tell(player, "Zoning removed from the selection.");
	}

	private static int width(Rect r) {
		return r.maxX() - r.minX() + 1;
	}

	private static int depth(Rect r) {
		return r.maxZ() - r.minZ() + 1;
	}

	private static void tell(ServerPlayerEntity player, String msg) {
		player.sendMessage(Text.literal(msg), false);
	}
}

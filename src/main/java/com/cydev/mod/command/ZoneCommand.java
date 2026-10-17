package com.cydev.mod.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.cydev.mod.expansion.newcity.ExpansionService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.zone.ZoneMap;
import com.cydev.mod.sim.zone.ZoneType;
import com.cydev.mod.world.item.WandSelections;
import com.cydev.mod.world.persistence.CityData;
import com.cydev.mod.world.persistence.ZoneData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Commands for the Zoning Wand's work, for players who prefer typing:
 * <pre>
 * /cydev zone type &lt;residential|commercial|industrial|office&gt;
 * /cydev zone apply | clear          (act on the wand's selection)
 * /cydev zone here                   (what is zoned where you stand)
 * /cydev zone summary &lt;city&gt;
 * </pre>
 */
public final class ZoneCommand {
	private ZoneCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("cydev").then(zoneTree())));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> zoneTree() {
		return literal("zone")
			.then(literal("type").then(argument("type", StringArgumentType.word())
				.suggests((ctx, builder) -> CommandSource.suggestMatching(
					Arrays.stream(ZoneType.values()).map(t -> t.name().toLowerCase()), builder))
				.executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					ZoneType type = ZoneType.parse(StringArgumentType.getString(ctx, "type"));
					if (type == null) {
						return fail(ctx.getSource(), "Unknown zone type. Use one of: "
							+ Arrays.toString(ZoneType.values()));
					}
					WandSelections.setType(player, type);
					return 1;
				})))
			.then(literal("apply").executes(ctx -> {
				WandSelections.apply(ctx.getSource().getPlayerOrThrow());
				return 1;
			}))
			.then(literal("clear").executes(ctx -> {
				WandSelections.clear(ctx.getSource().getPlayerOrThrow());
				return 1;
			}))
			.then(literal("here").executes(ctx -> {
				ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
				var server = ctx.getSource().getServer();
				City city = ExpansionService.cityAt(CityData.get(server).registry(), p.getBlockX(), p.getBlockZ());
				if (city == null) {
					return ok(ctx.getSource(), "You are on unclaimed land.");
				}
				ZoneType type = ZoneData.get(server).registry().of(city.id).zoneAt(p.getBlockX(), p.getBlockZ());
				return ok(ctx.getSource(), city.name + ": " + (type == null ? "not zoned here." : "zoned " + type.label() + " here."));
			}))
			.then(literal("summary").then(argument("city", StringArgumentType.word()).executes(ctx -> {
				var server = ctx.getSource().getServer();
				City city = CityData.get(server).findByName(StringArgumentType.getString(ctx, "city"));
				if (city == null) {
					return fail(ctx.getSource(), "Unknown city.");
				}
				ZoneMap map = ZoneData.get(server).registry().of(city.id);
				ok(ctx.getSource(), "Zoning in " + city.name + " (" + map.plots.size() + " plots):");
				for (ZoneType t : ZoneType.values()) {
					ok(ctx.getSource(), "  " + t.label() + ": " + map.areaOf(t) + " blocks");
				}
				return 1;
			})));
	}

	private static int ok(ServerCommandSource s, String msg) {
		s.sendFeedback(() -> Text.literal(msg), false);
		return 1;
	}

	private static int fail(ServerCommandSource s, String msg) {
		s.sendError(Text.literal(msg));
		return 0;
	}
}

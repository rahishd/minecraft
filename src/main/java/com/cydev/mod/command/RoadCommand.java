package com.cydev.mod.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.cydev.mod.network.road.RoadNetwork;
import com.cydev.mod.network.road.RoadPos;
import com.cydev.mod.world.persistence.RoadData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Inspect the road network built from placed road blocks:
 * <pre>
 * /cydev road here                      (the road block you stand on)
 * /cydev road summary                   (blocks, separate road systems, junctions)
 * /cydev road connected &lt;from&gt; &lt;to&gt;   (are two road blocks joined, and how far apart)
 * </pre>
 */
public final class RoadCommand {
	private RoadCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("cydev").then(roadTree())));
	}

	private static RoadPos at(BlockPos p) {
		return new RoadPos(p.getX(), p.getY(), p.getZ());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> roadTree() {
		return literal("road")
			.then(literal("here").executes(ctx -> {
				ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
				RoadNetwork net = RoadData.get(ctx.getSource().getServer()).network();
				RoadPos pos = at(p.getBlockPos());
				if (!net.contains(pos)) {
					pos = new RoadPos(pos.x(), pos.y() - 1, pos.z());
				}
				if (!net.contains(pos)) {
					return fail(ctx.getSource(), "You are not standing on a drivable road block.");
				}
				int system = net.componentOf(pos).size();
				ok(ctx.getSource(), net.kindAt(pos) + " at " + pos.x() + " " + pos.y() + " " + pos.z() + ": "
					+ net.degree(pos) + " connection(s)"
					+ (net.isJunction(pos) ? ", junction" : net.isDeadEnd(pos) ? ", dead end" : "")
					+ "; part of a road system of " + system + " block(s).");
				return 1;
			}))
			.then(literal("summary").executes(ctx -> {
				RoadNetwork.Stats s = RoadData.get(ctx.getSource().getServer()).network().stats();
				if (s.tiles() == 0) {
					return ok(ctx.getSource(), "No roads yet. Place asphalt blocks to build some.");
				}
				ok(ctx.getSource(), s.tiles() + " road blocks in " + s.components() + " separate road system(s); the biggest has "
					+ s.largestComponent() + ".");
				ok(ctx.getSource(), "  " + s.junctions() + " junction block(s), " + s.deadEnds() + " dead end(s).");
				return 1;
			}))
			.then(literal("connected").then(argument("from", BlockPosArgumentType.blockPos())
				.then(argument("to", BlockPosArgumentType.blockPos()).executes(ctx -> {
					RoadNetwork net = RoadData.get(ctx.getSource().getServer()).network();
					RoadPos from = at(BlockPosArgumentType.getBlockPos(ctx, "from"));
					RoadPos to = at(BlockPosArgumentType.getBlockPos(ctx, "to"));
					if (!net.contains(from) || !net.contains(to)) {
						return fail(ctx.getSource(), "Both positions must be drivable road blocks.");
					}
					List<RoadPos> route = net.path(from, to);
					if (route == null) {
						return ok(ctx.getSource(), "Not connected: these blocks are in separate road systems.");
					}
					return ok(ctx.getSource(), "Connected: " + (route.size() - 1) + " block(s) apart along the road.");
				}))));
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

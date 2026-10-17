package com.cydev.mod.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Role;
import com.cydev.mod.government.election.Candidate;
import com.cydev.mod.government.election.Election;
import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.world.persistence.CityData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /cydev city ..., /cydev election ..., /cydev mayor ...
 * Command-line interface for the government system until the GUI exists.
 */
public final class CityCommand {
	private CityCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("cydev")
				.then(cityTree())
				.then(electionTree())
				.then(mayorTree())));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> cityTree() {
		return literal("city")
			.then(literal("create").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(argument("name", StringArgumentType.word()).executes(ctx -> {
					CityData data = CityData.get(ctx.getSource().getServer());
					String name = StringArgumentType.getString(ctx, "name");
					if (data.findByName(name) != null) {
						return fail(ctx.getSource(), "A city named " + name + " already exists.");
					}
					City city = data.registry().create(name);
					data.markDirty();
					return ok(ctx.getSource(), "Created city " + city.name, true);
				})))
			.then(literal("list").executes(ctx -> {
				CityData data = CityData.get(ctx.getSource().getServer());
				if (data.registry().all().isEmpty()) {
					return ok(ctx.getSource(), "No cities yet.", false);
				}
				for (City c : data.registry().all().values()) {
					ok(ctx.getSource(), c.name + " - Mayor: " + (c.hasMayor() ? c.mayorName : "none")
						+ " - treasury " + c.treasury, false);
				}
				return 1;
			}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> electionTree() {
		return literal("election")
			.then(literal("start").then(argument("city", StringArgumentType.word())
				.executes(ctx -> startElection(ctx, false))))
			.then(literal("forcestart").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(argument("city", StringArgumentType.word()).executes(ctx -> startElection(ctx, true))))
			.then(literal("run").then(argument("city", StringArgumentType.word())
				.then(argument("slogan", StringArgumentType.greedyString()).executes(ctx -> {
					CityData data = CityData.get(ctx.getSource().getServer());
					City city = data.findByName(StringArgumentType.getString(ctx, "city"));
					if (city == null) {
						return fail(ctx.getSource(), "Unknown city.");
					}
					ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
					String err = ElectionService.addCandidate(city, p.getUuid(), p.getName().getString(),
						StringArgumentType.getString(ctx, "slogan"));
					if (err != null) {
						return fail(ctx.getSource(), err);
					}
					data.markDirty();
					return ok(ctx.getSource(), p.getName().getString() + " is now a candidate for Mayor of " + city.name, true);
				}))))
			.then(literal("vote").then(argument("city", StringArgumentType.word())
				.then(argument("candidate", StringArgumentType.word()).executes(ctx -> {
					CityData data = CityData.get(ctx.getSource().getServer());
					City city = data.findByName(StringArgumentType.getString(ctx, "city"));
					if (city == null) {
						return fail(ctx.getSource(), "Unknown city.");
					}
					String wanted = StringArgumentType.getString(ctx, "candidate");
					Candidate target = null;
					if (city.election != null) {
						for (Candidate c : city.election.candidates) {
							if (c.name().equalsIgnoreCase(wanted)) {
								target = c;
							}
						}
					}
					if (target == null) {
						return fail(ctx.getSource(), "No such candidate.");
					}
					ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
					String err = ElectionService.vote(city, p.getUuid(), target.playerId());
					if (err != null) {
						return fail(ctx.getSource(), err);
					}
					data.markDirty();
					return ok(ctx.getSource(), "Vote cast for " + target.name() + ".", false);
				}))))
			.then(literal("status").then(argument("city", StringArgumentType.word()).executes(ctx -> {
				CityData data = CityData.get(ctx.getSource().getServer());
				City city = data.findByName(StringArgumentType.getString(ctx, "city"));
				if (city == null) {
					return fail(ctx.getSource(), "Unknown city.");
				}
				Election e = city.election;
				if (e == null) {
					return ok(ctx.getSource(), "No election in " + city.name + ".", false);
				}
				ok(ctx.getSource(), "Election in " + city.name + ": " + e.state, false);
				for (Candidate c : e.candidates) {
					ok(ctx.getSource(), " - " + c.name() + " (" + e.votesFor(c.playerId()) + " votes): " + c.slogan(), false);
				}
				return 1;
			})))
			.then(literal("close").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(argument("city", StringArgumentType.word()).executes(ctx -> {
					CityData data = CityData.get(ctx.getSource().getServer());
					City city = data.findByName(StringArgumentType.getString(ctx, "city"));
					if (city == null || !ElectionService.isActive(city)) {
						return fail(ctx.getSource(), "No active election.");
					}
					String msg = ElectionService.close(city, System.currentTimeMillis());
					data.markDirty();
					ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal(msg), false);
					return 1;
				})));
	}

	private static int startElection(CommandContext<ServerCommandSource> ctx, boolean force) {
		CityData data = CityData.get(ctx.getSource().getServer());
		City city = data.findByName(StringArgumentType.getString(ctx, "city"));
		if (city == null) {
			return fail(ctx.getSource(), "Unknown city.");
		}
		String err = ElectionService.start(city, System.currentTimeMillis(), force);
		if (err != null) {
			return fail(ctx.getSource(), err);
		}
		data.markDirty();
		ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal(
			"Election started in " + city.name + "! File your candidacy with /cydev election run "
				+ city.name + " <slogan>"), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> mayorTree() {
		return literal("mayor")
			.then(literal("info").then(argument("city", StringArgumentType.word()).executes(ctx -> {
				CityData data = CityData.get(ctx.getSource().getServer());
				City city = data.findByName(StringArgumentType.getString(ctx, "city"));
				if (city == null) {
					return fail(ctx.getSource(), "Unknown city.");
				}
				if (!city.hasMayor()) {
					return ok(ctx.getSource(), city.name + " has no mayor.", false);
				}
				long left = Math.max(0, city.termEnd - System.currentTimeMillis());
				ok(ctx.getSource(), "Mayor of " + city.name + ": " + city.mayorName
					+ " - term ends in " + TimeUnit.MILLISECONDS.toDays(left) + " day(s).", false);
				for (Appointment a : city.appointments) {
					ok(ctx.getSource(), " - " + a.role() + ": " + a.name(), false);
				}
				return 1;
			})))
			.then(literal("appoint").then(argument("city", StringArgumentType.word())
				.then(argument("role", StringArgumentType.word())
					.then(argument("player", StringArgumentType.word()).executes(ctx -> {
						CityData data = CityData.get(ctx.getSource().getServer());
						City city = data.findByName(StringArgumentType.getString(ctx, "city"));
						if (city == null) {
							return fail(ctx.getSource(), "Unknown city.");
						}
						Role role;
						try {
							role = Role.valueOf(StringArgumentType.getString(ctx, "role").toUpperCase());
						} catch (IllegalArgumentException ex) {
							return fail(ctx.getSource(), "Role must be advisor, secretary or council_member.");
						}
						ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager()
							.getPlayer(StringArgumentType.getString(ctx, "player"));
						if (target == null) {
							return fail(ctx.getSource(), "Player must be online.");
						}
						ServerPlayerEntity mayor = ctx.getSource().getPlayerOrThrow();
						String err = ElectionService.appoint(city, mayor.getUuid(), target.getUuid(),
							target.getName().getString(), role);
						if (err != null) {
							return fail(ctx.getSource(), err);
						}
						data.markDirty();
						return ok(ctx.getSource(), target.getName().getString() + " appointed as " + role, true);
					})))));
	}

	private static int ok(ServerCommandSource s, String msg, boolean broadcast) {
		s.sendFeedback(() -> Text.literal(msg), broadcast);
		return 1;
	}

	private static int fail(ServerCommandSource s, String msg) {
		s.sendError(Text.literal(msg));
		return 0;
	}
}

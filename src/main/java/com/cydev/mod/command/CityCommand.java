package com.cydev.mod.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.election.Candidate;
import com.cydev.mod.government.election.ElectedOffice;
import com.cydev.mod.government.election.Election;
import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Ward;
import com.cydev.mod.world.persistence.CityData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Temporary command interface for the government system (GUI comes later):
 * <pre>
 * /cydev city create|list
 * /cydev city &lt;city&gt; info | election ... | appoint &lt;position&gt; &lt;player&gt; | ward create &lt;no&gt; &lt;name&gt;
 * /cydev ward &lt;city&gt; &lt;no&gt; info | election ... | appoint &lt;position&gt; &lt;player&gt;
 * </pre>
 */
public final class CityCommand {
	private CityCommand() {
	}

	private record Target(CityData data, City city, Ward ward, ElectedOffice office) {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("cydev")
				.then(cityTree())
				.then(wardTree())));
	}

	private static boolean isOp(ServerCommandSource s) {
		return CommandManager.GAMEMASTERS_CHECK.allows(s.getPermissions());
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
						+ " - " + c.wards.size() + " ward(s) - treasury " + c.treasury, false);
				}
				return 1;
			}))
			.then(argument("city", StringArgumentType.word())
				.then(literal("info").executes(ctx -> {
					Target t = resolve(ctx, false);
					return t == null ? 0 : cityInfo(ctx.getSource(), t.city());
				}))
				.then(electionTree(false))
				.then(appointTree(false))
				.then(literal("ward").then(literal("create")
					.then(argument("number", IntegerArgumentType.integer(1))
						.then(argument("name", StringArgumentType.word()).executes(ctx -> {
							Target t = resolve(ctx, false);
							if (t == null) {
								return 0;
							}
							ServerCommandSource src = ctx.getSource();
							boolean mayor = src.getEntity() instanceof ServerPlayerEntity p && t.city().isMayor(p.getUuid());
							if (!mayor && !isOp(src)) {
								return fail(src, "Only the Mayor can create wards.");
							}
							int no = IntegerArgumentType.getInteger(ctx, "number");
							if (t.city().ward(no) != null) {
								return fail(src, "Ward " + no + " already exists.");
							}
							t.city().wards.add(new Ward(UUID.randomUUID(), no, StringArgumentType.getString(ctx, "name")));
							t.data().markDirty();
							return ok(src, "Ward " + no + " created in " + t.city().name, true);
						}))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> wardTree() {
		return literal("ward").then(argument("city", StringArgumentType.word())
			.then(argument("ward", IntegerArgumentType.integer(1))
				.then(literal("info").executes(ctx -> {
					Target t = resolve(ctx, true);
					if (t == null) {
						return 0;
					}
					Ward w = t.ward();
					ok(ctx.getSource(), "Ward " + w.number + " (" + w.name + ") of " + t.city().name
						+ " - Chairperson: " + (w.hasHolder() ? w.chairName : "none"), false);
					for (Appointment a : w.appointments) {
						ok(ctx.getSource(), " - " + a.position() + ": " + a.name(), false);
					}
					return 1;
				}))
				.then(electionTree(true))
				.then(appointTree(true))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> electionTree(boolean ward) {
		return literal("election")
			.then(literal("start").executes(ctx -> startElection(ctx, ward, false)))
			.then(literal("forcestart").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.executes(ctx -> startElection(ctx, ward, true)))
			.then(literal("run").then(argument("slogan", StringArgumentType.greedyString()).executes(ctx -> {
				Target t = resolve(ctx, ward);
				if (t == null) {
					return 0;
				}
				ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
				String err = ElectionService.addCandidate(t.office(), p.getUuid(), p.getName().getString(),
					StringArgumentType.getString(ctx, "slogan"));
				if (err != null) {
					return fail(ctx.getSource(), err);
				}
				t.data().markDirty();
				return ok(ctx.getSource(), p.getName().getString() + " is a candidate for " + t.office().officeName(), true);
			})))
			.then(literal("vote").then(argument("candidate", StringArgumentType.word()).executes(ctx -> {
				Target t = resolve(ctx, ward);
				if (t == null) {
					return 0;
				}
				String wanted = StringArgumentType.getString(ctx, "candidate");
				Candidate target = null;
				if (t.office().election() != null) {
					for (Candidate c : t.office().election().candidates) {
						if (c.name().equalsIgnoreCase(wanted)) {
							target = c;
						}
					}
				}
				if (target == null) {
					return fail(ctx.getSource(), "No such candidate.");
				}
				ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
				String err = ElectionService.vote(t.office(), p.getUuid(), target.playerId());
				if (err != null) {
					return fail(ctx.getSource(), err);
				}
				t.data().markDirty();
				return ok(ctx.getSource(), "Vote cast for " + target.name() + ".", false);
			})))
			.then(literal("status").executes(ctx -> {
				Target t = resolve(ctx, ward);
				if (t == null) {
					return 0;
				}
				Election e = t.office().election();
				if (e == null) {
					return ok(ctx.getSource(), "No election for " + t.office().officeName() + ".", false);
				}
				ok(ctx.getSource(), "Election for " + t.office().officeName() + ": " + e.state, false);
				for (Candidate c : e.candidates) {
					ok(ctx.getSource(), " - " + c.name() + " (" + e.votesFor(c.playerId()) + " votes): " + c.slogan(), false);
				}
				return 1;
			}))
			.then(literal("close").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.executes(ctx -> {
					Target t = resolve(ctx, ward);
					if (t == null) {
						return 0;
					}
					if (!ElectionService.isActive(t.office())) {
						return fail(ctx.getSource(), "No active election.");
					}
					String msg = ElectionService.close(t.office(), System.currentTimeMillis());
					t.data().markDirty();
					ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal(msg), false);
					return 1;
				}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> appointTree(boolean ward) {
		return literal("appoint").then(argument("position", StringArgumentType.word())
			.then(argument("player", StringArgumentType.word()).executes(ctx -> {
				Target t = resolve(ctx, ward);
				if (t == null) {
					return 0;
				}
				Position pos;
				try {
					pos = Position.valueOf(StringArgumentType.getString(ctx, "position").toUpperCase());
				} catch (IllegalArgumentException ex) {
					return fail(ctx.getSource(), "Unknown position.");
				}
				ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager()
					.getPlayer(StringArgumentType.getString(ctx, "player"));
				if (target == null) {
					return fail(ctx.getSource(), "Player must be online.");
				}
				ServerPlayerEntity boss = ctx.getSource().getPlayerOrThrow();
				String name = target.getName().getString();
				String err = ward
					? ElectionService.appointWard(t.ward(), boss.getUuid(), target.getUuid(), name, pos)
					: ElectionService.appointMunicipal(t.city(), boss.getUuid(), target.getUuid(), name, pos);
				if (err != null) {
					return fail(ctx.getSource(), err);
				}
				t.data().markDirty();
				return ok(ctx.getSource(), name + " appointed as " + pos, true);
			})));
	}

	private static int startElection(CommandContext<ServerCommandSource> ctx, boolean ward, boolean force) {
		Target t = resolve(ctx, ward);
		if (t == null) {
			return 0;
		}
		String err = ElectionService.start(t.office(), System.currentTimeMillis(), force);
		if (err != null) {
			return fail(ctx.getSource(), err);
		}
		t.data().markDirty();
		ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal(
			"Election started for " + t.office().officeName() + " (" + t.city().name
				+ ")! File candidacy with the election run command."), false);
		return 1;
	}

	private static int cityInfo(ServerCommandSource s, City city) {
		if (!city.hasMayor()) {
			ok(s, city.name + " has no mayor.", false);
		} else {
			long left = Math.max(0, city.termEnd - System.currentTimeMillis());
			ok(s, "Mayor of " + city.name + ": " + city.mayorName + " - term ends in "
				+ TimeUnit.MILLISECONDS.toDays(left) + " day(s).", false);
			if (city.deputyName != null) {
				ok(s, "Deputy Mayor: " + city.deputyName, false);
			}
		}
		for (Appointment a : city.appointments) {
			ok(s, " - " + a.position() + ": " + a.name(), false);
		}
		for (Ward w : city.wards) {
			ok(s, " Ward " + w.number + " (" + w.name + ") Chairperson: " + (w.hasHolder() ? w.chairName : "none"), false);
		}
		return 1;
	}

	private static Target resolve(CommandContext<ServerCommandSource> ctx, boolean ward) {
		CityData data = CityData.get(ctx.getSource().getServer());
		City city = data.findByName(StringArgumentType.getString(ctx, "city"));
		if (city == null) {
			fail(ctx.getSource(), "Unknown city.");
			return null;
		}
		if (!ward) {
			return new Target(data, city, null, city);
		}
		Ward w = city.ward(IntegerArgumentType.getInteger(ctx, "ward"));
		if (w == null) {
			fail(ctx.getSource(), "Unknown ward.");
			return null;
		}
		return new Target(data, city, w, w);
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

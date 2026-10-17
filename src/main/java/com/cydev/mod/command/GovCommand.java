package com.cydev.mod.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.cydev.mod.expansion.federation.Federation;
import com.cydev.mod.expansion.federation.FederationService;
import com.cydev.mod.expansion.newcity.ExpansionService;
import com.cydev.mod.government.council.ActorResolver;
import com.cydev.mod.government.council.AssemblyService;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.mayor.ScoreService;
import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.government.workflow.AuditEntry;
import com.cydev.mod.government.workflow.CaseStatus;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.government.workflow.ServiceType;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRecords;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.city.Ward;
import com.cydev.mod.world.persistence.CityData;
import com.cydev.mod.world.persistence.RecordsData;
import com.cydev.mod.world.persistence.ZoneData;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Administration commands: service cases, projects, budget, score, audit,
 * land, expansion, federations and the sample scenario. Temporary UI until
 * the in-game counters and boards exist.
 */
public final class GovCommand {
	private GovCommand() {
	}

	/** Everything a handler needs, resolved from the "city" argument. */
	private record Env(ServerCommandSource src, CityData cities, RecordsData recs, City city, CityRecords rec,
					   long now) {
		ServerPlayerEntity player() throws CommandSyntaxException {
			return src.getPlayerOrThrow();
		}

		void save() {
			cities.markDirty();
			recs.markDirty();
		}
	}

	private interface Handler {
		int run(CommandContext<ServerCommandSource> ctx, Env env) throws CommandSyntaxException;
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("cydev")
				.then(caseTree())
				.then(projectTree())
				.then(budgetTree())
				.then(withCity(literal("score"), (ctx, e) -> score(e)))
				.then(auditTree())
				.then(landTree())
				.then(expandTree())
				.then(federationTree())
				.then(literal("scenario").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
					.then(literal("load").executes(GovCommand::loadScenario)))));
	}

	// ---------- helpers ----------

	private static Env env(CommandContext<ServerCommandSource> ctx) {
		var server = ctx.getSource().getServer();
		CityData cities = CityData.get(server);
		City city = cities.findByName(StringArgumentType.getString(ctx, "city"));
		if (city == null) {
			fail(ctx.getSource(), "Unknown city.");
			return null;
		}
		RecordsData recs = RecordsData.get(server);
		return new Env(ctx.getSource(), cities, recs, city, recs.of(city.id), System.currentTimeMillis());
	}

	private static LiteralArgumentBuilder<ServerCommandSource> withCity(LiteralArgumentBuilder<ServerCommandSource> lit, Handler h) {
		return lit.then(argument("city", StringArgumentType.word()).executes(wrap(h)));
	}

	private static com.mojang.brigadier.Command<ServerCommandSource> wrap(Handler h) {
		return ctx -> {
			Env e = env(ctx);
			return e == null ? 0 : h.run(ctx, e);
		};
	}

	private static <T> RequiredArgumentBuilder<ServerCommandSource, T> arg(String name,
			com.mojang.brigadier.arguments.ArgumentType<T> type) {
		return argument(name, type);
	}

	/** Tries each role the player holds (ward role first); succeeds if any role is allowed. */
	private static String asAny(List<Actor> actors, Function<Actor, String> action) {
		String first = null;
		for (Actor a : actors) {
			String err = action.apply(a);
			if (err == null) {
				return null;
			}
			if (first == null) {
				first = err;
			}
		}
		return first;
	}

	private static List<Actor> roles(Env e, ServerPlayerEntity p, java.util.UUID wardId) {
		return ActorResolver.rolesOf(e.city(), p.getUuid(), p.getName().getString(), wardId);
	}

	private static CaseStatus status(CommandContext<ServerCommandSource> ctx) {
		try {
			return CaseStatus.valueOf(StringArgumentType.getString(ctx, "status").toUpperCase());
		} catch (IllegalArgumentException ex) {
			fail(ctx.getSource(), "Unknown status. Use one of: " + java.util.Arrays.toString(CaseStatus.values()));
			return null;
		}
	}

	private static String optional(CommandContext<ServerCommandSource> ctx, String name) {
		try {
			return StringArgumentType.getString(ctx, name);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String wardName(City city, java.util.UUID wardId) {
		Ward w = city.wardById(wardId);
		return w == null ? "?" : "Ward " + w.number;
	}

	private static void history(Env e, List<AuditEntry> log) {
		for (AuditEntry a : log) {
			ok(e.src(), "  " + Instant.ofEpochMilli(a.timestamp()) + " " + a.actorName() + " (" + a.role() + "): "
				+ a.action() + " " + a.oldStatus() + " -> " + a.newStatus()
				+ (a.reason() == null ? "" : " - " + a.reason()), false);
		}
	}

	// ---------- service cases ----------

	private static LiteralArgumentBuilder<ServerCommandSource> caseTree() {
		return literal("case")
			.then(literal("file").then(arg("city", StringArgumentType.word())
				.then(arg("ward", IntegerArgumentType.integer(1))
					.then(arg("type", StringArgumentType.word()).executes(wrap((ctx, e) -> {
						Ward ward = e.city().ward(IntegerArgumentType.getInteger(ctx, "ward"));
						if (ward == null) {
							return fail(e.src(), "Unknown ward.");
						}
						ServiceType type;
						try {
							type = ServiceType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
						} catch (IllegalArgumentException ex) {
							return fail(e.src(), "Unknown service. Use one of: " + java.util.Arrays.toString(ServiceType.values()));
						}
						ServerPlayerEntity p = e.player();
						ServiceCase c = new ServiceCase(java.util.UUID.randomUUID(), e.rec().nextCaseNo++, p.getUuid(),
							p.getName().getString(), ward.id, type, e.now());
						e.rec().cases.add(c);
						Actor me = new Actor(p.getUuid(), p.getName().getString(), Position.RESIDENT, ward.id);
						String err = e.rec().workflow().move(c, me, CaseStatus.SUBMITTED, null, e.now());
						e.save();
						return err != null ? fail(e.src(), err)
							: ok(e.src(), "Case #" + c.caseNo + " (" + type + ") submitted to Ward " + ward.number
								+ ". Add documents with /cydev case evidence.", false);
					}))))))
			.then(literal("evidence").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1))
					.then(arg("text", StringArgumentType.greedyString()).executes(wrap((ctx, e) -> {
						ServiceCase c = e.rec().findCase(IntegerArgumentType.getInteger(ctx, "no"));
						ServerPlayerEntity p = e.player();
						if (c == null || !c.applicantId.equals(p.getUuid())) {
							return fail(e.src(), "No such case of yours.");
						}
						if (c.isEnded()) {
							return fail(e.src(), "Case is already finished.");
						}
						c.evidence.add(StringArgumentType.getString(ctx, "text"));
						e.save();
						return ok(e.src(), "Document added to case #" + c.caseNo + ".", false);
					}))))))
			.then(literal("move").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1))
					.then(arg("status", StringArgumentType.word())
						.executes(wrap((ctx, e) -> moveCase(ctx, e, null)))
						.then(arg("reason", StringArgumentType.greedyString())
							.executes(wrap((ctx, e) -> moveCase(ctx, e, StringArgumentType.getString(ctx, "reason")))))))))
			.then(literal("info").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1)).executes(wrap((ctx, e) -> {
					ServiceCase c = e.rec().findCase(IntegerArgumentType.getInteger(ctx, "no"));
					if (c == null) {
						return fail(e.src(), "No such case.");
					}
					ok(e.src(), "Case #" + c.caseNo + " " + c.type + " - " + wardName(e.city(), c.wardId) + " - applicant "
						+ c.applicantName + " - " + c.status + " (with " + c.assignedRole + ")"
						+ " - documents: " + c.evidence.size() + (c.rating > 0 ? " - rated " + c.rating + "/5" : ""), false);
					history(e, c.history);
					return 1;
				})))))
			.then(literal("list").then(arg("city", StringArgumentType.word()).executes(wrap((ctx, e) -> {
				ServerPlayerEntity p = e.player();
				boolean staff = roles(e, p, null).stream().anyMatch(a -> a.position() != Position.RESIDENT);
				int shown = 0;
				for (int i = e.rec().cases.size() - 1; i >= 0 && shown < 15; i--) {
					ServiceCase c = e.rec().cases.get(i);
					if (staff || c.applicantId.equals(p.getUuid())) {
						ok(e.src(), "#" + c.caseNo + " " + c.type + " " + wardName(e.city(), c.wardId) + " "
							+ c.applicantName + " - " + c.status, false);
						shown++;
					}
				}
				return shown == 0 ? ok(e.src(), "No cases.", false) : 1;
			}))))
			.then(literal("rate").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1))
					.then(arg("stars", IntegerArgumentType.integer(1, 5)).executes(wrap((ctx, e) -> {
						ServiceCase c = e.rec().findCase(IntegerArgumentType.getInteger(ctx, "no"));
						ServerPlayerEntity p = e.player();
						if (c == null || !c.applicantId.equals(p.getUuid())) {
							return fail(e.src(), "No such case of yours.");
						}
						if (!c.isEnded()) {
							return fail(e.src(), "You can rate a case once it is finished.");
						}
						if (c.rating > 0) {
							return fail(e.src(), "You already rated this case.");
						}
						c.rating = IntegerArgumentType.getInteger(ctx, "stars");
						e.save();
						return ok(e.src(), "Thank you - rating recorded.", false);
					}))))));
	}

	private static int moveCase(CommandContext<ServerCommandSource> ctx, Env e, String reason) throws CommandSyntaxException {
		ServiceCase c = e.rec().findCase(IntegerArgumentType.getInteger(ctx, "no"));
		CaseStatus to = status(ctx);
		if (c == null) {
			return fail(e.src(), "No such case.");
		}
		if (to == null) {
			return 0;
		}
		String err = asAny(roles(e, e.player(), c.wardId),
			a -> e.rec().workflow().move(c, a, to, reason, e.now()));
		if (err != null) {
			return fail(e.src(), err);
		}
		e.save();
		return ok(e.src(), "Case #" + c.caseNo + " is now " + c.status + ".", false);
	}

	// ---------- projects ----------

	private static LiteralArgumentBuilder<ServerCommandSource> projectTree() {
		return literal("project")
			.then(literal("propose").then(arg("city", StringArgumentType.word())
				.then(arg("ward", IntegerArgumentType.integer(1))
					.then(arg("line", IntegerArgumentType.integer(1))
						.then(arg("priority", IntegerArgumentType.integer(1))
							.then(arg("estimate", LongArgumentType.longArg(1))
								.then(arg("beneficiaries", IntegerArgumentType.integer(0))
									.then(arg("title", StringArgumentType.greedyString()).executes(wrap((ctx, e) -> {
										Ward ward = e.city().ward(IntegerArgumentType.getInteger(ctx, "ward"));
										BudgetLine line = e.rec().findLine(IntegerArgumentType.getInteger(ctx, "line"));
										if (ward == null || line == null) {
											return fail(e.src(), "Unknown ward or budget line.");
										}
										ServerPlayerEntity p = e.player();
										String where = "x=" + p.getBlockX() + " z=" + p.getBlockZ();
										ProjectProposal pr = new ProjectProposal(e.rec().nextProjectNo++, ward.id,
											StringArgumentType.getString(ctx, "title"), where,
											IntegerArgumentType.getInteger(ctx, "priority"),
											LongArgumentType.getLong(ctx, "estimate"),
											IntegerArgumentType.getInteger(ctx, "beneficiaries"), line);
										e.rec().projects.add(pr);
										String err = asAny(roles(e, p, ward.id),
											a -> e.rec().projectService().move(pr, a, CaseStatus.SUBMITTED, null, e.now()));
										e.save();
										return err != null
											? ok(e.src(), "Project #" + pr.projectNo + " saved as DRAFT: " + err, false)
											: ok(e.src(), "Project #" + pr.projectNo + " submitted for technical review.", true);
									}))))))))))
			.then(literal("move").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1))
					.then(arg("status", StringArgumentType.word())
						.executes(wrap((ctx, e) -> moveProject(ctx, e, null)))
						.then(arg("reason", StringArgumentType.greedyString())
							.executes(wrap((ctx, e) -> moveProject(ctx, e, StringArgumentType.getString(ctx, "reason")))))))))
			.then(literal("budgetcheck").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1)).executes(wrap((ctx, e) ->
					projectAction(ctx, e, (p, a) -> {
						String r = e.rec().projectService().budgetCheck(p, a, e.now());
						return r;
					}, "Budget check done."))))))
			.then(literal("contractor").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1))
					.then(arg("name", StringArgumentType.greedyString()).executes(wrap((ctx, e) ->
						projectAction(ctx, e, (p, a) -> e.rec().projectService().assignContractor(p, a,
							StringArgumentType.getString(ctx, "name")), "Contractor assigned.")))))))
			.then(literal("progress").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1))
					.then(arg("percent", IntegerArgumentType.integer(0, 100)).executes(wrap((ctx, e) ->
						projectAction(ctx, e, (p, a) -> e.rec().projectService().setProgress(p, a,
							IntegerArgumentType.getInteger(ctx, "percent"), e.now()), "Progress recorded.")))))))
			.then(orderNode("stop", (e, p, a, reason) -> e.rec().projectService().stop(p, a, reason, e.now()), "Stop order issued.", true))
			.then(orderNode("lift", (e, p, a, reason) -> e.rec().projectService().lift(p, a, reason, e.now()), "Stop order lifted.", false))
			.then(orderNode("ban", (e, p, a, reason) -> e.rec().projectService().ban(p, a, reason, e.now()), "Project banned.", true))
			.then(literal("info").then(arg("city", StringArgumentType.word())
				.then(arg("no", IntegerArgumentType.integer(1)).executes(wrap((ctx, e) -> {
					ProjectProposal p = e.rec().findProject(IntegerArgumentType.getInteger(ctx, "no"));
					if (p == null) {
						return fail(e.src(), "No such project.");
					}
					ok(e.src(), "Project #" + p.projectNo + " " + p.title + " - " + wardName(e.city(), p.wardId)
						+ " - " + p.status + (p.stopped ? " [STOPPED: " + p.orderReason + "]" : "")
						+ (p.banned ? " [BANNED: " + p.orderReason + "]" : ""), false);
					ok(e.src(), "  priority " + p.priority + ", cost " + p.estimate + " from " + p.budgetLine.owner
						+ ", beneficiaries " + p.beneficiaries + ", progress " + p.progressPercent + "%, contractor "
						+ (p.contractor == null ? "-" : p.contractor) + ", at " + p.location, false);
					history(e, p.history);
					return 1;
				})))))
			.then(literal("list").then(arg("city", StringArgumentType.word()).executes(wrap((ctx, e) -> {
				if (e.rec().projects.isEmpty()) {
					return ok(e.src(), "No projects.", false);
				}
				for (ProjectProposal p : e.rec().projects) {
					ok(e.src(), "#" + p.projectNo + " " + p.title + " " + wardName(e.city(), p.wardId) + " P" + p.priority
						+ " cost " + p.estimate + " - " + p.status + (p.stopped ? " (STOPPED)" : "")
						+ (p.banned ? " (BANNED)" : "") + " " + p.progressPercent + "%", false);
				}
				return 1;
			}))));
	}

	private interface ProjectOp {
		String apply(ProjectProposal p, Actor a);
	}

	private interface OrderOp {
		String apply(Env e, ProjectProposal p, Actor a, String reason);
	}

	private static int projectAction(CommandContext<ServerCommandSource> ctx, Env e, ProjectOp op, String done)
			throws CommandSyntaxException {
		ProjectProposal p = e.rec().findProject(IntegerArgumentType.getInteger(ctx, "no"));
		if (p == null) {
			return fail(e.src(), "No such project.");
		}
		String err = asAny(roles(e, e.player(), p.wardId), a -> op.apply(p, a));
		e.save();
		if (err != null) {
			return fail(e.src(), err);
		}
		return ok(e.src(), done, false);
	}

	private static int moveProject(CommandContext<ServerCommandSource> ctx, Env e, String reason) throws CommandSyntaxException {
		CaseStatus to = status(ctx);
		if (to == null) {
			return 0;
		}
		return projectAction(ctx, e, (p, a) -> e.rec().projectService().move(p, a, to, reason, e.now()),
			"Project status updated.");
	}

	private static LiteralArgumentBuilder<ServerCommandSource> orderNode(String name, OrderOp op, String done, boolean broadcast) {
		return literal(name).then(arg("city", StringArgumentType.word())
			.then(arg("no", IntegerArgumentType.integer(1))
				.then(arg("reason", StringArgumentType.greedyString()).executes(wrap((ctx, e) -> {
					ProjectProposal p = e.rec().findProject(IntegerArgumentType.getInteger(ctx, "no"));
					if (p == null) {
						return fail(e.src(), "No such project.");
					}
					String reason = StringArgumentType.getString(ctx, "reason");
					String err = asAny(roles(e, e.player(), p.wardId), a -> op.apply(e, p, a, reason));
					e.save();
					if (err != null) {
						return fail(e.src(), err);
					}
					return ok(e.src(), "Project #" + p.projectNo + ": " + done + " " + reason, broadcast);
				})))));
	}

	// ---------- budget ----------

	private static LiteralArgumentBuilder<ServerCommandSource> budgetTree() {
		return literal("budget")
			.then(literal("add").then(arg("city", StringArgumentType.word())
				.then(arg("year", IntegerArgumentType.integer(1))
					.then(arg("amount", LongArgumentType.longArg(1))
						.then(arg("owner", StringArgumentType.greedyString()).executes(wrap((ctx, e) -> {
							boolean op = CommandManager.GAMEMASTERS_CHECK.allows(e.src().getPermissions());
							boolean accounts = e.src().getEntity() instanceof ServerPlayerEntity p
								&& roles(e, p, null).stream().anyMatch(a -> a.position() == Position.ACCOUNTS_OFFICER);
							if (!op && !accounts) {
								return fail(e.src(), "Only the Accounts Officer can propose budget lines.");
							}
							BudgetLine l = e.rec().addLine(IntegerArgumentType.getInteger(ctx, "year"),
								StringArgumentType.getString(ctx, "owner"), LongArgumentType.getLong(ctx, "amount"), "Municipal revenue");
							e.save();
							return ok(e.src(), "Budget line #" + l.lineNo + " proposed. The Assembly must vote on it.", true);
						})))))))
			.then(literal("list").then(arg("city", StringArgumentType.word()).executes(wrap((ctx, e) -> {
				if (e.rec().budgetLines.isEmpty()) {
					return ok(e.src(), "No budget lines.", false);
				}
				int members = AssemblyService.members(e.city()).size();
				for (BudgetLine b : e.rec().budgetLines) {
					ok(e.src(), "#" + b.lineNo + " " + b.owner + " FY" + b.fiscalYear + ": allocated " + b.allocated
						+ ", committed " + b.committed + ", spent " + b.spent + ", available " + b.available()
						+ (b.assemblyApproved ? " [APPROVED]" : " [Assembly " + b.assemblyYes.size() + "/" + members + " yes]"), false);
				}
				return 1;
			}))))
			.then(literal("vote").then(arg("city", StringArgumentType.word())
				.then(arg("line", IntegerArgumentType.integer(1))
					.then(arg("yes", BoolArgumentType.bool()).executes(wrap((ctx, e) -> {
						BudgetLine l = e.rec().findLine(IntegerArgumentType.getInteger(ctx, "line"));
						if (l == null) {
							return fail(e.src(), "No such budget line.");
						}
						String err = AssemblyService.vote(e.city(), l, e.player().getUuid(), BoolArgumentType.getBool(ctx, "yes"));
						if (err != null) {
							return fail(e.src(), err);
						}
						e.save();
						return ok(e.src(), l.assemblyApproved ? "Budget line #" + l.lineNo + " passed by the Assembly!"
							: "Vote recorded.", l.assemblyApproved);
					}))))));
	}

	// ---------- score / audit ----------

	private static int score(Env e) {
		ScoreService.Score s = ScoreService.compute(e.rec(), e.now());
		ok(e.src(), "Administration score for " + e.city().name + " (Mayor " + (e.city().hasMayor() ? e.city().mayorName : "none")
			+ "): " + s.overall() + "/100", false);
		ok(e.src(), "  Resident satisfaction " + s.satisfaction() + " (avg " + String.format("%.1f", s.avgRating()) + "/5)"
			+ " | Cases resolved " + s.resolvedPercent() + "% | On time " + s.timelinessPercent() + "% | Projects delivered "
			+ s.deliveryPercent() + "%", false);
		ok(e.src(), "  Cases " + s.casesTotal() + " (open " + s.casesOpen() + ", overdue " + s.casesOverdue()
			+ ") | Projects done " + s.projectsCompleted() + ", active " + s.projectsActive()
			+ " | Budget used " + s.budgetUsedPercent() + "%", false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> auditTree() {
		return literal("audit").then(arg("city", StringArgumentType.word()).executes(wrap((ctx, e) -> {
			List<AuditEntry> log = e.rec().auditLog;
			if (log.isEmpty()) {
				return ok(e.src(), "Audit log is empty.", false);
			}
			history(e, log.subList(Math.max(0, log.size() - 15), log.size()));
			return 1;
		})));
	}

	// ---------- land / expansion / federation ----------

	private static LiteralArgumentBuilder<ServerCommandSource> landTree() {
		return literal("land")
			.then(literal("claim").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(arg("city", StringArgumentType.word())
					.then(arg("x1", IntegerArgumentType.integer())
						.then(arg("z1", IntegerArgumentType.integer())
							.then(arg("x2", IntegerArgumentType.integer())
								.then(arg("z2", IntegerArgumentType.integer()).executes(wrap((ctx, e) -> {
									Rect r = Rect.of(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "z1"),
										IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "z2"));
									String err = ExpansionService.claim(e.cities().registry(), e.city(), r);
									if (err != null) {
										return fail(e.src(), err);
									}
									e.save();
									return ok(e.src(), "Land claimed for " + e.city().name + ".", true);
								}))))))))
			.then(literal("here").executes(ctx -> {
				ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
				City c = ExpansionService.cityAt(CityData.get(ctx.getSource().getServer()).registry(), p.getBlockX(), p.getBlockZ());
				return ok(ctx.getSource(), c == null ? "You are on unclaimed land." : "You are in " + c.name + ".", false);
			}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> expandTree() {
		return literal("expand").then(arg("city", StringArgumentType.word())
			.then(arg("newName", StringArgumentType.word())
				.then(arg("x1", IntegerArgumentType.integer())
					.then(arg("z1", IntegerArgumentType.integer())
						.then(arg("x2", IntegerArgumentType.integer())
							.then(arg("z2", IntegerArgumentType.integer()).executes(wrap((ctx, e) -> {
								Rect r = Rect.of(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "z1"),
									IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "z2"));
								City[] out = new City[1];
								String err = com.cydev.mod.expansion.newcity.ExpansionService.expand(e.cities().registry(), e.city(),
									e.player().getUuid(), StringArgumentType.getString(ctx, "newName"), r, e.now(), out);
								if (err != null) {
									return fail(e.src(), err);
								}
								ZoneData zones = ZoneData.get(e.src().getServer());
								zones.registry().transfer(e.city().id, out[0].id, r);
								zones.markDirty();
								e.save();
								return ok(e.src(), "New city " + out[0].name + " created from " + e.city().name
									+ ". An election for its first Mayor is open; " + e.city().mayorName
									+ " remains Mayor of " + e.city().name + ".", true);
							}))))))));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> federationTree() {
		return literal("federation")
			.then(literal("create").then(arg("city", StringArgumentType.word())
				.then(arg("name", StringArgumentType.word()).executes(wrap((ctx, e) -> {
					Federation[] out = new Federation[1];
					String err = FederationService.create(e.recs().federations(), e.city(), e.player().getUuid(),
						StringArgumentType.getString(ctx, "name"), out);
					if (err != null) {
						return fail(e.src(), err);
					}
					e.save();
					return ok(e.src(), "Federation " + out[0].name + " founded by " + e.city().name + ".", true);
				})))))
			.then(literal("propose").then(arg("city", StringArgumentType.word())
				.then(arg("target", StringArgumentType.word()).executes(wrap((ctx, e) -> {
					City target = e.cities().findByName(StringArgumentType.getString(ctx, "target"));
					Federation fed = FederationService.federationOf(e.recs().federations(), e.city().id);
					if (target == null || fed == null) {
						return fail(e.src(), "Unknown target city, or your city is not in a federation.");
					}
					String err = FederationService.propose(e.recs().federations(), fed, e.city(), e.player().getUuid(), target);
					if (err != null) {
						return fail(e.src(), err);
					}
					e.save();
					return ok(e.src(), "Proposed adding " + target.name + " to " + fed.name + ".", true);
				})))))
			.then(literal("vote").then(arg("city", StringArgumentType.word())
				.then(arg("target", StringArgumentType.word()).executes(wrap((ctx, e) -> {
					City target = e.cities().findByName(StringArgumentType.getString(ctx, "target"));
					Federation fed = FederationService.federationOf(e.recs().federations(), e.city().id);
					if (target == null || fed == null) {
						return fail(e.src(), "Unknown target city, or your city is not in a federation.");
					}
					String err = FederationService.vote(e.recs().federations(), fed, e.city(), e.player().getUuid(), target.id, null);
					if (err != null) {
						return fail(e.src(), err);
					}
					e.save();
					return ok(e.src(), "Vote recorded.", false);
				})))))
			.then(literal("accept").then(arg("city", StringArgumentType.word())
				.then(arg("federation", StringArgumentType.word()).executes(wrap((ctx, e) -> {
					Federation fed = null;
					for (Federation f : e.recs().federations()) {
						if (f.name.equalsIgnoreCase(StringArgumentType.getString(ctx, "federation"))) {
							fed = f;
						}
					}
					if (fed == null) {
						return fail(e.src(), "Unknown federation.");
					}
					String err = FederationService.accept(fed, e.city(), e.player().getUuid());
					if (err != null) {
						return fail(e.src(), err);
					}
					e.save();
					return ok(e.src(), "Accepted. " + (fed.members.contains(e.city().id) ? e.city().name + " has joined " + fed.name + "!"
						: "Waiting for the member mayors' majority."), true);
				})))))
			.then(literal("list").executes(ctx -> {
				RecordsData recs = RecordsData.get(ctx.getSource().getServer());
				CityData cities = CityData.get(ctx.getSource().getServer());
				if (recs.federations().isEmpty()) {
					return ok(ctx.getSource(), "No federations.", false);
				}
				for (Federation f : recs.federations()) {
					List<String> names = new ArrayList<>();
					for (java.util.UUID id : f.members) {
						City c = cities.registry().all().get(id);
						names.add(c == null ? "?" : c.name + " (Mayor " + (c.hasMayor() ? c.mayorName : "none") + ")");
					}
					ok(ctx.getSource(), f.name + ": " + String.join(", ", names), false);
				}
				return 1;
			}));
	}

	// ---------- sample scenario ----------

	private static int loadScenario(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		CityData cities = CityData.get(src.getServer());
		RecordsData recs = RecordsData.get(src.getServer());
		if (cities.findByName("Demo") != null) {
			return fail(src, "The Demo city already exists.");
		}
		City city = cities.registry().create("Demo");
		String err = ExpansionService.claim(cities.registry(), city, Rect.of(0, 0, 999, 999));
		if (err != null) {
			cities.registry().all().remove(city.id);
			return fail(src, "Cannot place Demo: " + err);
		}
		city.wards.add(new Ward(java.util.UUID.randomUUID(), 1, "Riverside"));
		city.wards.add(new Ward(java.util.UUID.randomUUID(), 2, "Market"));
		city.wards.add(new Ward(java.util.UUID.randomUUID(), 3, "Hillside"));
		for (Ward w : city.wards) {
			w.population = 1500;
		}
		CityRecords rec = recs.of(city.id);
		BudgetLine roads = rec.addLine(2083, "Roads & Drainage", 1_000_000, "Municipal revenue");
		roads.assemblyApproved = true;
		BudgetLine admin = rec.addLine(2083, "Administration", 250_000, "Municipal revenue");
		admin.assemblyApproved = true;
		Ward hill = city.ward(3);
		ProjectProposal pr = new ProjectProposal(rec.nextProjectNo++, hill.id, "Hillside road and drainage upgrade",
			"Hillside main road", 1, 350_000, 1200, roads);
		rec.projects.add(pr);
		cities.markDirty();
		recs.markDirty();
		ok(src, "Demo city loaded: 3 wards, 2 approved budget lines, draft project #" + pr.projectNo + ".", true);
		ok(src, "Next: hold elections (/cydev city Demo election forcestart), appoint staff, then walk project #1 through "
			+ "/cydev project move Demo 1 submitted ... (the Hillside Ward Chair submits it).", false);
		return 1;
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

package com.cydev.mod.world.persistence;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.expansion.federation.Federation;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.workflow.AuditEntry;
import com.cydev.mod.government.workflow.CaseStatus;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.government.workflow.ServiceType;
import com.cydev.mod.sim.city.CityRecords;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

/** Saves cases, projects, budgets, audit logs and federations (data/cydev_records.dat). */
public class RecordsData extends PersistentState {
	private static final Codec<AuditEntry> AUDIT_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("actor").forGetter(AuditEntry::actorId),
		Codec.STRING.fieldOf("actorName").forGetter(AuditEntry::actorName),
		Codec.STRING.fieldOf("role").forGetter(e -> e.role().name()),
		Codec.STRING.fieldOf("action").forGetter(AuditEntry::action),
		Uuids.CODEC.fieldOf("record").forGetter(AuditEntry::recordId),
		Codec.STRING.fieldOf("old").forGetter(e -> e.oldStatus().name()),
		Codec.STRING.fieldOf("new").forGetter(e -> e.newStatus().name()),
		Codec.LONG.fieldOf("time").forGetter(AuditEntry::timestamp),
		Codec.STRING.optionalFieldOf("reason").forGetter(e -> Optional.ofNullable(e.reason()))
	).apply(i, (actor, name, role, action, record, old, now, time, reason) -> new AuditEntry(
		actor, name, Position.valueOf(role), action, record, CaseStatus.valueOf(old), CaseStatus.valueOf(now),
		time, reason.orElse(null))));

	private static final Codec<ServiceCase> CASE_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(c -> c.id),
		Codec.INT.fieldOf("no").forGetter(c -> c.caseNo),
		Uuids.CODEC.fieldOf("applicant").forGetter(c -> c.applicantId),
		Codec.STRING.fieldOf("applicantName").forGetter(c -> c.applicantName),
		Uuids.CODEC.fieldOf("ward").forGetter(c -> c.wardId),
		Codec.STRING.fieldOf("type").forGetter(c -> c.type.name()),
		Codec.STRING.listOf().fieldOf("evidence").forGetter(c -> c.evidence),
		Codec.LONG.fieldOf("created").forGetter(c -> c.created),
		Codec.STRING.fieldOf("status").forGetter(c -> c.status.name()),
		Codec.STRING.optionalFieldOf("returnStatus").forGetter(c -> Optional.ofNullable(c.returnStatus).map(Enum::name)),
		Codec.STRING.fieldOf("assigned").forGetter(c -> c.assignedRole.name()),
		Codec.LONG.fieldOf("updated").forGetter(c -> c.updated),
		Codec.INT.optionalFieldOf("rating", 0).forGetter(c -> c.rating),
		AUDIT_CODEC.listOf().fieldOf("history").forGetter(c -> c.history)
	).apply(i, (id, no, applicant, applicantName, ward, type, evidence, created, status, returnStatus,
				assigned, updated, rating, history) -> {
		ServiceCase c = new ServiceCase(id, no, applicant, applicantName, ward, ServiceType.valueOf(type), created);
		c.evidence.addAll(evidence);
		c.status = CaseStatus.valueOf(status);
		c.returnStatus = returnStatus.map(CaseStatus::valueOf).orElse(null);
		c.assignedRole = Position.valueOf(assigned);
		c.updated = updated;
		c.rating = rating;
		c.history.addAll(history);
		return c;
	}));

	private static final Codec<BudgetLine> LINE_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(b -> b.id),
		Codec.INT.fieldOf("no").forGetter(b -> b.lineNo),
		Codec.INT.fieldOf("year").forGetter(b -> b.fiscalYear),
		Codec.STRING.fieldOf("owner").forGetter(b -> b.owner),
		Codec.LONG.fieldOf("allocated").forGetter(b -> b.allocated),
		Codec.LONG.fieldOf("committed").forGetter(b -> b.committed),
		Codec.LONG.fieldOf("spent").forGetter(b -> b.spent),
		Codec.STRING.fieldOf("source").forGetter(b -> b.fundingSource),
		Codec.BOOL.fieldOf("approved").forGetter(b -> b.assemblyApproved),
		Uuids.CODEC.listOf().fieldOf("yes").forGetter(b -> b.assemblyYes),
		Uuids.CODEC.listOf().fieldOf("no_votes").forGetter(b -> b.assemblyNo)
	).apply(i, (id, no, year, owner, allocated, committed, spent, source, approved, yes, noVotes) -> {
		BudgetLine b = new BudgetLine(id, no, year, owner, allocated, source);
		b.committed = committed;
		b.spent = spent;
		b.assemblyApproved = approved;
		b.assemblyYes.addAll(yes);
		b.assemblyNo.addAll(noVotes);
		return b;
	}));

	/** Project state flags, grouped to stay under the codec field limit. */
	private record Flags(String status, Optional<String> returnStatus, boolean technical, boolean budget,
						 Optional<String> contractor, int progress, boolean stopped, boolean banned,
						 Optional<String> orderReason) {
	}

	private static final Codec<Flags> FLAGS_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("status").forGetter(Flags::status),
		Codec.STRING.optionalFieldOf("returnStatus").forGetter(Flags::returnStatus),
		Codec.BOOL.fieldOf("technical").forGetter(Flags::technical),
		Codec.BOOL.fieldOf("budget").forGetter(Flags::budget),
		Codec.STRING.optionalFieldOf("contractor").forGetter(Flags::contractor),
		Codec.INT.fieldOf("progress").forGetter(Flags::progress),
		Codec.BOOL.fieldOf("stopped").forGetter(Flags::stopped),
		Codec.BOOL.fieldOf("banned").forGetter(Flags::banned),
		Codec.STRING.optionalFieldOf("orderReason").forGetter(Flags::orderReason)
	).apply(i, Flags::new));

	/** A project plus the id of its budget line, linked after loading. */
	private record ProjectDto(ProjectProposal project, UUID lineId) {
	}

	private static final Codec<ProjectDto> PROJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(d -> d.project.id),
		Codec.INT.fieldOf("no").forGetter(d -> d.project.projectNo),
		Uuids.CODEC.fieldOf("ward").forGetter(d -> d.project.wardId),
		Codec.STRING.fieldOf("title").forGetter(d -> d.project.title),
		Codec.STRING.fieldOf("location").forGetter(d -> d.project.location),
		Codec.INT.fieldOf("priority").forGetter(d -> d.project.priority),
		Codec.LONG.fieldOf("estimate").forGetter(d -> d.project.estimate),
		Codec.INT.fieldOf("beneficiaries").forGetter(d -> d.project.beneficiaries),
		Uuids.CODEC.fieldOf("line").forGetter(ProjectDto::lineId),
		FLAGS_CODEC.fieldOf("state").forGetter(d -> new Flags(d.project.status.name(),
			Optional.ofNullable(d.project.returnStatus).map(Enum::name), d.project.technicallyCleared,
			d.project.budgetCleared, Optional.ofNullable(d.project.contractor), d.project.progressPercent,
			d.project.stopped, d.project.banned, Optional.ofNullable(d.project.orderReason))),
		AUDIT_CODEC.listOf().fieldOf("history").forGetter(d -> d.project.history)
	).apply(i, (id, no, ward, title, location, priority, estimate, beneficiaries, line, f, history) -> {
		ProjectProposal p = new ProjectProposal(id, no, ward, title, location, priority, estimate, beneficiaries, null);
		p.status = CaseStatus.valueOf(f.status());
		p.returnStatus = f.returnStatus().map(CaseStatus::valueOf).orElse(null);
		p.technicallyCleared = f.technical();
		p.budgetCleared = f.budget();
		p.contractor = f.contractor().orElse(null);
		p.progressPercent = f.progress();
		p.stopped = f.stopped();
		p.banned = f.banned();
		p.orderReason = f.orderReason().orElse(null);
		p.history.addAll(history);
		return new ProjectDto(p, line);
	}));

	private static final Codec<CityRecords> RECORDS_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("city").forGetter(r -> r.cityId),
		Codec.INT.fieldOf("nextCase").forGetter(r -> r.nextCaseNo),
		Codec.INT.fieldOf("nextProject").forGetter(r -> r.nextProjectNo),
		Codec.INT.fieldOf("nextBudget").forGetter(r -> r.nextBudgetNo),
		LINE_CODEC.listOf().fieldOf("lines").forGetter(r -> r.budgetLines),
		CASE_CODEC.listOf().fieldOf("cases").forGetter(r -> r.cases),
		PROJECT_CODEC.listOf().fieldOf("projects").forGetter(r -> {
			List<ProjectDto> dtos = new ArrayList<>();
			for (ProjectProposal p : r.projects) {
				dtos.add(new ProjectDto(p, p.budgetLine.id));
			}
			return dtos;
		}),
		AUDIT_CODEC.listOf().fieldOf("audit").forGetter(r -> r.auditLog)
	).apply(i, (city, nc, np, nb, lines, cases, projects, audit) -> {
		CityRecords r = new CityRecords(city);
		r.nextCaseNo = nc;
		r.nextProjectNo = np;
		r.nextBudgetNo = nb;
		r.budgetLines.addAll(lines);
		r.cases.addAll(cases);
		for (ProjectDto d : projects) {
			for (BudgetLine b : r.budgetLines) {
				if (b.id.equals(d.lineId())) {
					d.project().budgetLine = b;
				}
			}
			r.projects.add(d.project());
		}
		r.auditLog.addAll(audit);
		return r;
	}));

	private static final Codec<Federation> FEDERATION_CODEC = RecordCodecBuilder.create(i -> i.group(
		Uuids.CODEC.fieldOf("id").forGetter(f -> f.id),
		Codec.STRING.fieldOf("name").forGetter(f -> f.name),
		Uuids.CODEC.listOf().fieldOf("members").forGetter(f -> f.members),
		RecordCodecBuilder.<Federation.Proposal>create(j -> j.group(
			Uuids.CODEC.fieldOf("city").forGetter(p -> p.cityId),
			Uuids.CODEC.listOf().fieldOf("yes").forGetter(p -> p.yes),
			Codec.BOOL.fieldOf("accepted").forGetter(p -> p.accepted)
		).apply(j, (city, yes, accepted) -> {
			Federation.Proposal p = new Federation.Proposal(city);
			p.yes.addAll(yes);
			p.accepted = accepted;
			return p;
		})).listOf().fieldOf("proposals").forGetter(f -> f.proposals)
	).apply(i, (id, name, members, proposals) -> {
		Federation f = new Federation(id, name);
		f.members.addAll(members);
		f.proposals.addAll(proposals);
		return f;
	}));

	private static final Codec<RecordsData> CODEC = RecordCodecBuilder.create(i -> i.group(
		RECORDS_CODEC.listOf().fieldOf("records").forGetter(d -> new ArrayList<>(d.records.values())),
		FEDERATION_CODEC.listOf().fieldOf("federations").forGetter(d -> d.federations)
	).apply(i, (records, federations) -> {
		RecordsData d = new RecordsData();
		records.forEach(r -> d.records.put(r.cityId, r));
		d.federations.addAll(federations);
		return d;
	}));

	private static final PersistentStateType<RecordsData> TYPE =
		new PersistentStateType<>(CydevMod.MOD_ID + "_records", RecordsData::new, CODEC, null);

	private final Map<UUID, CityRecords> records = new HashMap<>();
	private final List<Federation> federations = new ArrayList<>();

	public CityRecords of(UUID cityId) {
		return records.computeIfAbsent(cityId, CityRecords::new);
	}

	public List<Federation> federations() {
		return federations;
	}

	public static RecordsData get(MinecraftServer server) {
		return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE);
	}
}

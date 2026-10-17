package com.cydev.mod.government;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cydev.mod.expansion.federation.Federation;
import com.cydev.mod.expansion.federation.FederationService;
import com.cydev.mod.expansion.newcity.ExpansionService;
import com.cydev.mod.government.council.AssemblyService;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.election.ElectionState;
import com.cydev.mod.government.mayor.ScoreService;
import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.project.ProjectService;
import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.government.workflow.CaseStatus;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.government.workflow.ServiceType;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRecords;
import com.cydev.mod.sim.city.CityRegistry;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.city.Ward;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdministrationTest {
	static final long T0 = 5_000_000L;

	City cityWithMayor(String name) {
		City c = new City(UUID.randomUUID(), name);
		c.mayorId = UUID.randomUUID();
		c.mayorName = "Mayor of " + name;
		return c;
	}

	// ---- assembly ----

	@Test
	void budgetNeedsAssemblyMajority() {
		City city = cityWithMayor("ABC");
		city.deputyId = UUID.randomUUID();
		Ward w1 = new Ward(UUID.randomUUID(), 1, "One");
		w1.chairId = UUID.randomUUID();
		city.wards.add(w1);
		BudgetLine line = new BudgetLine(1, 2083, "Roads", 1000, "Municipal");

		assertNotNull(AssemblyService.vote(city, line, UUID.randomUUID(), true), "outsider cannot vote");
		assertNull(AssemblyService.vote(city, line, city.mayorId, true));
		assertTrue(!line.assemblyApproved, "1 of 3 is not a majority");
		assertNotNull(AssemblyService.vote(city, line, city.mayorId, true), "no double vote");
		assertNull(AssemblyService.vote(city, line, w1.chairId, true));
		assertTrue(line.assemblyApproved, "2 of 3 passes");
	}

	@Test
	void projectCannotBeApprovedWithoutAssemblyApproval() {
		List<com.cydev.mod.government.workflow.AuditEntry> log = new ArrayList<>();
		ProjectService svc = new ProjectService(log);
		UUID wardId = UUID.randomUUID();
		Actor chair = new Actor(UUID.randomUUID(), "c", Position.WARD_CHAIR, wardId);
		Actor eng = new Actor(UUID.randomUUID(), "e", Position.ENGINEER, null);
		Actor acc = new Actor(UUID.randomUUID(), "a", Position.ACCOUNTS_OFFICER, null);
		Actor mayor = new Actor(UUID.randomUUID(), "m", Position.MAYOR, null);
		BudgetLine line = new BudgetLine(1, 2083, "Roads", 1000, "Municipal");
		ProjectProposal p = new ProjectProposal(1, wardId, "Road", "x", 1, 100, 10, line);
		svc.move(p, chair, CaseStatus.SUBMITTED, null, T0);
		svc.move(p, eng, CaseStatus.TECHNICAL_REVIEW, null, T0);
		svc.move(p, eng, CaseStatus.BUDGET_REVIEW, null, T0);
		assertNotNull(svc.budgetCheck(p, acc, T0), "line not passed by assembly");
		assertNotNull(svc.move(p, mayor, CaseStatus.APPROVED, null, T0));
		line.assemblyApproved = true;
		assertNull(svc.budgetCheck(p, acc, T0));
		assertNull(svc.move(p, mayor, CaseStatus.APPROVED, null, T0));
	}

	// ---- stop / ban ----

	ProjectProposal approvedProject(ProjectService svc, UUID wardId) {
		Actor chair = new Actor(UUID.randomUUID(), "c", Position.WARD_CHAIR, wardId);
		Actor eng = new Actor(UUID.randomUUID(), "e", Position.ENGINEER, null);
		Actor acc = new Actor(UUID.randomUUID(), "a", Position.ACCOUNTS_OFFICER, null);
		Actor mayor = new Actor(UUID.randomUUID(), "m", Position.MAYOR, null);
		BudgetLine line = new BudgetLine(1, 2083, "Roads", 1000, "Municipal");
		line.assemblyApproved = true;
		ProjectProposal p = new ProjectProposal(1, wardId, "Road", "x", 1, 400, 10, line);
		svc.move(p, chair, CaseStatus.SUBMITTED, null, T0);
		svc.move(p, eng, CaseStatus.TECHNICAL_REVIEW, null, T0);
		svc.move(p, eng, CaseStatus.BUDGET_REVIEW, null, T0);
		svc.budgetCheck(p, acc, T0);
		assertNull(svc.move(p, mayor, CaseStatus.APPROVED, null, T0));
		return p;
	}

	@Test
	void stopOrderPausesWorkAndCanBeLifted() {
		ProjectService svc = new ProjectService(new ArrayList<>());
		UUID wardId = UUID.randomUUID();
		ProjectProposal p = approvedProject(svc, wardId);
		Actor admin = new Actor(UUID.randomUUID(), "ad", Position.ADMIN_OFFICER, null);
		Actor inspector = new Actor(UUID.randomUUID(), "i", Position.INSPECTOR, null);
		Actor resident = new Actor(UUID.randomUUID(), "r", Position.RESIDENT, wardId);
		Actor otherChair = new Actor(UUID.randomUUID(), "oc", Position.WARD_CHAIR, UUID.randomUUID());

		assertNotNull(svc.stop(p, resident, "because", T0), "residents cannot stop works");
		assertNotNull(svc.stop(p, otherChair, "not my ward", T0));
		assertNotNull(svc.stop(p, inspector, " ", T0), "reason required");
		assertNull(svc.stop(p, inspector, "Unsafe excavation", T0));
		assertNotNull(svc.move(p, admin, CaseStatus.PROCUREMENT, null, T0), "stopped work cannot progress");
		assertNull(svc.lift(p, inspector, "Fixed", T0));
		assertNull(svc.move(p, admin, CaseStatus.PROCUREMENT, null, T0));
	}

	@Test
	void banReleasesCommittedFundsAndIsPermanent() {
		ProjectService svc = new ProjectService(new ArrayList<>());
		UUID wardId = UUID.randomUUID();
		ProjectProposal p = approvedProject(svc, wardId);
		Actor mayor = new Actor(UUID.randomUUID(), "m", Position.MAYOR, null);
		Actor inspector = new Actor(UUID.randomUUID(), "i", Position.INSPECTOR, null);
		assertEquals(400, p.budgetLine.committed);
		assertNotNull(svc.ban(p, inspector, "x", T0), "only the mayor bans");
		assertNull(svc.ban(p, mayor, "Illegal encroachment", T0));
		assertEquals(0, p.budgetLine.committed);
		assertNotNull(svc.lift(p, mayor, "oops", T0), "ban cannot be lifted");
		assertNotNull(svc.move(p, new Actor(UUID.randomUUID(), "ad", Position.ADMIN_OFFICER, null), CaseStatus.PROCUREMENT, null, T0));
	}

	// ---- dispute / judicial ----

	@Test
	void disputeGoesToDeputyMayorNotMayor() {
		CityRecords rec = new CityRecords(UUID.randomUUID());
		UUID wardId = UUID.randomUUID();
		Actor resident = new Actor(UUID.randomUUID(), "r", Position.RESIDENT, wardId);
		Actor secretary = new Actor(UUID.randomUUID(), "s", Position.WARD_SECRETARY, wardId);
		Actor deputy = new Actor(UUID.randomUUID(), "d", Position.DEPUTY_MAYOR, null);
		Actor mayor = new Actor(UUID.randomUUID(), "m", Position.MAYOR, null);
		ServiceCase c = new ServiceCase(resident.id(), "r", wardId, ServiceType.DISPUTE_MEDIATION, T0);
		c.evidence.add("boundary dispute notes");
		var wf = rec.workflow();
		assertNull(wf.move(c, resident, CaseStatus.SUBMITTED, null, T0));
		assertNull(wf.move(c, secretary, CaseStatus.WARD_REVIEW, null, T0));
		assertNotNull(wf.move(c, secretary, CaseStatus.ISSUED, null, T0), "ward cannot decide disputes");
		assertNull(wf.move(c, secretary, CaseStatus.REFERRED, "Judicial matter", T0));
		assertNotNull(wf.move(c, mayor, CaseStatus.DEPARTMENT_REVIEW, null, T0));
		assertNull(wf.move(c, deputy, CaseStatus.DEPARTMENT_REVIEW, null, T0));
		assertNull(wf.move(c, deputy, CaseStatus.APPROVED, null, T0));
	}

	// ---- score ----

	@Test
	void scoreReflectsRatingsAndOverdueCases() {
		CityRecords rec = new CityRecords(UUID.randomUUID());
		UUID wardId = UUID.randomUUID();
		for (int i = 0; i < 2; i++) {
			ServiceCase c = new ServiceCase(UUID.randomUUID(), "r", wardId, ServiceType.RESIDENCE_CERTIFICATE, T0);
			c.status = CaseStatus.ISSUED;
			c.rating = 5;
			rec.cases.add(c);
		}
		ServiceCase stuck = new ServiceCase(UUID.randomUUID(), "r", wardId, ServiceType.BUILDING_PERMIT, T0);
		stuck.status = CaseStatus.DEPARTMENT_REVIEW;
		rec.cases.add(stuck);

		ScoreService.Score early = ScoreService.compute(rec, T0 + 1000);
		ScoreService.Score late = ScoreService.compute(rec, T0 + ScoreService.CASE_SLA_MS + 1000);
		assertEquals(100, early.satisfaction());
		assertEquals(0, early.casesOverdue());
		assertEquals(1, late.casesOverdue());
		assertTrue(late.overall() < early.overall(), "overdue work lowers the score");
	}

	// ---- land / expansion ----

	@Test
	void rectSubtractLeavesNoOverlapAndSameArea() {
		Rect whole = Rect.of(0, 0, 99, 99);
		Rect cut = Rect.of(40, 40, 59, 59);
		List<Rect> rest = whole.subtract(cut);
		long area = 0;
		for (Rect r : rest) {
			area += r.area();
			assertTrue(!r.overlaps(cut));
		}
		assertEquals(whole.area() - cut.area(), area);
	}

	@Test
	void mayorCanCarveNewCityWhoseElectionStarts() {
		CityRegistry reg = new CityRegistry();
		City abc = reg.create("ABC");
		abc.mayorId = UUID.randomUUID();
		abc.mayorName = "Aman Pandey";
		assertNull(ExpansionService.claim(reg, abc, Rect.of(0, 0, 99, 99)));

		City[] out = new City[1];
		assertNotNull(ExpansionService.expand(reg, abc, UUID.randomUUID(), "XYZ", Rect.of(60, 60, 99, 99), T0, out), "only the mayor");
		assertNotNull(ExpansionService.expand(reg, abc, abc.mayorId, "XYZ", Rect.of(80, 80, 150, 150), T0, out), "outside land");
		assertNull(ExpansionService.expand(reg, abc, abc.mayorId, "XYZ", Rect.of(60, 60, 99, 99), T0, out));

		City xyz = out[0];
		assertEquals(abc.id, xyz.parentId);
		assertEquals(ElectionState.NOMINATION, xyz.election.state);
		assertEquals("Aman Pandey", abc.mayorName, "original mayor keeps the original city");
		assertEquals(xyz, ExpansionService.cityAt(reg, 70, 70));
		assertEquals(abc, ExpansionService.cityAt(reg, 10, 10));
		assertNotNull(ExpansionService.claim(reg, abc, Rect.of(65, 65, 70, 70)), "land already belongs to XYZ");
	}

	// ---- federation ----

	@Test
	void federationAdmitsCityOnMajorityAndOwnMayorAcceptance() {
		List<Federation> all = new ArrayList<>();
		City a = cityWithMayor("ABC");
		City b = cityWithMayor("XYZ");
		City c = cityWithMayor("PQR");
		Federation[] out = new Federation[1];
		assertNull(FederationService.create(all, a, a.mayorId, "Union", out));
		Federation fed = out[0];

		assertNull(FederationService.propose(all, fed, a, a.mayorId, b));
		assertTrue(!fed.members.contains(b.id), "XYZ's mayor must accept first");
		assertNull(FederationService.accept(fed, b, b.mayorId));
		assertTrue(fed.members.contains(b.id));

		assertNull(FederationService.propose(all, fed, a, a.mayorId, c));
		assertNull(FederationService.accept(fed, c, c.mayorId));
		assertTrue(!fed.members.contains(c.id), "1 of 2 member mayors is not a majority");
		assertNull(FederationService.vote(all, fed, b, b.mayorId, c.id, null));
		assertTrue(fed.members.contains(c.id));
	}
}

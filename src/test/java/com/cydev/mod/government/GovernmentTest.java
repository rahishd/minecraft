package com.cydev.mod.government;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.government.election.ElectionState;
import com.cydev.mod.government.mayor.MayorTerm;
import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.project.ProjectService;
import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.government.workflow.AuditEntry;
import com.cydev.mod.government.workflow.AuthorityRules;
import com.cydev.mod.government.workflow.CaseStatus;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.government.workflow.ServiceType;
import com.cydev.mod.government.workflow.WorkflowService;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Ward;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernmentTest {
	static final long T0 = 1_000_000L;

	final List<AuditEntry> log = new ArrayList<>();
	final WorkflowService workflow = new WorkflowService(new AuthorityRules(), log);
	final ProjectService projects = new ProjectService(log);
	final UUID wardId = UUID.randomUUID();

	final Actor resident = new Actor(UUID.randomUUID(), "Sita", Position.RESIDENT, wardId);
	final Actor secretary = new Actor(UUID.randomUUID(), "Ram", Position.WARD_SECRETARY, wardId);
	final Actor chair = new Actor(UUID.randomUUID(), "Hari", Position.WARD_CHAIR, wardId);
	final Actor otherWardSecretary = new Actor(UUID.randomUUID(), "Gita", Position.WARD_SECRETARY, UUID.randomUUID());
	final Actor engineer = new Actor(UUID.randomUUID(), "Eng", Position.ENGINEER, null);
	final Actor accounts = new Actor(UUID.randomUUID(), "Acc", Position.ACCOUNTS_OFFICER, null);
	final Actor admin = new Actor(UUID.randomUUID(), "Adm", Position.ADMIN_OFFICER, null);
	final Actor inspector = new Actor(UUID.randomUUID(), "Insp", Position.INSPECTOR, null);
	final Actor mayor = new Actor(UUID.randomUUID(), "Aman", Position.MAYOR, null);

	ServiceCase submitted(ServiceType type) {
		ServiceCase c = new ServiceCase(resident.id(), resident.name(), wardId, type, T0);
		c.evidence.add("citizenship copy");
		assertNull(workflow.move(c, resident, CaseStatus.SUBMITTED, null, T0));
		assertNull(workflow.move(c, secretary, CaseStatus.WARD_REVIEW, null, T0));
		return c;
	}

	@Test
	void wardIssuesSimpleServiceDirectly() {
		ServiceCase c = submitted(ServiceType.RESIDENCE_CERTIFICATE);
		assertNull(workflow.move(c, secretary, CaseStatus.ISSUED, null, T0));
		assertEquals(CaseStatus.ISSUED, c.status);
		assertNull(workflow.move(c, resident, CaseStatus.CLOSED, null, T0));
	}

	@Test
	void wardCannotIssueMunicipalServiceButCanRefer() {
		ServiceCase c = submitted(ServiceType.BUILDING_PERMIT);
		assertNotNull(workflow.move(c, secretary, CaseStatus.ISSUED, null, T0));
		assertNotNull(workflow.move(c, secretary, CaseStatus.REFERRED, "", T0), "reason required");
		assertNull(workflow.move(c, secretary, CaseStatus.REFERRED, "Needs engineering review", T0));
		assertEquals(Position.ENGINEER, c.assignedRole);
	}

	@Test
	void referralRoutesToCorrectDepartment() {
		ServiceCase c = submitted(ServiceType.BUSINESS_REGISTRATION);
		workflow.move(c, secretary, CaseStatus.REFERRED, "Revenue matter", T0);
		assertNotNull(workflow.move(c, engineer, CaseStatus.DEPARTMENT_REVIEW, null, T0), "wrong department");
		Actor revenue = new Actor(UUID.randomUUID(), "Rev", Position.REVENUE_OFFICER, null);
		assertNull(workflow.move(c, revenue, CaseStatus.DEPARTMENT_REVIEW, null, T0));
	}

	@Test
	void mayorCannotApproveCitizenServices() {
		ServiceCase c = submitted(ServiceType.BUILDING_PERMIT);
		workflow.move(c, secretary, CaseStatus.REFERRED, "Technical", T0);
		workflow.move(c, engineer, CaseStatus.DEPARTMENT_REVIEW, null, T0);
		assertNotNull(workflow.move(c, mayor, CaseStatus.APPROVED, null, T0));
		assertEquals(CaseStatus.DEPARTMENT_REVIEW, c.status);
		assertNull(workflow.move(c, engineer, CaseStatus.APPROVED, null, T0));
	}

	@Test
	void otherWardStaffCannotActOnCase() {
		ServiceCase c = new ServiceCase(resident.id(), "Sita", wardId, ServiceType.RESIDENCE_CERTIFICATE, T0);
		workflow.move(c, resident, CaseStatus.SUBMITTED, null, T0);
		assertNotNull(workflow.move(c, otherWardSecretary, CaseStatus.WARD_REVIEW, null, T0));
	}

	@Test
	void missingDocumentsReturnedWithReasonThenResubmitted() {
		ServiceCase c = submitted(ServiceType.RESIDENCE_CERTIFICATE);
		c.evidence.clear();
		assertNotNull(workflow.move(c, secretary, CaseStatus.ISSUED, null, T0), "needs documents");
		assertNotNull(workflow.move(c, secretary, CaseStatus.MORE_INFO_REQUIRED, null, T0), "reason required");
		assertNull(workflow.move(c, secretary, CaseStatus.MORE_INFO_REQUIRED, "Bring citizenship copy", T0));
		c.evidence.add("citizenship copy");
		assertNull(workflow.move(c, resident, CaseStatus.WARD_REVIEW, null, T0));
	}

	@Test
	void staffCannotProcessTheirOwnCase() {
		ServiceCase c = new ServiceCase(secretary.id(), "Ram", wardId, ServiceType.RESIDENCE_CERTIFICATE, T0);
		c.evidence.add("doc");
		workflow.move(c, secretary, CaseStatus.SUBMITTED, null, T0);
		assertNotNull(workflow.move(c, secretary, CaseStatus.WARD_REVIEW, null, T0));
	}

	@Test
	void everyStatusChangeIsAudited() {
		int before = log.size();
		ServiceCase c = submitted(ServiceType.RESIDENCE_CERTIFICATE);
		workflow.move(c, secretary, CaseStatus.ISSUED, null, T0);
		assertEquals(before + 3, log.size());
		AuditEntry last = c.history.get(c.history.size() - 1);
		assertEquals(CaseStatus.WARD_REVIEW, last.oldStatus());
		assertEquals(CaseStatus.ISSUED, last.newStatus());
		assertEquals(Position.WARD_SECRETARY, last.role());
	}

	@Test
	void invalidTransitionIsRejected() {
		ServiceCase c = new ServiceCase(resident.id(), "Sita", wardId, ServiceType.RESIDENCE_CERTIFICATE, T0);
		assertNotNull(workflow.move(c, resident, CaseStatus.ISSUED, null, T0));
		assertEquals(CaseStatus.DRAFT, c.status);
	}

	// ---- projects ----

	ProjectProposal proposalToBudgetReview(long estimate, long allocated) {
		BudgetLine line = new BudgetLine(1, 2083, "Roads & Drainage", allocated, "Municipal revenue");
		line.assemblyApproved = true;
		ProjectProposal p = new ProjectProposal(1, wardId, "Drainage", "Ward 3 main road", 1, estimate, 200, line);
		assertNull(projects.move(p, chair, CaseStatus.SUBMITTED, null, T0));
		assertNull(projects.move(p, engineer, CaseStatus.TECHNICAL_REVIEW, null, T0));
		assertNull(projects.move(p, engineer, CaseStatus.BUDGET_REVIEW, null, T0));
		return p;
	}

	@Test
	void fullProjectPipelineSpendsBudget() {
		ProjectProposal p = proposalToBudgetReview(500, 1000);
		assertNull(projects.budgetCheck(p, accounts, T0));
		assertNull(projects.move(p, mayor, CaseStatus.APPROVED, null, T0));
		assertEquals(500, p.budgetLine.committed);
		assertNull(projects.move(p, admin, CaseStatus.PROCUREMENT, null, T0));
		assertNotNull(projects.move(p, admin, CaseStatus.IN_PROGRESS, null, T0), "no contractor yet");
		assertNull(projects.assignContractor(p, admin, "Everest Builders"));
		assertNull(projects.move(p, admin, CaseStatus.IN_PROGRESS, null, T0));
		Actor contractor = new Actor(UUID.randomUUID(), "Everest", Position.CONTRACTOR, null);
		assertNull(projects.move(p, contractor, CaseStatus.INSPECTION, null, T0));
		assertNull(projects.move(p, inspector, CaseStatus.COMPLETED, null, T0));
		assertEquals(0, p.budgetLine.committed);
		assertEquals(500, p.budgetLine.spent);
		assertNull(projects.move(p, chair, CaseStatus.CLOSED, null, T0));
	}

	@Test
	void insufficientBudgetDefersProject() {
		ProjectProposal p = proposalToBudgetReview(5000, 1000);
		assertNotNull(projects.budgetCheck(p, accounts, T0));
		assertEquals(CaseStatus.DEFERRED, p.status);
		assertEquals(0, p.budgetLine.committed);
		assertTrue(p.history.get(p.history.size() - 1).reason().contains("Insufficient"));
	}

	@Test
	void mayorCannotApproveWithoutBudgetClearance() {
		ProjectProposal p = proposalToBudgetReview(500, 1000);
		assertNotNull(projects.move(p, mayor, CaseStatus.APPROVED, null, T0));
		assertEquals(CaseStatus.BUDGET_REVIEW, p.status);
	}

	@Test
	void mayorCannotSkipTechnicalReview() {
		BudgetLine line = new BudgetLine(1, 2083, "Roads", 1000, "Municipal");
		line.assemblyApproved = true;
		ProjectProposal p = new ProjectProposal(1, wardId, "Road", "x", 1, 100, 50, line);
		projects.move(p, chair, CaseStatus.SUBMITTED, null, T0);
		assertNotNull(projects.move(p, mayor, CaseStatus.APPROVED, null, T0));
		assertFalse(p.technicallyCleared);
	}

	@Test
	void wardChairCannotApproveOwnProject() {
		ProjectProposal p = proposalToBudgetReview(500, 1000);
		projects.budgetCheck(p, accounts, T0);
		assertNotNull(projects.move(p, chair, CaseStatus.APPROVED, null, T0));
	}

	@Test
	void rejectedAndDeferredNeedReasonAndAreRecorded() {
		ProjectProposal p = proposalToBudgetReview(500, 1000);
		projects.budgetCheck(p, accounts, T0);
		assertNotNull(projects.move(p, mayor, CaseStatus.REJECTED, "", T0));
		assertNull(projects.move(p, mayor, CaseStatus.DEFERRED, "Wait for next fiscal year", T0));
		assertEquals(CaseStatus.DEFERRED, p.status);
		assertNull(projects.move(p, chair, CaseStatus.SUBMITTED, null, T0), "ward can revise and resubmit");
	}

	// ---- elections ----

	@Test
	void mayorElectionWithDeputyAndThirtyDayTerm() {
		City city = new City(UUID.randomUUID(), "ABC");
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID v1 = UUID.randomUUID();
		UUID v2 = UUID.randomUUID();
		UUID v3 = UUID.randomUUID();
		assertNull(ElectionService.start(city, T0, false));
		assertNotNull(ElectionService.start(city, T0, false), "only one election at a time");
		assertNull(ElectionService.addCandidate(city, a, "Aman", "Roads first"));
		assertNull(ElectionService.addCandidate(city, b, "Bina", "Schools first"));
		assertNotNull(ElectionService.vote(city, v1, a), "voting not open yet");

		long votingOpens = T0 + MayorTerm.NOMINATION_MS;
		assertNotNull(ElectionService.tick(city, votingOpens));
		assertEquals(ElectionState.VOTING, city.election.state);
		assertNull(ElectionService.vote(city, v1, a));
		assertNull(ElectionService.vote(city, v2, a));
		assertNull(ElectionService.vote(city, v3, b));
		assertNotNull(ElectionService.vote(city, v1, b), "one vote per voter");

		long votingEnds = votingOpens + MayorTerm.VOTING_MS;
		assertNotNull(ElectionService.tick(city, votingEnds));
		assertEquals(a, city.mayorId);
		assertEquals(b, city.deputyId);
		assertEquals(30L * 24 * 60 * 60 * 1000, city.termEnd - city.termStart);
		assertNotNull(ElectionService.start(city, votingEnds + 1000, false), "term still running");
		assertNull(ElectionService.start(city, city.termEnd, false), "new election after term");
	}

	@Test
	void electionWithNoCandidatesIsCancelled() {
		City city = new City(UUID.randomUUID(), "ABC");
		ElectionService.start(city, T0, false);
		assertNotNull(ElectionService.tick(city, T0 + MayorTerm.NOMINATION_MS));
		assertEquals(ElectionState.CLOSED, city.election.state);
		assertFalse(city.hasMayor());
	}

	@Test
	void wardChairAndStaffAppointments() {
		City city = new City(UUID.randomUUID(), "ABC");
		Ward ward = new Ward(UUID.randomUUID(), 3, "Ward Three");
		UUID chairId = UUID.randomUUID();
		ElectionService.start(ward, T0, false);
		ElectionService.addCandidate(ward, chairId, "Hari", "Clean water");
		ElectionService.tick(ward, T0 + MayorTerm.NOMINATION_MS);
		ElectionService.vote(ward, UUID.randomUUID(), chairId);
		ElectionService.tick(ward, T0 + MayorTerm.NOMINATION_MS + MayorTerm.VOTING_MS);
		assertTrue(ward.isHolder(chairId));

		UUID clerk = UUID.randomUUID();
		assertNotNull(ElectionService.appointWard(ward, UUID.randomUUID(), clerk, "Ram", Position.WARD_SECRETARY));
		assertNull(ElectionService.appointWard(ward, chairId, clerk, "Ram", Position.WARD_SECRETARY));
		assertNotNull(ElectionService.appointWard(ward, chairId, clerk, "Ram", Position.ENGINEER), "not a ward post");
		assertEquals(1, ward.appointments.size());
		assertNotNull(ElectionService.appointMunicipal(city, UUID.randomUUID(), clerk, "Ram", Position.CAO), "no mayor yet");
	}
}

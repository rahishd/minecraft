package com.cydev.mod.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cydev.mod.expansion.federation.Federation;
import com.cydev.mod.government.council.Appointment;
import com.cydev.mod.government.council.Position;
import com.cydev.mod.government.election.ElectionService;
import com.cydev.mod.government.project.BudgetLine;
import com.cydev.mod.government.project.ProjectProposal;
import com.cydev.mod.government.workflow.Actor;
import com.cydev.mod.government.workflow.CaseStatus;
import com.cydev.mod.government.workflow.ServiceCase;
import com.cydev.mod.government.workflow.ServiceType;
import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.Rect;
import com.cydev.mod.sim.city.Ward;
import com.cydev.mod.world.persistence.CityData;
import com.cydev.mod.world.persistence.RecordsData;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Makes sure everything we save can be loaded back unchanged. */
class PersistenceRoundTripTest {
	@SuppressWarnings("unchecked")
	private static <T> Codec<T> codec(Class<?> owner) throws Exception {
		Field f = owner.getDeclaredField("CODEC");
		f.setAccessible(true);
		return (Codec<T>) f.get(null);
	}

	@Test
	void cityDataSurvivesSaveAndLoad() throws Exception {
		Codec<CityData> codec = codec(CityData.class);
		CityData data = new CityData();
		City city = data.registry().create("ABC");
		city.treasury = 1234;
		city.regions.add(Rect.of(0, 0, 99, 99));
		Ward ward = new Ward(UUID.randomUUID(), 3, "Hillside");
		ward.population = 900;
		city.wards.add(ward);
		UUID candidate = UUID.randomUUID();
		ElectionService.start(city, 1000, false);
		ElectionService.addCandidate(city, candidate, "Aman", "Roads first");
		ElectionService.start(ward, 1000, false);
		city.appointments.add(new Appointment(UUID.randomUUID(), "Eng", Position.ENGINEER));
		city.mayorId = candidate;
		city.mayorName = "Aman";
		city.termStart = 5;
		city.termEnd = 99;

		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		CityData back = codec.parse(JsonOps.INSTANCE, json).getOrThrow();

		City c2 = back.findByName("ABC");
		assertNotNull(c2);
		assertEquals(1234, c2.treasury);
		assertEquals("Aman", c2.mayorName);
		assertEquals(99, c2.termEnd);
		assertEquals(1, c2.wards.size());
		assertEquals("Hillside", c2.wards.get(0).name);
		assertNotNull(c2.wards.get(0).election);
		assertEquals(1, c2.election.candidates.size());
		assertEquals(Position.ENGINEER, c2.appointments.get(0).position());
		assertEquals(Rect.of(0, 0, 99, 99), c2.regions.get(0));
	}

	@Test
	void recordsDataSurvivesSaveAndLoad() throws Exception {
		Codec<RecordsData> codec = codec(RecordsData.class);
		RecordsData data = new RecordsData();
		UUID cityId = UUID.randomUUID();
		var rec = data.of(cityId);
		BudgetLine line = rec.addLine(2083, "Roads", 5000, "Municipal revenue");
		line.assemblyApproved = true;
		line.assemblyYes.add(UUID.randomUUID());

		UUID wardId = UUID.randomUUID();
		ServiceCase sc = new ServiceCase(UUID.randomUUID(), rec.nextCaseNo++, UUID.randomUUID(), "Sita", wardId,
			ServiceType.BUILDING_PERMIT, 10);
		sc.evidence.add("plan.pdf");
		rec.cases.add(sc);
		Actor res = new Actor(sc.applicantId, "Sita", Position.RESIDENT, wardId);
		assertEquals(null, rec.workflow().move(sc, res, CaseStatus.SUBMITTED, null, 11));
		sc.rating = 4;

		ProjectProposal pr = new ProjectProposal(rec.nextProjectNo++, wardId, "Road", "x=1 z=2", 1, 700, 50, line);
		pr.status = CaseStatus.IN_PROGRESS;
		pr.contractor = "Everest";
		pr.stopped = true;
		pr.orderReason = "Unsafe";
		rec.projects.add(pr);

		Federation fed = new Federation(UUID.randomUUID(), "Union");
		fed.members.add(cityId);
		Federation.Proposal prop = new Federation.Proposal(UUID.randomUUID());
		prop.yes.add(UUID.randomUUID());
		fed.proposals.add(prop);
		data.federations().add(fed);

		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		RecordsData back = codec.parse(JsonOps.INSTANCE, json).getOrThrow();

		var rec2 = back.of(cityId);
		assertEquals(1, rec2.budgetLines.size());
		assertTrue(rec2.budgetLines.get(0).assemblyApproved);
		assertEquals(1, rec2.cases.size());
		assertEquals(CaseStatus.SUBMITTED, rec2.cases.get(0).status);
		assertEquals(4, rec2.cases.get(0).rating);
		assertEquals(1, rec2.cases.get(0).history.size());
		assertEquals(1, rec2.projects.size());
		ProjectProposal pr2 = rec2.projects.get(0);
		assertEquals(rec2.budgetLines.get(0), pr2.budgetLine, "project is re-linked to its budget line");
		assertTrue(pr2.stopped);
		assertEquals("Everest", pr2.contractor);
		assertEquals(CaseStatus.IN_PROGRESS, pr2.status);
		assertEquals(rec.auditLog.size(), rec2.auditLog.size());
		assertEquals(1, back.federations().size());
		assertEquals(1, back.federations().get(0).proposals.size());
		assertEquals(2, rec2.nextCaseNo);
	}
}

package com.cydev.mod.expansion.federation;

import com.cydev.mod.sim.city.City;
import com.cydev.mod.sim.city.CityRegistry;
import java.util.List;
import java.util.UUID;

/** Rules for founding a federation and admitting cities by majority of member mayors. */
public final class FederationService {
	private FederationService() {
	}

	public static Federation federationOf(List<Federation> all, UUID cityId) {
		for (Federation f : all) {
			if (f.members.contains(cityId)) {
				return f;
			}
		}
		return null;
	}

	public static String create(List<Federation> all, City founder, UUID actor, String name, Federation[] out) {
		if (!founder.isMayor(actor)) {
			return "Only the Mayor of " + founder.name + " can found a federation.";
		}
		if (federationOf(all, founder.id) != null) {
			return founder.name + " is already in a federation.";
		}
		for (Federation f : all) {
			if (f.name.equalsIgnoreCase(name)) {
				return "A federation named " + name + " already exists.";
			}
		}
		Federation f = new Federation(UUID.randomUUID(), name);
		f.members.add(founder.id);
		all.add(f);
		if (out != null && out.length > 0) {
			out[0] = f;
		}
		return null;
	}

	/** A member city's mayor proposes adding another city. */
	public static String propose(List<Federation> all, Federation fed, City proposerCity, UUID actor, City target) {
		if (!fed.members.contains(proposerCity.id) || !proposerCity.isMayor(actor)) {
			return "Only a Mayor of a member city can propose new members.";
		}
		if (federationOf(all, target.id) != null) {
			return target.name + " is already in a federation.";
		}
		if (fed.proposal(target.id) != null) {
			return "A proposal for " + target.name + " is already open.";
		}
		fed.proposals.add(new Federation.Proposal(target.id));
		return vote(all, fed, proposerCity, actor, target.id, null);
	}

	/** A member mayor votes yes on a proposal. Admits the city once a majority voted and the city's mayor accepted. */
	public static String vote(List<Federation> all, Federation fed, City voterCity, UUID actor, UUID targetId, CityRegistry registry) {
		Federation.Proposal p = fed.proposal(targetId);
		if (p == null) {
			return "No such proposal.";
		}
		if (!fed.members.contains(voterCity.id) || !voterCity.isMayor(actor)) {
			return "Only a Mayor of a member city can vote.";
		}
		if (!p.yes.contains(actor)) {
			p.yes.add(actor);
		}
		return tryAdmit(fed, p);
	}

	/** The proposed city's own mayor accepts the invitation. */
	public static String accept(Federation fed, City target, UUID actor) {
		Federation.Proposal p = fed.proposal(target.id);
		if (p == null) {
			return "No open proposal for " + target.name + ".";
		}
		if (!target.isMayor(actor)) {
			return "Only the Mayor of " + target.name + " can accept.";
		}
		p.accepted = true;
		return tryAdmit(fed, p);
	}

	private static String tryAdmit(Federation fed, Federation.Proposal p) {
		if (p.accepted && p.yes.size() * 2 > fed.members.size()) {
			fed.members.add(p.cityId);
			fed.proposals.remove(p);
		}
		return null;
	}
}

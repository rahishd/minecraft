package com.cydev.mod.government.workflow;

import com.cydev.mod.government.council.Position;
import java.util.EnumMap;
import java.util.Map;

/**
 * Who may complete which service. Configurable because real authority depends
 * on law, local rules and delegated powers.
 */
public class AuthorityRules {
	public record Rule(boolean wardCanIssue, Position department) {
	}

	private final Map<ServiceType, Rule> rules = new EnumMap<>(ServiceType.class);

	public AuthorityRules() {
		for (ServiceType t : ServiceType.values()) {
			rules.put(t, new Rule(t.defaultWardCanIssue, t.defaultDepartment));
		}
	}

	public Rule get(ServiceType type) {
		return rules.get(type);
	}

	public void set(ServiceType type, boolean wardCanIssue, Position department) {
		rules.put(type, new Rule(wardCanIssue, department));
	}
}

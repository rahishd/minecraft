package com.cydev.mod.sim.zone;

/** What a plot of city land may be used for. Demand and growth will key off this later. */
public enum ZoneType {
	RESIDENTIAL("Residential"),
	COMMERCIAL("Commercial"),
	INDUSTRIAL("Industrial"),
	OFFICE("Office");

	private final String label;

	ZoneType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/** The next type in wand-cycling order (wraps around). */
	public ZoneType next() {
		ZoneType[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	/** Case-insensitive lookup; null when the name is unknown. */
	public static ZoneType parse(String name) {
		for (ZoneType t : values()) {
			if (t.name().equalsIgnoreCase(name)) {
				return t;
			}
		}
		return null;
	}
}

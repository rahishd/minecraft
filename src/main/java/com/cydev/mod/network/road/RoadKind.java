package com.cydev.mod.network.road;

/** The road block family. Kerbs are part of the family but vehicles cannot drive on them. */
public enum RoadKind {
	ASPHALT("asphalt", true),
	CENTER_LINE("asphalt_center_line", true),
	LANE_DASHED("asphalt_lane_dashed", true),
	EDGE_LINE("asphalt_edge_line", true),
	CROSSWALK("asphalt_crosswalk", true),
	KERB("asphalt_kerb", false);

	private final String blockName;
	private final boolean drivable;

	RoadKind(String blockName, boolean drivable) {
		this.blockName = blockName;
		this.drivable = drivable;
	}

	/** Registry path of the block, under the cydev namespace. */
	public String blockName() {
		return blockName;
	}

	/** Drivable kinds form the road network; the others only decorate it. */
	public boolean drivable() {
		return drivable;
	}

	public static RoadKind parse(String name) {
		for (RoadKind k : values()) {
			if (k.name().equalsIgnoreCase(name)) {
				return k;
			}
		}
		return null;
	}
}

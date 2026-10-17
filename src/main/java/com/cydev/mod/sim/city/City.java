package com.cydev.mod.sim.city;

import java.util.UUID;

/** Pure-Java city record. No Minecraft types so it stays testable. */
public class City {
	public final UUID id;
	public String name;
	public UUID mayorId;
	public long treasury;

	public City(UUID id, String name) {
		this.id = id;
		this.name = name;
	}
}

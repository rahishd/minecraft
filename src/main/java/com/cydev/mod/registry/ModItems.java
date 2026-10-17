package com.cydev.mod.registry;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.world.item.ZoningWandItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {
	public static final Item ZONING_WAND = register("zoning_wand",
		settings -> new ZoningWandItem(settings.maxCount(1)));

	private ModItems() {
	}

	/** Loads the class, which registers every item. Call once from the mod initializer. */
	public static void register() {
	}

	private static Item register(String name, java.util.function.Function<Item.Settings, Item> factory) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(CydevMod.MOD_ID, name));
		return Registry.register(Registries.ITEM, key, factory.apply(new Item.Settings().registryKey(key)));
	}
}

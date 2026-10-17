package com.cydev.mod.registry;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.world.block.RoadBlock;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The "Cydev" creative tab. */
public final class ModItemGroups {
	public static final RegistryKey<ItemGroup> MAIN =
		RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(CydevMod.MOD_ID, "main"));

	private ModItemGroups() {
	}

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, MAIN, FabricItemGroup.builder()
			.displayName(Text.translatable("itemGroup." + CydevMod.MOD_ID + ".main"))
			.icon(() -> new ItemStack(ModBlocks.ASPHALT_CENTER_LINE))
			.entries((context, entries) -> {
				entries.add(ModItems.ZONING_WAND);
				for (RoadBlock block : ModBlocks.roads()) {
					entries.add(block);
				}
			})
			.build());
	}
}

package com.cydev.mod.registry;

import com.cydev.mod.core.CydevMod;
import com.cydev.mod.network.road.RoadKind;
import com.cydev.mod.world.block.AxisRoadBlock;
import com.cydev.mod.world.block.FacingRoadBlock;
import com.cydev.mod.world.block.RoadBlock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** The road block family. Models, textures and blockstates live under assets/cydev. */
public final class ModBlocks {
	private static final Map<RoadKind, RoadBlock> ROADS = new EnumMap<>(RoadKind.class);

	public static final RoadBlock ASPHALT = road(RoadKind.ASPHALT, RoadBlock::new);
	public static final RoadBlock ASPHALT_CENTER_LINE = road(RoadKind.CENTER_LINE, AxisRoadBlock::new);
	public static final RoadBlock ASPHALT_LANE_DASHED = road(RoadKind.LANE_DASHED, AxisRoadBlock::new);
	public static final RoadBlock ASPHALT_EDGE_LINE = road(RoadKind.EDGE_LINE, FacingRoadBlock::new);
	public static final RoadBlock ASPHALT_CROSSWALK = road(RoadKind.CROSSWALK, AxisRoadBlock::new);
	public static final RoadBlock ASPHALT_KERB = road(RoadKind.KERB, FacingRoadBlock::new);

	private ModBlocks() {
	}

	/** Loads the class, which registers every block. Call once from the mod initializer. */
	public static void register() {
	}

	public static RoadBlock of(RoadKind kind) {
		return ROADS.get(kind);
	}

	public static Iterable<RoadBlock> roads() {
		return ROADS.values();
	}

	private static RoadBlock road(RoadKind kind, BiFunction<RoadKind, AbstractBlock.Settings, RoadBlock> factory) {
		Identifier id = Identifier.of(CydevMod.MOD_ID, kind.blockName());
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
		AbstractBlock.Settings settings = AbstractBlock.Settings.create()
			.registryKey(blockKey)
			.mapColor(kind == RoadKind.KERB ? MapColor.STONE_GRAY : MapColor.DEEPSLATE_GRAY)
			.strength(1.5f, 6.0f)
			.sounds(BlockSoundGroup.STONE)
			.nonOpaque()
			.pistonBehavior(PistonBehavior.DESTROY);
		RoadBlock block = Registry.register(Registries.BLOCK, blockKey, factory.apply(kind, settings));

		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
		Registry.register(Registries.ITEM, itemKey,
			new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
		ROADS.put(kind, block);
		return block;
	}
}

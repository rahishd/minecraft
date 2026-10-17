package com.cydev.mod.world.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Surveyor's wand for zoning city land.
 * <ul>
 * <li>Left-click a block: first corner.</li>
 * <li>Right-click a block: second corner.</li>
 * <li>Right-click the air: zone the selection as the current type.</li>
 * <li>Sneak + right-click: switch zone type.</li>
 * </ul>
 * Only the Mayor or a Ward Chairperson can actually zone, and only on their city's land.
 */
public class ZoningWandItem extends Item {
	public ZoningWandItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getPlayer() instanceof ServerPlayerEntity player) {
			if (player.isSneaking()) {
				WandSelections.cycleType(player);
			} else {
				WandSelections.setCorner(player, context.getBlockPos(), false);
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (user instanceof ServerPlayerEntity player) {
			if (player.isSneaking()) {
				WandSelections.cycleType(player);
			} else {
				WandSelections.apply(player);
			}
		}
		return ActionResult.SUCCESS;
	}
}

package com.cydev.mod.world.block;

import com.cydev.mod.network.road.RoadKind;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

/** A road block with a marking that runs along one axis (centre line, lane dashes, crosswalk). */
public class AxisRoadBlock extends RoadBlock {
	public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

	public AxisRoadBlock(RoadKind kind, Settings settings) {
		super(kind, settings);
		setDefaultState(getDefaultState().with(AXIS, Direction.Axis.Z));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	/** The marking runs the way the player is facing. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(AXIS, ctx.getHorizontalPlayerFacing().getAxis());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
			return state.with(AXIS, state.get(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}

package com.cydev.mod.world.block;

import com.cydev.mod.network.road.RoadKind;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * A road block with something along one edge: a painted edge line, or a kerb
 * (a raised stone strip). {@code facing} is the edge the line or kerb is on.
 */
public class FacingRoadBlock extends RoadBlock {
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

	/** Kerb: 4 pixels wide and rising to 5 pixels, on top of the 2 pixel surface. */
	private static final double KERB_TOP = 5 / 16.0;
	private static final double KERB_WIDTH = 4 / 16.0;
	private static final VoxelShape KERB_NORTH = VoxelShapes.union(SURFACE, VoxelShapes.cuboid(0, 0, 0, 1, KERB_TOP, KERB_WIDTH));
	private static final VoxelShape KERB_SOUTH = VoxelShapes.union(SURFACE, VoxelShapes.cuboid(0, 0, 1 - KERB_WIDTH, 1, KERB_TOP, 1));
	private static final VoxelShape KERB_WEST = VoxelShapes.union(SURFACE, VoxelShapes.cuboid(0, 0, 0, KERB_WIDTH, KERB_TOP, 1));
	private static final VoxelShape KERB_EAST = VoxelShapes.union(SURFACE, VoxelShapes.cuboid(1 - KERB_WIDTH, 0, 0, 1, KERB_TOP, 1));

	public FacingRoadBlock(RoadKind kind, Settings settings) {
		super(kind, settings);
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape shapeOf(BlockState state) {
		if (kind() != RoadKind.KERB) {
			return SURFACE;
		}
		return switch (state.get(FACING)) {
			case SOUTH -> KERB_SOUTH;
			case WEST -> KERB_WEST;
			case EAST -> KERB_EAST;
			default -> KERB_NORTH;
		};
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** The edge feature is on the side the player is facing. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}

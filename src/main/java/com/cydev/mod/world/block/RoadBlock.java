package com.cydev.mod.world.block;

import com.cydev.mod.network.road.RoadKind;
import com.cydev.mod.network.road.RoadPos;
import com.cydev.mod.world.persistence.RoadData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A thin asphalt slab (2/16 of a block). Placing or breaking one keeps the
 * world's {@link com.cydev.mod.network.road.RoadNetwork} in step.
 */
public class RoadBlock extends Block {
	/** The road surface: 2 pixels high, like a layer of asphalt on the ground. */
	protected static final VoxelShape SURFACE = VoxelShapes.cuboid(0, 0, 0, 1, 2 / 16.0, 1);

	private final RoadKind kind;

	public RoadBlock(RoadKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public RoadKind kind() {
		return kind;
	}

	protected VoxelShape shapeOf(BlockState state) {
		return SURFACE;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapeOf(state);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapeOf(state);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		sync(world, pos);
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		super.onStateReplaced(state, world, pos, moved);
		sync(world, pos);
	}

	/**
	 * Makes the network match whatever block is at the position now. Reading
	 * the world instead of trusting the event means the hooks are safe to run
	 * in any order (a road block replaced by another one, a rotation, a break).
	 * Like cities, roads live in the overworld only.
	 */
	private static void sync(World world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		RoadData data = RoadData.get(serverWorld.getServer());
		RoadPos at = new RoadPos(pos.getX(), pos.getY(), pos.getZ());
		boolean changed = serverWorld.getBlockState(pos).getBlock() instanceof RoadBlock road
			? data.network().add(at, road.kind())
			: data.network().remove(at);
		if (changed) {
			data.markDirty();
		}
	}
}

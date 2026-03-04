package videogoose.resourcesrefueled.systems;

import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.module.util.SystemModule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;

import java.io.IOException;
import java.util.HashMap;

/**
 * System module that manages a fluid pipe network on a single entity.
 * <p>
 * <b>Network topology</b> is tracked in two explicit maps rather than the inherited
 * {@code blocks} array:
 * <ul>
 *   <li>{@link #tankSegments} — one entry per placed {@code FLUID_TANK} block.
 *       Drives structural capacity: {@code tankCapacity = tankSegments.size() × capacityPerBlock}.</li>
 *   <li>{@link #pipeSegments} — one entry per placed pipe-network block
 *       ({@code FLUID_PIPE}, {@code FLUID_PUMP}, {@code FLUID_VALVE}, {@code FLUID_FILTER}).
 *       Used for future routing / connectivity logic.</li>
 * </ul>
 * The inherited {@code blocks} map is deliberately not populated for fluid-network blocks.
 * <p>
 * <b>Placement / removal</b> is handled by {@link #handlePlace} and {@link #handleRemove},
 * which update the appropriate map and trigger {@link #recalculateCapacity()} when tank
 * blocks change.
 * <p>
 * <b>Serialisation</b> writes both maps to the packet buffer so the network is fully
 * restored on the client and on entity reload.
 */
public class FluidTankSystemModule extends SystemModule {

	// -------------------------------------------------------------------------
	// Value types
	// -------------------------------------------------------------------------

	/** All tank blocks on this entity, keyed by block index. Drives capacity. */
	private final HashMap<Long, FluidTankSegment> tankSegments = new HashMap<>();
	/** All pipe-network blocks on this entity, keyed by block index. */
	private final HashMap<Long, FluidPipeSegment> pipeSegments = new HashMap<>();

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------
	/** Element ID of the fluid currently held in this network (0 = empty / unset). */
	private short fluidId;
	/**
	 * Fluid units currently stored across the whole network.
	 * Always clamped to {@link #tankCapacity} by {@link #recalculateCapacity()}.
	 */
	private double currentFluidLevel;
	/**
	 * Total capacity in fluid units — derived structurally from
	 * {@code tankSegments.size() × capacityPerBlock}. Never set directly from outside.
	 */
	private double tankCapacity;

	public FluidTankSystemModule(SegmentController entity, ManagerContainer<?> managerContainer) {
		super(entity, managerContainer, ResourcesRefueled.getInstance(), ElementRegistry.FLUID_TANK.getId());
		recalculateCapacity();
	}

	@Override
	public void handle(Timer timer) {
		// Future: per-tick pump-driven fluid flow between sub-networks.
	}

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	/**
	 * Called by the engine when a block belonging to this module's registered ID set
	 * is placed on the entity.
	 * <p>
	 * Routes the block into {@link #tankSegments} or {@link #pipeSegments} depending on
	 * type. Does <em>not</em> call {@code super.handlePlace()} so the inherited
	 * {@code blocks} map is never populated for fluid-network blocks.
	 *
	 * @param abs         Absolute packed block index (includes orientation bits).
	 * @param orientation Block orientation byte.
	 */
	@Override
	public void handlePlace(long abs, byte orientation) {
		long posIndex = ElementCollection.getPosIndexFrom4(abs);
		short type = getBlockTypeAt(posIndex);
		if(type == ElementRegistry.FLUID_TANK.getId()) {
			tankSegments.put(posIndex, new FluidTankSegment(posIndex, type));
			recalculateCapacity();
		} else if(ElementRegistry.isPipe(type)) {
			pipeSegments.put(posIndex, new FluidPipeSegment(posIndex, type));
			flagUpdatedData();
		}
	}

	// -------------------------------------------------------------------------
	// SystemModule lifecycle
	// -------------------------------------------------------------------------

	/**
	 * Called by the engine when a block belonging to this module's registered ID set
	 * is removed from the entity.
	 * <p>
	 * Removes the block from the appropriate map. Does <em>not</em> call
	 * {@code super.handleRemove()} so the inherited {@code blocks} map is unaffected.
	 *
	 * @param abs Absolute packed block index (includes orientation bits).
	 */
	@Override
	public void handleRemove(long abs) {
		long posIndex = ElementCollection.getPosIndexFrom4(abs);
		if(tankSegments.remove(posIndex) != null) {
			recalculateCapacity();
		} else if(pipeSegments.remove(posIndex) != null) {
			flagUpdatedData();
		}
	}

	// -------------------------------------------------------------------------
	// Block placement / removal
	// -------------------------------------------------------------------------

	/**
	 * Recomputes {@link #tankCapacity} from the current number of tank segments and
	 * the configured per-block capacity. Clamps {@link #currentFluidLevel} so it
	 * never exceeds the new capacity. Calls {@link #flagUpdatedData()} to mark the
	 * module dirty for network sync.
	 */
	private void recalculateCapacity() {
		tankCapacity = tankSegments.size() * ConfigManager.getFluidTankCapacityPerBlock();
		if(currentFluidLevel > tankCapacity) {
			currentFluidLevel = tankCapacity;
		}
		flagUpdatedData();
	}

	@Override
	public void onTagSerialize(PacketWriteBuffer buffer) throws IOException {
		buffer.writeShort(fluidId);
		buffer.writeDouble(currentFluidLevel);

		// Tank segments
		buffer.writeInt(tankSegments.size());
		for(FluidTankSegment seg : tankSegments.values()) {
			buffer.writeLong(seg.blockIndex);
			buffer.writeShort(seg.blockType);
		}

		// Pipe segments
		buffer.writeInt(pipeSegments.size());
		for(FluidPipeSegment seg : pipeSegments.values()) {
			buffer.writeLong(seg.blockIndex);
			buffer.writeShort(seg.blockType);
		}
	}

	// -------------------------------------------------------------------------
	// Capacity
	// -------------------------------------------------------------------------

	@Override
	public void onTagDeserialize(PacketReadBuffer buffer) throws IOException {
		fluidId = buffer.readShort();
		currentFluidLevel = buffer.readDouble();

		// Tank segments
		tankSegments.clear();
		int tankCount = buffer.readInt();
		for(int i = 0; i < tankCount; i++) {
			long index = buffer.readLong();
			short type = buffer.readShort();
			tankSegments.put(index, new FluidTankSegment(index, type));
		}

		// Pipe segments
		pipeSegments.clear();
		int pipeCount = buffer.readInt();
		for(int i = 0; i < pipeCount; i++) {
			long index = buffer.readLong();
			short type = buffer.readShort();
			pipeSegments.put(index, new FluidPipeSegment(index, type));
		}

		// Capacity is always structural — derive it from the restored map.
		recalculateCapacity();
	}

	// -------------------------------------------------------------------------
	// Serialisation
	// -------------------------------------------------------------------------

	@Override
	public double getPowerConsumedPerSecondResting() {
		return 0;
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
		return 0;
	}

	// -------------------------------------------------------------------------
	// SystemModule power stubs
	// -------------------------------------------------------------------------

	@Override
	public String getName() {
		return "Fluid Tank System Module - " + ElementKeyMap.getInfo(fluidId).getName();
	}

	public short getFluidId() {
		return fluidId;
	}

	public void setFluidId(short fluidId) {
		this.fluidId = fluidId;
		flagUpdatedData();
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	/** Structural capacity — always derived from {@code tankSegments.size() × capacityPerBlock}. */
	public double getTankCapacity() {
		return tankCapacity;
	}

	public double getCurrentFluidLevel() {
		return currentFluidLevel;
	}

	public void setCurrentFluidLevel(double currentFluidLevel) {
		this.currentFluidLevel = Math.max(0, Math.min(currentFluidLevel, tankCapacity));
		flagUpdatedData();
	}

	/** Read-only view of the tank-segment map (for testing / debugging). */
	public HashMap<Long, FluidTankSegment> getTankSegments() {
		return tankSegments;
	}

	/** Read-only view of the pipe-segment map (for testing / debugging). */
	public HashMap<Long, FluidPipeSegment> getPipeSegments() {
		return pipeSegments;
	}

	/**
	 * Returns the block indices of all tank segments for explosion origin scatter.
	 */
	public LongArrayList getBlockIndices() {
		LongArrayList indices = new LongArrayList(tankSegments.size());
		for(long index : tankSegments.keySet()) {
			indices.add(index);
		}
		return indices;
	}

	/**
	 * Computes the axis-aligned bounding box that encloses all tank segments.
	 * Used to determine explosion radius and origin during tank destruction.
	 */
	public BoundingBox getBoundingBox() {
		BoundingBox boundingBox = new BoundingBox();
		Vector3i min = new Vector3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		Vector3i max = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
		Vector3i pos = new Vector3i();
		for(long index : tankSegments.keySet()) {
			ElementCollection.getPosFromIndex(index, pos);
			min.set(Math.min(min.x, pos.x), Math.min(min.y, pos.y), Math.min(min.z, pos.z));
			max.set(Math.max(max.x, pos.x), Math.max(max.y, pos.y), Math.max(max.z, pos.z));
		}
		if(!tankSegments.isEmpty()) {
			boundingBox.set(min.toVector3f(), max.toVector3f());
		}
		return boundingBox;
	}

	// -------------------------------------------------------------------------
	// Explosion helpers (read from tankSegments, not the inherited blocks map)
	// -------------------------------------------------------------------------

	/**
	 * Resolves the block type at the given position index from the entity's segment data.
	 * Returns 0 if the type cannot be determined (safe fallback — the block simply won't
	 * match any segment category).
	 */
	private short getBlockTypeAt(long posIndex) {
		try {
			return segmentController.getSegmentBuffer().getPointUnsave(posIndex).getType();
		} catch(Exception e) {
			return 0;
		}
	}

	/** One placed FLUID_TANK block that contributes capacity to the network. */
	public static final class FluidTankSegment {
		public final long blockIndex;
		public final short blockType;

		public FluidTankSegment(long blockIndex, short blockType) {
			this.blockIndex = blockIndex;
			this.blockType = blockType;
		}
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/** One placed pipe-network block (pipe, pump, valve, filter). */
	public static final class FluidPipeSegment {
		public final long blockIndex;
		public final short blockType;

		public FluidPipeSegment(long blockIndex, short blockType) {
			this.blockIndex = blockIndex;
			this.blockType = blockType;
		}
	}
}

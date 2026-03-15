package videogoose.resourcesreorganized.systems;

import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.module.util.SystemModule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.data.FluidMeta;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.logistics.fluid.*;
import videogoose.resourcesreorganized.manager.ConfigManager;

import java.io.IOException;
import java.util.*;

/**
 * System module that manages one or more fluid networks on a single entity.
 * <p>
 * <b>Topology model:</b> the entity's fluid blocks (tank blocks and pipe blocks) are
 * partitioned into connected components by face-adjacency. Each component is a
 * {@link FluidNetwork} with its own fluid level and structural capacity.
 * Tank blocks contribute capacity; pipe blocks carry fluid but contribute no storage.
 * <p>
 * <b>Placement</b> ({@link #onPlace}): the new block is added to the master maps,
 * all face-adjacent existing networks are merged into a single network containing the
 * new block, and capacity is recalculated.
 * <p>
 * <b>Removal</b> ({@link #onRemove}): the block is removed from its network and
 * the master maps. The remaining members of that network are re-flooded to form zero
 * or more new networks; fluid is distributed proportionally by new capacity.
 * <p>
 * <b>Serialisation</b>: each {@link FluidNetwork} writes its member indices, block
 * types, and fluid level. On deserialisation the maps are rebuilt from the member
 * lists and capacity is recalculated structurally.
 */
public class FluidSystemModule extends SystemModule {

	public static final int INPUT_SLOT_INDEX = 0;
	public static final int OUTPUT_SLOT_INDEX = 1;

	// =========================================================================
	// Inner types
	// =========================================================================

	/** All tank blocks on this entity, keyed by block index. */
	private final HashMap<Long, FluidTankSegment> tankSegments = new HashMap<>();
	/** All pipe-network blocks on this entity, keyed by block index. */
	private final HashMap<Long, FluidPipeSegment> pipeSegments = new HashMap<>();
	/** All fluid port blocks on this entity, keyed by block index. */
	private final HashMap<Long, FluidPortSegment> portSegments = new HashMap<>();
	/**
	 * The live set of connected fluid networks on this entity.
	 * Each network is an independent fluid container.
	 */
	private final List<FluidNetwork> networks = new ArrayList<>();

	// =========================================================================
	// Fields
	// =========================================================================
	private final SegmentController segmentController;

	public FluidSystemModule(ManagerContainer<?> managerContainer) {
		super(managerContainer.getSegmentController(), managerContainer, ResourcesReorganized.getInstance(), ElementRegistry.FLUID_TANK.getId());
		segmentController = managerContainer.getSegmentController();
	}

	/**
	 * Returns the six face-adjacent block indices for the given index.
	 * Uses {@link ElementCollection#getPosFromIndex} to decode and re-encode neighbours.
	 */
	private static Set<Long> faceAdjacentIndices(long index) {
		return FluidTopologyUtils.faceAdjacentIndices(index);
	}

	private static BoundingBox buildBoundingBox(LongArrayList tankIndices) {
		return FluidTopologyUtils.buildBoundingBox(tankIndices);
	}

	// =========================================================================
	// Constructor
	// =========================================================================

	@Override
	public void handle(Timer timer) {
		boolean debug = ConfigManager.isDebugMode();
		boolean changedByPumps = FluidPumpTickProcessor.process(pipeSegments, portSegments, networks, ConfigManager.getPumpTransferPerTick(), ConfigManager.getCapacityPerPort(), debug ? message -> ResourcesReorganized.getInstance().logInfo(message) : null);

		boolean changedByPorts = FluidPortTickProcessor.process(segmentController, portSegments, networks, ConfigManager.getCapacityPerCanister(), ConfigManager.getCapacityPerPort(), ElementRegistry.FLUID_CANISTER.getId(), debug ? message -> ResourcesReorganized.getInstance().logInfo(message) : null);

		if(changedByPumps || changedByPorts) {
			flagUpdatedData();
		}
	}

	// =========================================================================
	// SystemModule lifecycle
	// =========================================================================

	/**
	 * Registers a newly placed block and merges all adjacent networks into one.
	 * <p>
	 * Only tank and pipe blocks are handled; all other types are ignored.
	 *
	 * @param index       Block position index (from the event, no orientation bits).
	 * @param blockType   Element ID of the placed block.
	 */
	public void onPlace(long index, short blockType) {
		boolean changed = FluidTopologyMutationService.onPlace(index, blockType, segmentController, tankSegments, pipeSegments, portSegments, networks, ConfigManager.isDebugMode() ? message -> ResourcesReorganized.getInstance().logInfo(message) : null);
		if(changed) {
			flagUpdatedData();
		}
	}

	// Topology mutation internals moved to FluidTopologyMutationService.

	// =========================================================================
	// Placement / removal (called by SegmentPieceEventHandler)
	// =========================================================================

	/**
	 * Removes a block from its network and re-partitions the remaining members
	 * into new connected components, distributing fluid proportionally by capacity.
	 *
	 * @param index     Block position index (no orientation bits).
	 * @param blockType Element ID of the removed block.
	 */
	public void onRemove(long index, short blockType) {
		boolean changed = FluidTopologyMutationService.onRemove(index, blockType, tankSegments, pipeSegments, portSegments, networks, ConfigManager.isDebugMode() ? message -> ResourcesReorganized.getInstance().logInfo(message) : null);
		if(changed) {
			flagUpdatedData();
		}
	}

	@Override
	public void handlePlace(long index, byte blockType) {
		//Do nothing — we need the full short block type, so this is handled by onPlace() instead.
		//We just need to ensure that the parent method doesnt do anything, since the event handler will call onPlace() with the full block type.
	}

	@Override
	public void handleRemove(long index) {
		//Do nothing — we need the full short block type, so this is handled by onRemove() instead.
		//We just need to ensure that the parent method doesnt do anything, since the event handler will call onRemove() with the full block type.
	}

	// Topology partition/capacity helpers moved to FluidTopologyMutationService.

	@Override
	public void onTagSerialize(PacketWriteBuffer buffer) throws IOException {
		FluidNetworkCodec.serialize(buffer, networks, tankSegments, pipeSegments);
	}

	@Override
	public void onTagDeserialize(PacketReadBuffer buffer) throws IOException {
		FluidNetworkCodec.deserialize(buffer, networks, tankSegments, pipeSegments);
	}

	// =========================================================================
	// Serialisation
	// =========================================================================

	@Override
	public double getPowerConsumedPerSecondResting() {
		return 0;
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
		return 0;
	}

	// =========================================================================
	// SystemModule power stubs
	// =========================================================================

	@Override
	public String getName() {
		return "Fluid Tank System Module";
	}

	/**
	 * Returns the fluid ID of the first network with fluid, or 0 if all networks are empty.
	 * This is a legacy method for compatibility with older code that expects a single fluid type.
	 */
	public short getFluidId() {
		for(FluidNetwork net : networks) {
			if(net.fluidId != 0) return net.fluidId;
		}
		return 0;
	}

	/**
	 * Sets the fluid ID for all networks. This is a legacy method for compatibility.
	 * Generally, fluid IDs should be set per-network during fill operations.
	 */
	public void setFluidId(short fluidId) {
		for(FluidNetwork net : networks) {
			net.fluidId = fluidId;
		}
		flagUpdatedData();
	}

	// =========================================================================
	// Accessors — aggregate across all networks
	// =========================================================================

	/**
	 * Total fluid level summed across all networks.
	 * Used by {@link videogoose.resourcesreorganized.fuel.EntityFuelManager} and jump logic.
	 */
	public double getCurrentFluidLevel() {
		double total = 0;
		for(FluidNetwork net : networks) total += net.fluidLevel;
		return total;
	}

	/**
	 * Sets the total fluid level across all networks, distributing proportionally
	 * by each network's capacity. Used by write-back from {@link videogoose.resourcesreorganized.fuel.EntityFuelManager}.
	 */
	public void setCurrentFluidLevel(double totalLevel) {
		double totalCapacity = getTankCapacity();
		if(totalCapacity <= 0) return;
		double clamped = Math.min(totalLevel, totalCapacity);
		for(FluidNetwork net : networks) {
			double share = (net.tankCapacity / totalCapacity) * clamped;
			net.fluidLevel = Math.min(share, net.tankCapacity);
		}
		flagUpdatedData();
	}

	/**
	 * Total capacity summed across all networks.
	 */
	public double getTankCapacity() {
		double total = 0;
		for(FluidNetwork net : networks) total += net.tankCapacity;
		return total;
	}

	/**
	 * Drains {@code amount} fluid units from the networks, consuming from each
	 * proportionally to its current level. Returns the amount actually drained.
	 * <p>
	 * This is the primary method for all fuel-consumption code.
	 */
	public double drain(double amount) {
		double totalAvailable = getCurrentFluidLevel();
		if(totalAvailable <= 0 || amount <= 0) return 0;
		double toDrain = Math.min(amount, totalAvailable);
		double fraction = toDrain / totalAvailable;
		double drained = 0;
		for(FluidNetwork net : networks) {
			double take = net.fluidLevel * fraction;
			net.fluidLevel -= take;
			drained += take;

			// Clear fluid ID if network becomes empty
			if(net.fluidLevel <= 0) {
				net.fluidId = 0;
				net.fluidLevel = 0;
			}
		}
		flagUpdatedData();
		return drained;
	}

	/** Live network list — for direct inspection by consumers. */
	public List<FluidNetwork> getNetworks() {
		return networks;
	}

	/** Read-only view of all tank segments. */
	public HashMap<Long, FluidTankSegment> getTankSegments() {
		return tankSegments;
	}

	/** Read-only view of all pipe segments. */
	public HashMap<Long, FluidPipeSegment> getPipeSegments() {
		return pipeSegments;
	}

	/**
	 * Returns the tank block indices for the network that contains {@code blockIndex},
	 * or all tank indices if the block is not found in any network.
	 * Used to scatter explosion origins across the affected physical tank blocks.
	 */
	public LongArrayList getBlockIndicesForExplosion(long blockIndex) {
		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(blockIndex)) {
				LongArrayList indices = new LongArrayList();
				for(long idx : net.memberIndices) {
					if(tankSegments.containsKey(idx)) indices.add(idx);
				}
				return indices;
			}
		}
		// Fallback: all tank indices
		return getBlockIndices();
	}

	/** All tank block indices across all networks. */
	public LongArrayList getBlockIndices() {
		LongArrayList indices = new LongArrayList(tankSegments.size());
		for(long index : tankSegments.keySet()) indices.add(index);
		return indices;
	}

	// =========================================================================
	// Explosion helpers
	// =======================E==================================================

	/**
	 * Returns the fluid level of the network containing {@code blockIndex},
	 * or the total fluid level if not found. Used by explosion scaling.
	 */
	public double getFluidLevelForBlock(long blockIndex) {
		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(blockIndex)) return net.fluidLevel;
		}
		return getCurrentFluidLevel();
	}

	/**
	 * Returns the capacity of the network containing {@code blockIndex},
	 * or the total capacity if not found. Used by explosion radius scaling.
	 */
	public double getCapacityForBlock(long blockIndex) {
		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(blockIndex)) return net.tankCapacity;
		}
		return getTankCapacity();
	}

	/**
	 * Returns {@code true} if the network containing {@code blockIndex} holds a volatile
	 * fluid (one registered via {@link FluidMeta#registerVolatile}).
	 * Used by {@link videogoose.resourcesreorganized.listener.SegmentPieceEventHandler} to decide
	 * whether a destroyed tank block should trigger an explosion.
	 */
	public boolean isNetworkVolatile(long blockIndex) {
		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(blockIndex)) return net.isVolatile();
		}
		return false;
	}

	/**
	 * AABB of the tank blocks in the network that contains {@code blockIndex}.
	 * Falls back to all tank blocks if not found.
	 */
	public BoundingBox getBoundingBoxForBlock(long blockIndex) {
		LongArrayList tankIndices = getBlockIndicesForExplosion(blockIndex);
		return buildBoundingBox(tankIndices);
	}

	/** AABB enclosing all tank blocks across all networks. */
	public BoundingBox getBoundingBox() {
		return buildBoundingBox(getBlockIndices());
	}

	public String getFluidName() {
		// Collect unique fluid types
		Set<Short> fluidTypes = new HashSet<>();
		for(FluidNetwork net : networks) {
			if(net.fluidId != 0) {
				fluidTypes.add(net.fluidId);
			}
		}

		if(fluidTypes.isEmpty()) {
			return "Empty";
		} else if(fluidTypes.size() == 1) {
			short fluidId = fluidTypes.iterator().next();
			if(ElementKeyMap.isValidType(fluidId)) {
				return ElementKeyMap.getInfo(fluidId).getName();
			}
			return "Unknown";
		} else {
			// Multiple fluid types
			return "Mixed (" + fluidTypes.size() + " types)";
		}
	}

	/**
	 * Returns the flow rate in L/s for the pump at the given block index.
	 * Returns 0 if the block is not a pump or has no flow.
	 * Positive values indicate active pumping (outflow), negative would indicate reverse flow.
	 */
	public float getFlow(long blockIndex) {
		FluidPipeSegment seg = pipeSegments.get(blockIndex);
		if(seg == null || seg.blockType != ElementRegistry.PIPE_PUMP.getId()) {
			return 0;
		}
		return seg.flowRate;
	}

	/**
	 * Returns the flow rate for any block in a network. If the block is a pump, returns its flow.
	 * If the block is part of a network with pumps, returns the cumulative flow of all pumps in that network.
	 * Returns 0 if no flow.
	 */
	public float getFlowForNetwork(long blockIndex) {
		// First check if it's a pump itself
		FluidPipeSegment seg = pipeSegments.get(blockIndex);
		if(seg != null && seg.blockType == ElementRegistry.PIPE_PUMP.getId()) {
			return seg.flowRate;
		}

		// Find the network this block belongs to
		FluidNetwork network = null;
		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(blockIndex)) {
				network = net;
				break;
			}
		}

		if(network == null) return 0;

		// Calculate cumulative flow from all pumps in this network
		float totalFlow = 0;
		for(long memberIdx : network.memberIndices) {
			FluidPipeSegment pSeg = pipeSegments.get(memberIdx);
			if(pSeg != null && pSeg.blockType == ElementRegistry.PIPE_PUMP.getId()) {
				totalFlow += pSeg.flowRate;
			}
		}

		return totalFlow;
	}

	// Port tick logic moved to FluidPortTickProcessor.

	public Inventory getInventory(SegmentPiece segmentPiece) {
		if(segmentPiece.getSegmentController() instanceof ManagedUsableSegmentController<?>) {
			ManagedUsableSegmentController<?> controller = (ManagedUsableSegmentController<?>) segmentPiece.getSegmentController();
			return controller.getManagerContainer().getInventory(segmentPiece.getAbsoluteIndex());
		}
		return null;
	}

	/**
	 * Pulls up to {@code amount} fluid units from the first adjacent network of the pump
	 * at {@code pumpIndex} that has available fluid.
	 * <p>
	 * This is a low-level API used by pump machinery: callers should ensure the pump
	 * is active/powered and that {@code pumpIndex} refers to a pump block. Returns the
	 * amount actually extracted (may be less than requested if the network lacks fluid).
	 */
	public double pumpExtract(long pumpIndex, double amount) {
		if(amount <= 0) return 0;
		FluidPipeSegment seg = pipeSegments.get(pumpIndex);
		if(seg == null || seg.blockType != ElementRegistry.PIPE_PUMP.getId()) return 0; // not a pump we manage

		for(long nb : faceAdjacentIndices(pumpIndex)) {
			for(FluidNetwork net : networks) {
				if(net.memberIndices.contains(nb) && net.fluidLevel > 0) {
					double taken = Math.min(net.fluidLevel, amount);
					net.fluidLevel = Math.max(0.0, net.fluidLevel - taken);

					// Clear fluid ID if network becomes empty
					if(net.fluidLevel <= 0) {
						net.fluidId = 0;
						net.fluidLevel = 0;
					}

					flagUpdatedData();
					return taken;
				}
			}
		}
		return 0;
	}

	/**
	 * Inserts up to {@code amount} fluid units into the first adjacent network of the pump
	 * at {@code pumpIndex} that has free capacity.
	 * Returns the amount actually inserted (may be less than requested if the network is full).
	 */
	public double pumpInsert(long pumpIndex, double amount) {
		if(amount <= 0) return 0;
		FluidPipeSegment seg = pipeSegments.get(pumpIndex);
		if(seg == null || seg.blockType != ElementRegistry.PIPE_PUMP.getId()) return 0; // not a pump we manage

		double added = 0.0;
		for(long nb : faceAdjacentIndices(pumpIndex)) {
			for(FluidNetwork net : networks) {
				if(net.memberIndices.contains(nb)) {
					double free = Math.max(0.0, net.tankCapacity - net.fluidLevel);
					if(free <= 0) continue;
					added = Math.min(free, amount);
					net.fluidLevel += added;
				}
			}
		}
		if(added > 0) {
			flagUpdatedData();
		}
		return added;
	}

	/** One placed {@code FLUID_PORT} block — bridges inventory and the fluid network each tick. */
	public static final class FluidPortSegment extends videogoose.resourcesreorganized.logistics.fluid.model.FluidPortSegment {

		public FluidPortSegment(long blockIndex) {
			super(blockIndex);
		}
	}

	/** One placed {@code FLUID_TANK} block — contributes capacity to its network. */
	public static final class FluidTankSegment extends videogoose.resourcesreorganized.logistics.fluid.model.FluidTankSegment {

		public FluidTankSegment(long blockIndex, short blockType) {
			super(blockIndex, blockType);
		}
	}

	/** One placed pipe-network block (pipe, pump, valve, filter). */
	public static final class FluidPipeSegment extends videogoose.resourcesreorganized.logistics.fluid.model.FluidPipeSegment {

		public FluidPipeSegment(long blockIndex, short blockType) {
			super(blockIndex, blockType);
		}
	}

	/**
	 * A single connected component of the fluid network.
	 * <p>
	 * Membership ({@link #memberIndices}) covers both tank and pipe blocks.
	 * Capacity is derived structurally: {@code tankCount × capacityPerBlock}.
	 * Fluid level is never allowed to exceed capacity.
	 */
	public static final class FluidNetwork extends videogoose.resourcesreorganized.logistics.fluid.model.FluidNetwork {

		/**
		 * Returns {@code true} if the fluid currently stored in this network is volatile
		 * (i.e. will cause an explosion when the containing tank block is destroyed).
		 * Delegates to {@link FluidMeta#isVolatile}.
		 */
		public boolean isVolatile() {
			return FluidMeta.isVolatile(fluidId);
		}

		public String getFluidName() {
			if(fluidId == 0) {
				return "Empty";
			} else if(ElementKeyMap.isValidType(fluidId)) {
				return ElementKeyMap.getInfo(fluidId).getName();
			} else {
				return "Unknown";
			}
		}
	}
}

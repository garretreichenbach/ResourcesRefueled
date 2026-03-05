package videogoose.resourcesrefueled.systems;

import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.module.util.SystemModule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;

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
public class FluidTankSystemModule extends SystemModule {

	// =========================================================================
	// Inner types
	// =========================================================================

	/** All tank blocks on this entity, keyed by block index. */
	private final HashMap<Long, FluidTankSegment> tankSegments = new HashMap<>();
	/** All pipe-network blocks on this entity, keyed by block index. */
	private final HashMap<Long, FluidPipeSegment> pipeSegments = new HashMap<>();
	/**
	 * The live set of connected fluid networks on this entity.
	 * Each network is an independent fluid container.
	 */
	private final List<FluidNetwork> networks = new ArrayList<>();

	// =========================================================================
	// Fields
	// =========================================================================
	private final SegmentController segmentController;
	/** The fluid type this module accepts (e.g. HELIOGEN_CANISTER_FILLED). */
	private short fluidId;

	public FluidTankSystemModule(ManagerContainer<?> managerContainer) {
		super(managerContainer.getSegmentController(), managerContainer, ResourcesRefueled.getInstance(), ElementRegistry.FLUID_TANK.getId());
		segmentController = managerContainer.getSegmentController();
		fluidId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
	}

	/**
	 * Returns the six face-adjacent block indices for the given index.
	 * Uses {@link ElementCollection#getPosFromIndex} to decode and re-encode neighbours.
	 */
	private static Set<Long> faceAdjacentIndices(long index) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		Set<Long> result = new HashSet<>(6);
		result.add(ElementCollection.getIndex(pos.x + 1, pos.y, pos.z));
		result.add(ElementCollection.getIndex(pos.x - 1, pos.y, pos.z));
		result.add(ElementCollection.getIndex(pos.x, pos.y + 1, pos.z));
		result.add(ElementCollection.getIndex(pos.x, pos.y - 1, pos.z));
		result.add(ElementCollection.getIndex(pos.x, pos.y, pos.z + 1));
		result.add(ElementCollection.getIndex(pos.x, pos.y, pos.z - 1));
		return result;
	}

	private static BoundingBox buildBoundingBox(LongArrayList tankIndices) {
		BoundingBox bb = new BoundingBox();
		if(tankIndices.isEmpty()) return bb;
		Vector3i min = new Vector3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		Vector3i max = new Vector3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
		Vector3i pos = new Vector3i();
		for(int i = 0; i < tankIndices.size(); i++) {
			ElementCollection.getPosFromIndex(tankIndices.getLong(i), pos);
			min.set(Math.min(min.x, pos.x), Math.min(min.y, pos.y), Math.min(min.z, pos.z));
			max.set(Math.max(max.x, pos.x), Math.max(max.y, pos.y), Math.max(max.z, pos.z));
		}
		bb.set(min.toVector3f(), max.toVector3f());
		return bb;
	}

	// =========================================================================
	// Constructor
	// =========================================================================

	@Override
	public void handle(Timer timer) {
		// Simple pump transfer implementation: for each registered pump segment, try to move
		// up to configured amount from an adjacent network that has fluid to an adjacent
		// network that has free capacity. This is intentionally simple and will be expanded
		// later with orientation/valve/filter behaviour.
		double pumpRate = ConfigManager.getPumpTransferPerTick();
		boolean anyChange = false;

		for(FluidPipeSegment seg : pipeSegments.values()) {
			if(seg.blockType != ElementRegistry.PIPE_PUMP.getId()) continue;
			long pumpIndex = seg.blockIndex;

			// Find adjacent networks (may be 0, 1 or multiple). We'll collect distinct networks.
			Set<FluidNetwork> adjacentNets = new LinkedHashSet<>();
			for(long nb : faceAdjacentIndices(pumpIndex)) {
				for(FluidNetwork net : networks) {
					if(net.memberIndices.contains(nb)) {
						adjacentNets.add(net);
						break;
					}
				}
			}

			if(adjacentNets.size() < 2) continue; // nothing to pump between

			// Choose a source network (has fluid) and a target network (has capacity).
			FluidNetwork source = null;
			FluidNetwork target = null;
			for(FluidNetwork net : adjacentNets) {
				if(source == null && net.fluidLevel > 0) source = net;
				if(target == null && net.tankCapacity > net.fluidLevel) target = net;
				if(source != null && target != null && source != target) break;
			}
			if(source == null || target == null || source == target) continue;

			double transferable = Math.min(pumpRate, Math.min(source.fluidLevel, Math.max(0.0, target.tankCapacity - target.fluidLevel)));
			if(transferable <= 0) continue;

			source.fluidLevel = Math.max(0.0, source.fluidLevel - transferable);
			target.fluidLevel = Math.min(target.tankCapacity, target.fluidLevel + transferable);
			anyChange = true;
			if(ConfigManager.isDebugMode()) {
				ResourcesRefueled.getInstance().logInfo("[FluidNetwork] Pump @ " + pumpIndex + " moved " + transferable + " units from net{" + source.memberIndices.size() + "} to net{" + target.memberIndices.size() + "}");
			}
		}

		if(anyChange) flagUpdatedData();
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
		if(blockType == ElementRegistry.FLUID_TANK.getId()) {
			tankSegments.put(index, new FluidTankSegment(index, blockType));
		} else if(ElementRegistry.isPipe(blockType)) {
			// If this is a plain/vanilla pipe piece and it is not connected to any functional
			// component (tank, pump/valve/filter) or an existing network with capacity, treat
			// it as decorative and don't allocate memory for it.
			boolean isFunctional = shouldTreatAsFunctionalPipe(index, blockType);
			if(!isFunctional) {
				// Decorative pipe — ignore
				return;
			}
			pipeSegments.put(index, new FluidPipeSegment(index, blockType));
		} else {
			return; // not a fluid-network block
		}

		// Collect all networks that are adjacent to this new block.
		Set<Long> neighbours = faceAdjacentIndices(index);
		List<FluidNetwork> adjacentNetworks = new ArrayList<>();
		for(FluidNetwork net : networks) {
			for(long nb : neighbours) {
				if(net.memberIndices.contains(nb)) {
					adjacentNetworks.add(net);
					break;
				}
			}
		}

		// Merge all adjacent networks + the new block into a single network.
		FluidNetwork merged = new FluidNetwork();
		merged.memberIndices.add(index);
		for(FluidNetwork net : adjacentNetworks) {
			merged.memberIndices.addAll(net.memberIndices);
			merged.fluidLevel += net.fluidLevel;
			networks.remove(net);
		}
		recalculateNetworkCapacity(merged);
		// Clamp in case merged capacity is somehow less than combined fluid (shouldn't happen on add, but be safe).
		merged.fluidLevel = Math.min(merged.fluidLevel, merged.tankCapacity);
		networks.add(merged);

		flagUpdatedData();
		if(ConfigManager.isDebugMode()) {
			ResourcesRefueled.getInstance().logInfo("[FluidNetwork] Placed " + blockType + " @ " + index + " — networks: " + networks.size() + ", merged capacity: " + merged.tankCapacity);
		}
	}

	/**
	 * Heuristic that determines whether a placed pipe should be treated as part of
	 * the functional fluid network, or ignored as decorative.
	 * <p>
	 * Current rules (conservative):
	 * - Any mod pipe-type that has behaviour (pump/valve/filter) is always considered functional.
	 * - A pipe is functional if any face-adjacent neighbour is a tank block tracked by this module.
	 * - A pipe is functional if any face-adjacent neighbour is a tracked pipe block that itself
	 *   belongs to an existing network which has non-zero capacity (i.e. connected to tanks), or
	 *   if that neighbour is a mod functional pipe (pump/valve/filter).
	 * <p>
	 * This avoids creating in-memory networks for isolated vanilla pipe decorations while
	 * still picking up pipes that are connected to actual fluid storage or devices.
	 */
	private boolean shouldTreatAsFunctionalPipe(long index, short blockType) {
		// Always treat our own functional devices as real.
		short valveId = ElementRegistry.PIPE_VALVE.getId();
		short pumpId = ElementRegistry.PIPE_PUMP.getId();
		short filterId = ElementRegistry.PIPE_FILTER.getId();
		if(blockType == valveId || blockType == pumpId || blockType == filterId) {
			return true;
		}

		Set<Long> neighbours = faceAdjacentIndices(index);
		for(long nb : neighbours) {
			// Adjacent tank -> functional
			if(tankSegments.containsKey(nb)) {
				return true;
			}

			// Adjacent tracked pipe that is a functional device -> functional
			FluidPipeSegment seg = pipeSegments.get(nb);
			if(seg != null) {
				if(seg.blockType == valveId || seg.blockType == pumpId || seg.blockType == filterId) {
					return true;
				}
			}

			// If neighbour is part of an existing network that has capacity, treat as functional
			for(FluidNetwork net : networks) {
				if(net.memberIndices.contains(nb)) {
					if(net.tankCapacity > 0) {
						return true;
					}
					// Also treat as functional if the existing network contains any functional devices
					for(long member : net.memberIndices) {
						FluidPipeSegment ps = pipeSegments.get(member);
						if(ps != null && (ps.blockType == valveId || ps.blockType == pumpId || ps.blockType == filterId)) {
							return true;
						}
					}
				}
			}

			// New: check raw world block type for neighbours and treat as functional if
			// the neighbour can interact with fluids (condenser/refinery or our blocks).
			SegmentPiece piece = segmentController.getSegmentBuffer().getPointUnsave(nb);
			if(piece != null && piece.getType() != Element.TYPE_NONE && ElementRegistry.canInteractWithFluid(piece.getType())) {
				return true;
			}
		}
		return false;
	}

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
		boolean wasTank = tankSegments.remove(index) != null;
		boolean wasPipe = !wasTank && pipeSegments.remove(index) != null;
		if(!wasTank && !wasPipe) return;

		// Find the network this block belonged to.
		FluidNetwork ownerNetwork = null;
		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(index)) {
				ownerNetwork = net;
				break;
			}
		}
		if(ownerNetwork == null) {
			flagUpdatedData();
			return;
		}

		double savedFluid = ownerNetwork.fluidLevel;
		networks.remove(ownerNetwork);
		ownerNetwork.memberIndices.remove(index);

		if(ownerNetwork.memberIndices.isEmpty()) {
			// Nothing left — fluid is lost (pipe/tank was destroyed with no neighbours).
			flagUpdatedData();
			return;
		}

		// Re-flood the remaining members into one or more new networks.
		List<FluidNetwork> newNetworks = floodPartition(ownerNetwork.memberIndices);

		// Distribute fluid proportionally by new capacity so none is created or destroyed.
		double totalNewCapacity = 0;
		for(FluidNetwork net : newNetworks) {
			recalculateNetworkCapacity(net);
			totalNewCapacity += net.tankCapacity;
		}
		for(FluidNetwork net : newNetworks) {
			double fraction = (totalNewCapacity > 0) ? net.tankCapacity / totalNewCapacity : 1.0 / newNetworks.size();
			net.fluidLevel = Math.min(savedFluid * fraction, net.tankCapacity);
		}

		networks.addAll(newNetworks);
		flagUpdatedData();
		if(ConfigManager.isDebugMode()) {
			ResourcesRefueled.getInstance().logInfo("[FluidNetwork] Removed " + blockType + " @ " + index + " — split into " + newNetworks.size() + " network(s).");
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

	/**
	 * Partitions a set of block indices into connected components using BFS.
	 * A block is connected to its face-adjacent neighbours that are also in the set.
	 *
	 * @param members The full set of indices to partition (not modified).
	 * @return One {@link FluidNetwork} per connected component.
	 */
	private List<FluidNetwork> floodPartition(Set<Long> members) {
		Set<Long> unvisited = new HashSet<>(members);
		List<FluidNetwork> result = new ArrayList<>();

		while(!unvisited.isEmpty()) {
			FluidNetwork component = new FluidNetwork();
			Queue<Long> queue = new ArrayDeque<>();
			long seed = unvisited.iterator().next();
			queue.add(seed);
			unvisited.remove(seed);

			while(!queue.isEmpty()) {
				long current = queue.poll();
				component.memberIndices.add(current);
				for(long nb : faceAdjacentIndices(current)) {
					if(unvisited.contains(nb)) {
						unvisited.remove(nb);
						queue.add(nb);
					}
				}
			}
			result.add(component);
		}
		return result;
	}

	// =========================================================================
	// Connectivity helpers
	// =========================================================================

	/**
	 * Sets {@link FluidNetwork#tankCapacity} based on how many of the network's
	 * member indices are registered tank segments.
	 */
	private void recalculateNetworkCapacity(FluidNetwork net) {
		int tankCount = 0;
		for(long idx : net.memberIndices) {
			if(tankSegments.containsKey(idx)) tankCount++;
		}
		net.tankCapacity = tankCount * ConfigManager.getFluidTankCapacityPerBlock();
	}

	@Override
	public void onTagSerialize(PacketWriteBuffer buffer) throws IOException {
		buffer.writeShort(fluidId);
		buffer.writeInt(networks.size());
		for(FluidNetwork net : networks) {
			buffer.writeDouble(net.fluidLevel);
			buffer.writeInt(net.memberIndices.size());
			for(long idx : net.memberIndices) {
				buffer.writeLong(idx);
				// Write block type so we can rebuild tankSegments/pipeSegments on deserialise.
				short type;
				if(tankSegments.containsKey(idx)) {
					type = tankSegments.get(idx).blockType;
				} else if(pipeSegments.containsKey(idx)) {
					type = pipeSegments.get(idx).blockType;
				} else {
					type = 0;
				}
				buffer.writeShort(type);
			}
		}
	}

	@Override
	public void onTagDeserialize(PacketReadBuffer buffer) throws IOException {
		fluidId = buffer.readShort();
		tankSegments.clear();
		pipeSegments.clear();
		networks.clear();

		int networkCount = buffer.readInt();
		for(int n = 0; n < networkCount; n++) {
			FluidNetwork net = new FluidNetwork();
			net.fluidLevel = buffer.readDouble();
			int memberCount = buffer.readInt();
			for(int m = 0; m < memberCount; m++) {
				long idx = buffer.readLong();
				short type = buffer.readShort();
				net.memberIndices.add(idx);
				if(type == ElementRegistry.FLUID_TANK.getId()) {
					tankSegments.put(idx, new FluidTankSegment(idx, type));
				} else if(ElementRegistry.isPipe(type)) {
					pipeSegments.put(idx, new FluidPipeSegment(idx, type));
				}
			}
			recalculateNetworkCapacity(net);
			net.fluidLevel = Math.min(net.fluidLevel, net.tankCapacity);
			networks.add(net);
		}
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

	public short getFluidId() {
		return fluidId;
	}

	public void setFluidId(short fluidId) {
		this.fluidId = fluidId;
		flagUpdatedData();
	}

	// =========================================================================
	// Accessors — aggregate across all networks
	// =========================================================================

	/**
	 * Total fluid level summed across all networks.
	 * Used by {@link videogoose.resourcesrefueled.fuel.EntityFuelManager} and jump logic.
	 */
	public double getCurrentFluidLevel() {
		double total = 0;
		for(FluidNetwork net : networks) total += net.fluidLevel;
		return total;
	}

	/**
	 * Sets the total fluid level across all networks, distributing proportionally
	 * by each network's capacity. Used by write-back from {@link videogoose.resourcesrefueled.fuel.EntityFuelManager}.
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
	// =========================================================================

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

	/** One placed {@code FLUID_TANK} block — contributes capacity to its network. */
	public static final class FluidTankSegment {
		public final long blockIndex;
		public final short blockType;

		public FluidTankSegment(long blockIndex, short blockType) {
			this.blockIndex = blockIndex;
			this.blockType = blockType;
		}
	}

	/** One placed pipe-network block (pipe, pump, valve, filter). */
	public static final class FluidPipeSegment {
		public final long blockIndex;
		public final short blockType;

		public FluidPipeSegment(long blockIndex, short blockType) {
			this.blockIndex = blockIndex;
			this.blockType = blockType;
		}
	}

	/**
	 * A single connected component of the fluid network.
	 * <p>
	 * Membership ({@link #memberIndices}) covers both tank and pipe blocks.
	 * Capacity is derived structurally: {@code tankCount × capacityPerBlock}.
	 * Fluid level is never allowed to exceed capacity.
	 */
	public static final class FluidNetwork {
		/** All block indices (tanks + pipes) that belong to this network. */
		public final Set<Long> memberIndices = new HashSet<>();
		/** Current stored fluid units. Always ≤ {@link #tankCapacity}. */
		public double fluidLevel;
		/** Cached capacity — recalculated by {@link FluidTankSystemModule#recalculateNetworkCapacity}. */
		public double tankCapacity;

		/** True when this network has no blocks at all. */
		public boolean isEmpty() {
			return memberIndices.isEmpty();
		}
	}

	/**
	 * Pulls up to {@code amount} fluid units from the network that contains the pump at {@code pumpIndex}.
	 * <p>
	 * This is a low-level API used by pump machinery: callers should ensure the pump
	 * is active/powered and that {@code pumpIndex} refers to a pump block. Returns the
	 * amount actually extracted (may be less than requested if the network lacks fluid).
	 */
	public double pumpExtract(long pumpIndex, double amount) {
		if(amount <= 0) return 0;
		FluidPipeSegment seg = pipeSegments.get(pumpIndex);
		if(seg == null || seg.blockType != ElementRegistry.PIPE_PUMP.getId()) return 0; // not a pump we manage

		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(pumpIndex)) {
				double available = net.fluidLevel;
				double taken = Math.min(available, amount);
				net.fluidLevel = Math.max(0.0, net.fluidLevel - taken);
				flagUpdatedData();
				return taken;
			}
		}
		return 0;
	}

	/**
	 * Inserts up to {@code amount} fluid units into the network that contains the pump at {@code pumpIndex}.
	 * Returns the amount actually inserted (may be less than requested if the network is full).
	 */
	public double pumpInsert(long pumpIndex, double amount) {
		if(amount <= 0) return 0;
		FluidPipeSegment seg = pipeSegments.get(pumpIndex);
		if(seg == null || seg.blockType != ElementRegistry.PIPE_PUMP.getId()) return 0; // not a pump we manage

		for(FluidNetwork net : networks) {
			if(net.memberIndices.contains(pumpIndex)) {
				double free = Math.max(0.0, net.tankCapacity - net.fluidLevel);
				double added = Math.min(free, amount);
				net.fluidLevel += added;
				flagUpdatedData();
				return added;
			}
		}
		return 0;
	}
}

package videogoose.resourcesreorganized.systems;

import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.module.util.SystemModule;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.world.Sector;
import org.schema.game.server.data.GameServerState;
import org.schema.schine.graphicsengine.core.Timer;

import javax.vecmath.Vector3f;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.logistics.item.belt.BeltItem;
import videogoose.resourcesreorganized.logistics.item.belt.StallReason;
import videogoose.resourcesreorganized.logistics.item.belt.ConveyorBeltSimulator;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;
import videogoose.resourcesreorganized.logistics.item.runtime.InventoryReferenceRegistry;
import videogoose.resourcesreorganized.logistics.item.topology.ItemTopologyMutationService;
import videogoose.resourcesreorganized.logistics.item.topology.ItemTransportNetwork;
import videogoose.resourcesreorganized.logistics.item.topology.ItemTransportSegment;
import videogoose.resourcesreorganized.manager.ConfigManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-entity item transport system module.
 * <p>
 * Maintains the topology of conveyor networks on a single segment controller and
 * publishes their adjacent inventories to {@link InventoryReferenceRegistry} so the global
 * logistics runtime can reach them. Persistence is deferred to phase 4 — topology is rebuilt
 * from {@link #onPlace}/{@link #onRemove} events on this entity's lifetime.
 */
public class ItemTransportSystemModule extends SystemModule {

	private static final int TAG_VERSION = 3;

	private static final Set<ItemTransportSystemModule> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

	public static synchronized List<ItemTransportSystemModule> snapshotInstances() {
		return new ArrayList<>(INSTANCES);
	}

	private final SegmentController segmentController;
	private final ManagerContainer<?> managerContainer;

	private final Map<Long, ItemTransportSegment> conveyorSegments = new HashMap<>();
	private final List<ItemTransportNetwork> networks = new ArrayList<>();
	private final Set<String> registeredPortNodeIds = new HashSet<>();
	private final Map<Long, BeltItem> cellItems = new HashMap<>();
	private boolean pendingRebuild;
	private boolean syncDirty;
	private int syncCounter;

	public ItemTransportSystemModule(ManagerContainer<?> managerContainer) {
		super(managerContainer.getSegmentController(), managerContainer, ResourcesReorganized.getInstance(), ElementRegistry.CONVEYOR_BELT.getId());
		this.segmentController = managerContainer.getSegmentController();
		this.managerContainer = managerContainer;
		synchronized(ItemTransportSystemModule.class) {
			INSTANCES.add(this);
		}
	}

	public void onPlace(long index, short blockType, byte orientation) {
		boolean changed = ItemTopologyMutationService.onPlace(index, blockType, orientation, conveyorSegments, networks, managerContainer, debugLogger());
		if(changed) {
			syncRegistry();
			flagUpdatedData();
		}
	}

	public void onRemove(long index, short blockType) {
		// Any stack riding the removed belt cell spills into the sector as a free item, mirroring how
		// the vanilla game drops inventory contents when a storage block is destroyed.
		BeltItem dropped;
		synchronized(cellItems) {
			dropped = cellItems.remove(index);
		}
		if(dropped != null) {
			dropBeltItem(index, dropped);
			flagUpdatedData();
		}
		boolean changed = ItemTopologyMutationService.onRemove(index, blockType, conveyorSegments, networks, managerContainer, debugLogger());
		if(changed) {
			syncRegistry();
			flagUpdatedData();
		}
	}

	/**
	 * Spills a belt stack into the sector at the cell's world position as a floating {@link FreeItem},
	 * the same drop the engine uses for destroyed inventories. Server-only and best-effort: a failure to
	 * resolve the sector must never abort block removal.
	 */
	private void dropBeltItem(long index, BeltItem item) {
		if(item == null || item.count <= 0 || item.type <= 0 || !segmentController.isOnServer()) {
			return;
		}
		try {
			Vector3i pos = new Vector3i();
			ElementCollection.getPosFromIndex(index, pos);
			Vector3f world = segmentController.getAbsoluteElementWorldPositionShifted(pos, new Vector3f());
			Sector sector = ((GameServerState) segmentController.getState()).getUniverse().getSector(segmentController.getSectorId());
			if(sector != null && sector.getRemoteSector() != null) {
				sector.getRemoteSector().addItem(world, item.type, item.metaId, item.count);
			}
		} catch(Exception e) {
			ResourcesReorganized.getInstance().logException("Failed to drop conveyor belt item on removal", e);
		}
	}

	/**
	 * Recomputes ports for any network with a member face-adjacent to {@code index}.
	 * Call this when a non-transport block (e.g. a cargo container) is placed or removed
	 * so the affected network picks up or drops the inventory port.
	 */
	public void onNeighborChange(long index) {
		java.util.Set<Long> neighbours = videogoose.resourcesreorganized.logistics.item.topology.ItemTopologyUtils.faceAdjacentIndices(index);
		boolean changed = false;
		for(ItemTransportNetwork net : networks) {
			for(long nb : neighbours) {
				if(net.memberIndices.contains(nb)) {
					ItemTopologyMutationService.recomputePorts(net, conveyorSegments, managerContainer);
					changed = true;
					break;
				}
			}
		}
		if(changed) {
			syncRegistry();
			flagUpdatedData();
		}
	}

	@Override
	public void handle(Timer timer) {
		if(!segmentController.isOnServer()) {
			return;
		}
		if(pendingRebuild) {
			// Re-derive networks/ports from the segments restored on save load, now that the
			// entity's blocks and inventories are available.
			ItemTopologyMutationService.rebuildNetworks(conveyorSegments, networks, managerContainer);
			syncRegistry();
			pendingRebuild = false;
		}
		if(conveyorSegments.isEmpty() && cellItems.isEmpty()) {
			return;
		}
		// The simulator structurally mutates cellItems; lock so a concurrent serialize/render iteration
		// (which lock the same map) can't observe it mid-update.
		boolean changed;
		synchronized(cellItems) {
			changed = ConveyorBeltSimulator.tick(conveyorSegments, cellItems, managerContainer,
					ConfigManager.getConveyorBeltSpeed(), ConfigManager.getConveyorMaxPullPerCell(), debugLogger());
		}
		// Topology edits sync immediately (via onPlace/onRemove). Per-tick item movement is batched:
		// flush a keyframe at most every conveyor_sync_interval_ticks so we don't re-serialize the
		// whole module every tick. Clients interpolate item progress between keyframes.
		if(changed) {
			syncDirty = true;
		}
		if(++syncCounter >= ConfigManager.getConveyorSyncIntervalTicks()) {
			syncCounter = 0;
			if(syncDirty) {
				syncDirty = false;
				flagUpdatedData();
			}
		}
	}

	/** Live in-flight belt contents (cell index -&gt; item). Server simulates this; the client renders it. */
	public Map<Long, BeltItem> getCellItems() {
		return cellItems;
	}

	@Override
	public void handlePlace(long index, byte blockType) {
		// Routed via onPlace(long, short) from the event handler so the full block ID is known.
	}

	@Override
	public void handleRemove(long index) {
		// Routed via onRemove(long, short) from the event handler so the full block ID is known.
	}

	@Override
	public void onTagSerialize(PacketWriteBuffer buffer) throws IOException {
		buffer.writeInt(TAG_VERSION);
		writeSegments(buffer, conveyorSegments);
		// Second segment list: item tubes/pumps, removed once belts gained vertical turns. The slot is
		// still written (empty) so the tag layout is unchanged and saves stay readable both ways.
		writeSegments(buffer, Collections.emptyMap());
		// cellItems is read by the client render thread; serialization may run off the sim thread, so
		// hold its monitor while iterating (the renderer and the other mutators lock the same map).
		synchronized(cellItems) {
			writeCellItems(buffer, cellItems);
		}
	}

	@Override
	public void onTagDeserialize(PacketReadBuffer buffer) throws IOException {
		int version = buffer.readInt();
		// Only replace existing topology when the tag actually carries persisted data. A legacy stub
		// (version 0) must NOT wipe topology built from block-add events, or belts break on load.
		if(version >= 1) {
			conveyorSegments.clear();
			readSegments(buffer, conveyorSegments, TransportFamily.CONVEYOR);
			// Drain the old tube/pump segment list: saves written before those blocks were removed still
			// carry it, and the bytes have to be consumed for the rest of the tag to line up.
			readSegments(buffer, new HashMap<>(), TransportFamily.CONVEYOR);
			pendingRebuild = true;
		}
		if(version >= 2) {
			// Replace the whole map atomically w.r.t. the render thread iterating it. Version 3 appends a
			// stall-reason byte per item; older tags simply leave it at NONE.
			synchronized(cellItems) {
				cellItems.clear();
				readCellItems(buffer, cellItems, version >= 3);
			}
		}
	}

	private static void writeCellItems(PacketWriteBuffer buffer, Map<Long, BeltItem> cellItems) throws IOException {
		buffer.writeInt(cellItems.size());
		for(Map.Entry<Long, BeltItem> entry : cellItems.entrySet()) {
			BeltItem item = entry.getValue();
			buffer.writeLong(entry.getKey());
			buffer.writeShort(item.type);
			buffer.writeInt(item.metaId);
			buffer.writeInt(item.count);
			buffer.writeFloat(item.progress);
			buffer.writeByte(item.stall.code());
		}
	}

	private static void readCellItems(PacketReadBuffer buffer, Map<Long, BeltItem> target, boolean hasStall) throws IOException {
		int count = buffer.readInt();
		for(int i = 0; i < count; i++) {
			long cellIndex = buffer.readLong();
			short type = buffer.readShort();
			int metaId = buffer.readInt();
			int itemCount = buffer.readInt();
			float progress = buffer.readFloat();
			BeltItem item = new BeltItem(type, metaId, itemCount, progress);
			if(hasStall) {
				item.stall = StallReason.fromCode(buffer.readByte());
			}
			target.put(cellIndex, item);
		}
	}

	private static void writeSegments(PacketWriteBuffer buffer, Map<Long, ItemTransportSegment> segments) throws IOException {
		buffer.writeInt(segments.size());
		for(ItemTransportSegment segment : segments.values()) {
			buffer.writeLong(segment.blockIndex());
			buffer.writeShort(segment.blockType());
			buffer.writeByte(segment.orientation());
		}
	}

	private static void readSegments(PacketReadBuffer buffer, Map<Long, ItemTransportSegment> target, TransportFamily family) throws IOException {
		int count = buffer.readInt();
		for(int i = 0; i < count; i++) {
			long index = buffer.readLong();
			short type = buffer.readShort();
			byte orientation = buffer.readByte();
			target.put(index, new ItemTransportSegment(index, type, orientation, family));
		}
	}

	@Override
	public double getPowerConsumedPerSecondResting() {
		return 0;
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
		return 0;
	}

	@Override
	public String getName() {
		return "Item Transport System Module";
	}

	public List<ItemTransportNetwork> getNetworks() {
		return networks;
	}

	public Map<Long, ItemTransportSegment> getConveyorSegments() {
		return conveyorSegments;
	}

	public SegmentController getSegmentController() {
		return segmentController;
	}

	private void syncRegistry() {
		Set<String> newIds = new HashSet<>();
		for(ItemTransportNetwork net : networks) {
			LongIterator it = net.portIndices.iterator();
			while(it.hasNext()) {
				long portIndex = it.nextLong();
				Inventory inventory = managerContainer.getInventory(portIndex);
				if(inventory == null) {
					continue;
				}
				String nodeId = "inv:" + Integer.toHexString(System.identityHashCode(inventory));
				newIds.add(nodeId);
				InventoryReferenceRegistry.register(nodeId, inventory);
			}
		}
		for(String old : registeredPortNodeIds) {
			if(!newIds.contains(old)) {
				InventoryReferenceRegistry.remove(old);
			}
		}
		registeredPortNodeIds.clear();
		registeredPortNodeIds.addAll(newIds);
	}

	private static java.util.function.Consumer<String> debugLogger() {
		if(!ConfigManager.isDebugMode()) {
			return null;
		}
		return message -> ResourcesReorganized.getInstance().logInfo(message);
	}
}

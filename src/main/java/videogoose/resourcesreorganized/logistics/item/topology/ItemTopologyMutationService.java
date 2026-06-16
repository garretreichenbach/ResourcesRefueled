package videogoose.resourcesreorganized.logistics.item.topology;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Topology mutation logic for item transport networks.
 * <p>
 * Conveyor and tube blocks form separate connected components — the two families do not merge
 * even when adjacent. Pumps are part of the tube family.
 */
public final class ItemTopologyMutationService {

	private ItemTopologyMutationService() {
	}

	public static boolean onPlace(long index, short blockType, byte orientation,
								  Map<Long, ItemTransportSegment> conveyorSegments,
								  Map<Long, ItemTransportSegment> tubeSegments,
								  List<ItemTransportNetwork> networks,
								  ManagerContainer<?> managerContainer,
								  Consumer<String> debugLogger) {
		TransportFamily family = familyFor(blockType);
		if(family == null) {
			return false;
		}

		Map<Long, ItemTransportSegment> targetMap = (family == TransportFamily.CONVEYOR) ? conveyorSegments : tubeSegments;
		if(targetMap.containsKey(index)) {
			return false;
		}
		targetMap.put(index, new ItemTransportSegment(index, blockType, orientation, family));

		Set<Long> neighbours = ItemTopologyUtils.faceAdjacentIndices(index);
		List<ItemTransportNetwork> adjacent = new ArrayList<>();
		for(ItemTransportNetwork net : networks) {
			if(net.family != family) {
				continue;
			}
			for(long nb : neighbours) {
				if(net.memberIndices.contains(nb)) {
					adjacent.add(net);
					break;
				}
			}
		}

		ItemTransportNetwork merged = new ItemTransportNetwork(family);
		merged.memberIndices.add(index);
		for(ItemTransportNetwork net : adjacent) {
			merged.memberIndices.addAll(net.memberIndices);
			networks.remove(net);
		}
		networks.add(merged);
		recomputePorts(merged, conveyorSegments, tubeSegments, managerContainer);

		if(debugLogger != null) {
			debugLogger.accept("[ItemTransport] Placed " + blockType + " @ " + index + " family=" + family + " — networks: " + networks.size() + ", merged size: " + merged.memberIndices.size() + ", ports: " + merged.portIndices.size());
		}
		return true;
	}

	public static boolean onRemove(long index, short blockType,
								   Map<Long, ItemTransportSegment> conveyorSegments,
								   Map<Long, ItemTransportSegment> tubeSegments,
								   List<ItemTransportNetwork> networks,
								   ManagerContainer<?> managerContainer,
								   Consumer<String> debugLogger) {
		TransportFamily family = familyFor(blockType);
		if(family == null) {
			return false;
		}

		Map<Long, ItemTransportSegment> targetMap = (family == TransportFamily.CONVEYOR) ? conveyorSegments : tubeSegments;
		if(targetMap.remove(index) == null) {
			return false;
		}

		ItemTransportNetwork owner = null;
		for(ItemTransportNetwork net : networks) {
			if(net.memberIndices.contains(index)) {
				owner = net;
				break;
			}
		}
		if(owner == null) {
			return true;
		}

		networks.remove(owner);
		owner.memberIndices.remove(index);
		if(owner.memberIndices.isEmpty()) {
			if(debugLogger != null) {
				debugLogger.accept("[ItemTransport] Removed " + blockType + " @ " + index + " — network emptied.");
			}
			return true;
		}

		List<ItemTransportNetwork> partitions = floodPartition(owner.memberIndices, family, conveyorSegments, tubeSegments);
		for(ItemTransportNetwork part : partitions) {
			recomputePorts(part, conveyorSegments, tubeSegments, managerContainer);
			networks.add(part);
		}

		if(debugLogger != null) {
			debugLogger.accept("[ItemTransport] Removed " + blockType + " @ " + index + " — split into " + partitions.size() + " network(s).");
		}
		return true;
	}

	/**
	 * Rebuilds the network list (and each network's ports) from the current segment maps.
	 * Used after deserializing persisted topology on save load, where only the per-block
	 * segments are stored and the connected components must be re-derived.
	 */
	public static void rebuildNetworks(Map<Long, ItemTransportSegment> conveyorSegments,
									   Map<Long, ItemTransportSegment> tubeSegments,
									   List<ItemTransportNetwork> networks,
									   ManagerContainer<?> managerContainer) {
		networks.clear();
		rebuildFamily(conveyorSegments, TransportFamily.CONVEYOR, conveyorSegments, tubeSegments, networks, managerContainer);
		rebuildFamily(tubeSegments, TransportFamily.TUBE, conveyorSegments, tubeSegments, networks, managerContainer);
	}

	private static void rebuildFamily(Map<Long, ItemTransportSegment> familyMap, TransportFamily family,
									  Map<Long, ItemTransportSegment> conveyorSegments,
									  Map<Long, ItemTransportSegment> tubeSegments,
									  List<ItemTransportNetwork> networks,
									  ManagerContainer<?> managerContainer) {
		if(familyMap.isEmpty()) {
			return;
		}
		LongOpenHashSet members = new LongOpenHashSet();
		members.addAll(familyMap.keySet());
		for(ItemTransportNetwork net : floodPartition(members, family, conveyorSegments, tubeSegments)) {
			recomputePorts(net, conveyorSegments, tubeSegments, managerContainer);
			networks.add(net);
		}
	}

	public static void recomputePorts(ItemTransportNetwork net,
									  Map<Long, ItemTransportSegment> conveyorSegments,
									  Map<Long, ItemTransportSegment> tubeSegments,
									  ManagerContainer<?> managerContainer) {
		net.portIndices.clear();
		if(managerContainer == null) {
			return;
		}
		LongIterator it = net.memberIndices.iterator();
		while(it.hasNext()) {
			long member = it.nextLong();
			for(long nb : ItemTopologyUtils.faceAdjacentIndices(member)) {
				if(conveyorSegments.containsKey(nb) || tubeSegments.containsKey(nb)) {
					continue;
				}
				Inventory inventory = managerContainer.getInventory(nb);
				if(inventory != null) {
					net.portIndices.add(nb);
				}
			}
		}
	}

	private static List<ItemTransportNetwork> floodPartition(LongOpenHashSet members, TransportFamily family,
															 Map<Long, ItemTransportSegment> conveyorSegments,
															 Map<Long, ItemTransportSegment> tubeSegments) {
		LongOpenHashSet unvisited = new LongOpenHashSet(members);
		Map<Long, ItemTransportSegment> targetMap = (family == TransportFamily.CONVEYOR) ? conveyorSegments : tubeSegments;
		List<ItemTransportNetwork> result = new ArrayList<>();

		while(!unvisited.isEmpty()) {
			long seed = unvisited.iterator().nextLong();
			unvisited.remove(seed);
			ItemTransportNetwork comp = new ItemTransportNetwork(family);
			comp.memberIndices.add(seed);
			Deque<Long> queue = new ArrayDeque<>();
			queue.add(seed);
			while(!queue.isEmpty()) {
				long cur = queue.poll();
				for(long nb : ItemTopologyUtils.faceAdjacentIndices(cur)) {
					if(unvisited.contains(nb) && targetMap.containsKey(nb)) {
						unvisited.remove(nb);
						comp.memberIndices.add(nb);
						queue.add(nb);
					}
				}
			}
			result.add(comp);
		}
		return result;
	}

	public static TransportFamily familyFor(short blockType) {
		if(blockType == ElementRegistry.CONVEYOR_BELT.getId()) {
			return TransportFamily.CONVEYOR;
		}
		if(blockType == ElementRegistry.ITEM_TUBE.getId() || blockType == ElementRegistry.ITEM_PUMP.getId()) {
			return TransportFamily.TUBE;
		}
		return null;
	}
}

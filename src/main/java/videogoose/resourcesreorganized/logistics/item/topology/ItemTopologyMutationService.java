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
 * Conveyor belts are the only item transport family: face-adjacent belts form one connected component,
 * and any adjacent inventory becomes a port of that component. (A second TUBE family existed while
 * belts could not move items vertically; {@code BeltShape.TURN_UP} replaced it.)
 */
public final class ItemTopologyMutationService {

	private ItemTopologyMutationService() {
	}

	public static boolean onPlace(long index, short blockType, byte orientation,
								  Map<Long, ItemTransportSegment> conveyorSegments,
								  List<ItemTransportNetwork> networks,
								  ManagerContainer<?> managerContainer,
								  Consumer<String> debugLogger) {
		if(!ElementRegistry.isConveyorBelt(blockType)) {
			return false;
		}
		ItemTransportSegment existing = conveyorSegments.get(index);
		if(existing != null) {
			if(existing.blockType() == blockType && existing.orientation() == orientation) {
				return false;
			}
			// Same position, different block: the belt was swapped for another shape or re-placed at a new
			// rotation without a remove event reaching us. Refresh the stored facts rather than keeping the
			// old ones — the topology is what the simulator routes by, and if it disagrees with the world
			// then items route by a block the player can no longer see, which is undiagnosable in game
			// (the arrows, models and build cursor all read the world and would all look correct).
			// Network membership is keyed by position and is unaffected by the swap.
			conveyorSegments.put(index, new ItemTransportSegment(index, blockType, orientation, TransportFamily.CONVEYOR));
			if(debugLogger != null) {
				debugLogger.accept("[Conveyor] refreshed stale topology at " + index
						+ ": type " + existing.blockType() + "->" + blockType
						+ " orient " + existing.orientation() + "->" + orientation);
			}
			return true;
		}
		conveyorSegments.put(index, new ItemTransportSegment(index, blockType, orientation, TransportFamily.CONVEYOR));

		Set<Long> neighbours = ItemTopologyUtils.faceAdjacentIndices(index);
		List<ItemTransportNetwork> adjacent = new ArrayList<>();
		for(ItemTransportNetwork net : networks) {
			for(long nb : neighbours) {
				if(net.memberIndices.contains(nb)) {
					adjacent.add(net);
					break;
				}
			}
		}

		ItemTransportNetwork merged = new ItemTransportNetwork(TransportFamily.CONVEYOR);
		merged.memberIndices.add(index);
		for(ItemTransportNetwork net : adjacent) {
			merged.memberIndices.addAll(net.memberIndices);
			networks.remove(net);
		}
		networks.add(merged);
		recomputePorts(merged, conveyorSegments, managerContainer);

		if(debugLogger != null) {
			debugLogger.accept("[ItemTransport] Placed " + blockType + " @ " + index + " — networks: " + networks.size() + ", merged size: " + merged.memberIndices.size() + ", ports: " + merged.portIndices.size());
		}
		return true;
	}

	public static boolean onRemove(long index, short blockType,
								   Map<Long, ItemTransportSegment> conveyorSegments,
								   List<ItemTransportNetwork> networks,
								   ManagerContainer<?> managerContainer,
								   Consumer<String> debugLogger) {
		if(!ElementRegistry.isConveyorBelt(blockType) || conveyorSegments.remove(index) == null) {
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

		List<ItemTransportNetwork> partitions = floodPartition(owner.memberIndices, conveyorSegments);
		for(ItemTransportNetwork part : partitions) {
			recomputePorts(part, conveyorSegments, managerContainer);
			networks.add(part);
		}

		if(debugLogger != null) {
			debugLogger.accept("[ItemTransport] Removed " + blockType + " @ " + index + " — split into " + partitions.size() + " network(s).");
		}
		return true;
	}

	/**
	 * Rebuilds the network list (and each network's ports) from the current segment map.
	 * Used after deserializing persisted topology on save load, where only the per-block
	 * segments are stored and the connected components must be re-derived.
	 */
	public static void rebuildNetworks(Map<Long, ItemTransportSegment> conveyorSegments,
									   List<ItemTransportNetwork> networks,
									   ManagerContainer<?> managerContainer) {
		networks.clear();
		if(conveyorSegments.isEmpty()) {
			return;
		}
		LongOpenHashSet members = new LongOpenHashSet();
		members.addAll(conveyorSegments.keySet());
		for(ItemTransportNetwork net : floodPartition(members, conveyorSegments)) {
			recomputePorts(net, conveyorSegments, managerContainer);
			networks.add(net);
		}
	}

	public static void recomputePorts(ItemTransportNetwork net,
									  Map<Long, ItemTransportSegment> conveyorSegments,
									  ManagerContainer<?> managerContainer) {
		net.portIndices.clear();
		if(managerContainer == null) {
			return;
		}
		LongIterator it = net.memberIndices.iterator();
		while(it.hasNext()) {
			long member = it.nextLong();
			for(long nb : ItemTopologyUtils.faceAdjacentIndices(member)) {
				if(conveyorSegments.containsKey(nb)) {
					continue;
				}
				Inventory inventory = managerContainer.getInventory(nb);
				if(inventory != null) {
					net.portIndices.add(nb);
				}
			}
		}
	}

	private static List<ItemTransportNetwork> floodPartition(LongOpenHashSet members,
															 Map<Long, ItemTransportSegment> conveyorSegments) {
		LongOpenHashSet unvisited = new LongOpenHashSet(members);
		List<ItemTransportNetwork> result = new ArrayList<>();

		while(!unvisited.isEmpty()) {
			long seed = unvisited.iterator().nextLong();
			unvisited.remove(seed);
			ItemTransportNetwork comp = new ItemTransportNetwork(TransportFamily.CONVEYOR);
			comp.memberIndices.add(seed);
			Deque<Long> queue = new ArrayDeque<>();
			queue.add(seed);
			while(!queue.isEmpty()) {
				long cur = queue.poll();
				for(long nb : ItemTopologyUtils.faceAdjacentIndices(cur)) {
					if(unvisited.contains(nb) && conveyorSegments.containsKey(nb)) {
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
}

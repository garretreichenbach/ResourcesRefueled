package videogoose.resourcesreorganized.logistics.item.runtime;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferOutcome;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.planner.ItemRoute;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic transfer executor for early development/testing before StarMade inventory adapters are wired.
 */
public final class InMemoryTransferExecutor implements ItemTransferExecutor {

	private final Map<String, Map<ItemKey, Integer>> inventoryByNode = new HashMap<>();
	private final Map<String, Integer> capacityByNode = new HashMap<>();

	public void setCapacity(String nodeId, int capacity) {
		capacityByNode.put(nodeId, Math.max(1, capacity));
	}

	public void seed(String nodeId, short type, int meta, int count) {
		Map<ItemKey, Integer> inventory = inventoryByNode.computeIfAbsent(nodeId, ignored -> new HashMap<>());
		ItemKey key = new ItemKey(type, meta);
		inventory.put(key, Math.max(0, inventory.getOrDefault(key, 0) + count));
	}

	public int getCount(String nodeId, short type, int meta) {
		Map<ItemKey, Integer> inventory = inventoryByNode.getOrDefault(nodeId, new HashMap<>());
		return inventory.getOrDefault(new ItemKey(type, meta), 0);
	}

	@Override
	public ItemTransferReceipt execute(ItemTransferRequest request, ItemRoute route, long currentTick) {
		Map<ItemKey, Integer> source = inventoryByNode.computeIfAbsent(request.sourceNodeId(), ignored -> new HashMap<>());
		Map<ItemKey, Integer> destination = inventoryByNode.computeIfAbsent(request.destinationNodeId(), ignored -> new HashMap<>());

		ItemKey key = new ItemKey(request.itemType(), request.metaId());
		int available = source.getOrDefault(key, 0);
		if(available <= 0) {
			return ItemTransferReceipt.of(request, ItemTransferOutcome.FAILED, 0, "source-empty");
		}

		int destinationCapacity = capacityByNode.getOrDefault(request.destinationNodeId(), Integer.MAX_VALUE);
		int destinationLoad = destination.values().stream().mapToInt(Integer::intValue).sum();
		int destinationFree = Math.max(0, destinationCapacity - destinationLoad);
		int moved = Math.min(Math.min(available, destinationFree), Math.min(route.maxItemsPerTick(), request.count()));
		if(moved <= 0) {
			return ItemTransferReceipt.of(request, ItemTransferOutcome.FAILED, 0, "destination-full");
		}

		source.put(key, available - moved);
		destination.put(key, destination.getOrDefault(key, 0) + moved);

		ItemTransferOutcome outcome = (moved == request.count()) ? ItemTransferOutcome.SUCCESS : ItemTransferOutcome.PARTIAL;
		return ItemTransferReceipt.of(request, outcome, moved, "ok");
	}

	private record ItemKey(short type, int meta) {

		@Override
		public boolean equals(Object obj) {
			if(this == obj) return true;
			if(!(obj instanceof ItemKey(short type1, int meta1))) return false;
			return type == type1 && meta == meta1;
		}

		@Override
		public int hashCode() {
			return 31 * type + meta;
		}
	}
}


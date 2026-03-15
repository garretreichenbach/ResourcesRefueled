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

	private static final class ItemKey {
		private final short type;
		private final int meta;

		private ItemKey(short type, int meta) {
			this.type = type;
			this.meta = meta;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj) return true;
			if(!(obj instanceof ItemKey)) return false;
			ItemKey other = (ItemKey) obj;
			return type == other.type && meta == other.meta;
		}

		@Override
		public int hashCode() {
			return 31 * type + meta;
		}
	}

	private final Map<String, Map<ItemKey, Integer>> inventoryByNode = new HashMap<String, Map<ItemKey, Integer>>();
	private final Map<String, Integer> capacityByNode = new HashMap<String, Integer>();

	public void setCapacity(String nodeId, int capacity) {
		capacityByNode.put(nodeId, Math.max(1, capacity));
	}

	public void seed(String nodeId, short type, int meta, int count) {
		Map<ItemKey, Integer> inventory = inventoryByNode.computeIfAbsent(nodeId, ignored -> new HashMap<ItemKey, Integer>());
		ItemKey key = new ItemKey(type, meta);
		inventory.put(key, Math.max(0, inventory.getOrDefault(key, 0) + count));
	}

	public int getCount(String nodeId, short type, int meta) {
		Map<ItemKey, Integer> inventory = inventoryByNode.getOrDefault(nodeId, new HashMap<ItemKey, Integer>());
		return inventory.getOrDefault(new ItemKey(type, meta), 0);
	}

	@Override
	public ItemTransferReceipt execute(ItemTransferRequest request, ItemRoute route, long currentTick) {
		Map<ItemKey, Integer> source = inventoryByNode.computeIfAbsent(request.getSourceNodeId(), ignored -> new HashMap<ItemKey, Integer>());
		Map<ItemKey, Integer> destination = inventoryByNode.computeIfAbsent(request.getDestinationNodeId(), ignored -> new HashMap<ItemKey, Integer>());

		ItemKey key = new ItemKey(request.getItemType(), request.getMetaId());
		int available = source.getOrDefault(key, 0);
		if(available <= 0) {
			return ItemTransferReceipt.of(request, ItemTransferOutcome.FAILED, 0, "source-empty");
		}

		int destinationCapacity = capacityByNode.getOrDefault(request.getDestinationNodeId(), Integer.MAX_VALUE);
		int destinationLoad = destination.values().stream().mapToInt(Integer::intValue).sum();
		int destinationFree = Math.max(0, destinationCapacity - destinationLoad);
		int moved = Math.min(Math.min(available, destinationFree), Math.min(route.getMaxItemsPerTick(), request.getCount()));
		if(moved <= 0) {
			return ItemTransferReceipt.of(request, ItemTransferOutcome.FAILED, 0, "destination-full");
		}

		source.put(key, available - moved);
		destination.put(key, destination.getOrDefault(key, 0) + moved);

		ItemTransferOutcome outcome = (moved == request.getCount()) ? ItemTransferOutcome.SUCCESS : ItemTransferOutcome.PARTIAL;
		return ItemTransferReceipt.of(request, outcome, moved, "ok");
	}
}


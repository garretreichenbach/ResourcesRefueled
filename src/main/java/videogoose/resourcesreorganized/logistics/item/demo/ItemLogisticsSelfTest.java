package videogoose.resourcesreorganized.logistics.item.demo;

import videogoose.resourcesreorganized.logistics.item.graph.ItemEdge;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNodeType;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferOutcome;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.runtime.InMemoryTransferExecutor;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemLogisticsSystemModule;

public final class ItemLogisticsSelfTest {

	private ItemLogisticsSelfTest() {
	}

	public static void main(String[] args) {
		InMemoryTransferExecutor executor = new InMemoryTransferExecutor();
		executor.setCapacity("dst", 64);
		executor.seed("src", (short) 5, 0, 30);

		ItemLogisticsSystemModule module = new ItemLogisticsSystemModule(64, 8, 2, 4, () -> true, executor);
		module.registerNode(new ItemNode("src", ItemNodeType.INVENTORY_PORT, 20));
		module.registerNode(new ItemNode("belt", ItemNodeType.CONVEYOR, 8));
		module.registerNode(new ItemNode("dst", ItemNodeType.INVENTORY_PORT, 20));
		module.connectNodes(new ItemEdge("src", "belt", 8));
		module.connectNodes(new ItemEdge("belt", "dst", 8));

		ItemTransferRequest request = new ItemTransferRequest("src", "dst", (short) 5, 0, 20, 0, true);
		if(!module.enqueue(request)) {
			throw new IllegalStateException("failed to enqueue self-test request");
		}

		int moved = 0;
		for(long tick = 1; tick <= 5; tick++) {
			moved += module.tickBatch(tick).stream().mapToInt(ItemTransferReceipt::getMovedCount).sum();
		}

		if(moved < 20) {
			throw new IllegalStateException("expected to move at least 20 items, moved=" + moved);
		}
		if(executor.getCount("dst", (short) 5, 0) < 20) {
			throw new IllegalStateException("destination did not receive expected items");
		}

		if(module.tickBatch(6).stream().anyMatch(r -> r.getOutcome() == ItemTransferOutcome.FAILED)) {
			throw new IllegalStateException("unexpected failed transfer after completion");
		}

		System.out.println("ItemLogisticsSelfTest passed. diagnostics=" + module.diagnosticsSnapshot());
	}
}


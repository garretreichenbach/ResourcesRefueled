package videogoose.resourcesreorganized.logistics.item.demo;

import videogoose.resourcesreorganized.logistics.item.graph.ItemEdge;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNodeType;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;
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
		executor.setCapacity("beltDst", 64);
		executor.setCapacity("tubeDst", 128);
		executor.seed("src", (short) 5, 0, 80);
		executor.seed("beltTap", (short) 5, 0, 60);

		ItemLogisticsSystemModule module = new ItemLogisticsSystemModule(128, 16, 2, 4, () -> true, executor);
		module.registerNode(new ItemNode("src", ItemNodeType.INVENTORY_PORT, 32, TransportFamily.NEUTRAL, true, -1));
		module.registerNode(new ItemNode("beltTap", ItemNodeType.CONVEYOR, 12, TransportFamily.CONVEYOR, true, -1));
		module.registerNode(new ItemNode("beltA", ItemNodeType.CONVEYOR, 8, TransportFamily.CONVEYOR, false, 0b0001));
		module.registerNode(new ItemNode("beltB", ItemNodeType.CONVEYOR, 8, TransportFamily.CONVEYOR, false, 0b0010));
		module.registerNode(new ItemNode("beltDst", ItemNodeType.INVENTORY_PORT, 32, TransportFamily.NEUTRAL, true, -1));
		module.registerNode(new ItemNode("tubeA", ItemNodeType.TUBE, 24, TransportFamily.TUBE, false, -1));
		module.registerNode(new ItemNode("tubePump", ItemNodeType.PUMP, 24, TransportFamily.TUBE, false, -1));
		module.registerNode(new ItemNode("tubeDst", ItemNodeType.INVENTORY_PORT, 32, TransportFamily.NEUTRAL, true, -1));

		module.connectNodes(new ItemEdge("beltTap", "beltA", 8, TransportFamily.CONVEYOR, false, 0b0001));
		module.connectNodes(new ItemEdge("beltTap", "beltB", 8, TransportFamily.CONVEYOR, false, 0b0010));
		module.connectNodes(new ItemEdge("beltA", "beltDst", 8, TransportFamily.CONVEYOR, false, 0b0001));
		module.connectNodes(new ItemEdge("beltB", "beltDst", 8, TransportFamily.CONVEYOR, false, 0b0010));
		module.connectNodes(new ItemEdge("src", "tubeA", 24, TransportFamily.TUBE, true, -1));
		module.connectNodes(new ItemEdge("tubeA", "tubePump", 24, TransportFamily.TUBE, true, -1));
		module.connectNodes(new ItemEdge("tubePump", "tubeDst", 24, TransportFamily.TUBE, true, -1));

		ItemTransferRequest beltChannel0 = new ItemTransferRequest("beltTap", "beltDst", (short) 5, 0, 20, 0, true, TransportFamily.CONVEYOR, 0, false, false, true, false);
		ItemTransferRequest beltChannel1 = new ItemTransferRequest("beltTap", "beltDst", (short) 5, 0, 20, 0, true, TransportFamily.CONVEYOR, 1, false, false, true, false);
		ItemTransferRequest beltPortRequired = new ItemTransferRequest("beltTap", "beltDst", (short) 5, 0, 5, 0, true, TransportFamily.CONVEYOR, 0, false, false, true, true);
		ItemTransferRequest beltSourcePortRequired = new ItemTransferRequest("beltTap", "beltDst", (short) 5, 0, 5, 0, true, TransportFamily.CONVEYOR, 0, false, false, true, true, false);
		ItemTransferRequest tubeVertical = new ItemTransferRequest("src", "tubeDst", (short) 5, 0, 40, 0, true, TransportFamily.TUBE, -1, true, true);
		ItemTransferRequest invalidVerticalBelt = new ItemTransferRequest("src", "tubeDst", (short) 5, 0, 5, 0, true, TransportFamily.CONVEYOR, -1, false, false);

		if(!module.enqueue(beltChannel0) || !module.enqueue(beltChannel1) || !module.enqueue(beltPortRequired) || !module.enqueue(beltSourcePortRequired) || !module.enqueue(tubeVertical) || !module.enqueue(invalidVerticalBelt)) {
			throw new IllegalStateException("failed to enqueue one or more self-test requests");
		}

		int moved = 0;
		boolean sawExpectedRetry = false;
		for(long tick = 1; tick <= 5; tick++) {
			for(ItemTransferReceipt receipt : module.tickBatch(tick)) {
				moved += receipt.getMovedCount();
				if(receipt.getOutcome() == ItemTransferOutcome.RETRY_QUEUED && (receipt.getMessage().contains("family=CONVEYOR") || receipt.getMessage().contains("no-route"))) {
					sawExpectedRetry = true;
				}
			}
		}

		if(moved < 80) {
			throw new IllegalStateException("expected to move at least 80 items, moved=" + moved);
		}
		if(executor.getCount("beltDst", (short) 5, 0) < 40) {
			throw new IllegalStateException("belt destination did not receive expected channel traffic");
		}
		if(executor.getCount("tubeDst", (short) 5, 0) < 40) {
			throw new IllegalStateException("tube destination did not receive expected vertical traffic");
		}
		if(executor.getCount("beltTap", (short) 5, 0) > 20) {
			throw new IllegalStateException("belt source should have drained significantly via direct-adjacent extraction");
		}
		if(!sawExpectedRetry) {
			throw new IllegalStateException("expected conveyor vertical restriction retry was not observed");
		}

		if(module.tickBatch(6).stream().anyMatch(r -> r.getOutcome() == ItemTransferOutcome.FAILED)) {
			throw new IllegalStateException("unexpected failed transfer after completion");
		}

		System.out.println("ItemLogisticsSelfTest passed. diagnostics=" + module.diagnosticsSnapshot());
	}
}


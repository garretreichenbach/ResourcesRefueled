package videogoose.resourcesreorganized.logistics.item.runtime;

import videogoose.resourcesreorganized.logistics.item.api.ItemLogisticsApi;
import videogoose.resourcesreorganized.logistics.item.api.ItemMutationIngress;
import videogoose.resourcesreorganized.logistics.item.graph.ItemEdge;
import videogoose.resourcesreorganized.logistics.item.graph.ItemLogisticsGraph;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.planner.ItemRoutePlanner;
import videogoose.resourcesreorganized.logistics.item.queue.DeferredTransferQueue;
import videogoose.resourcesreorganized.logistics.item.queue.ItemTransferQueue;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class ItemLogisticsSystemModule implements ItemLogisticsApi, ItemMutationIngress {

	private final ItemLogisticsGraph graph;
	private final ItemTransferQueue queue;
	private final ItemLogisticsTickProcessor processor;
	private final ItemLogisticsDiagnostics diagnostics;

	public ItemLogisticsSystemModule(int queueCapacity, int transfersPerTick, int retryDelayTicks, int maxAttempts, BooleanSupplier failOpenSupplier, ItemTransferExecutor transferExecutor) {
		graph = new ItemLogisticsGraph();
		queue = new ItemTransferQueue(queueCapacity);
		diagnostics = new ItemLogisticsDiagnostics();
		processor = new ItemLogisticsTickProcessor(queue, new DeferredTransferQueue(), new TransferAttemptLedger(), new ItemRoutePlanner(graph), transferExecutor, new LogisticsFailOpenPolicy(failOpenSupplier), diagnostics, transfersPerTick, retryDelayTicks, maxAttempts);
	}

	@Override
	public void registerNode(ItemNode node) {
		graph.registerNode(node);
	}

	@Override
	public void removeNode(String nodeId) {
		graph.removeNode(nodeId);
	}

	@Override
	public void connectNodes(ItemEdge edge) {
		graph.connect(edge);
	}

	@Override
	public void disconnectNodes(String fromNodeId, String toNodeId) {
		graph.disconnect(fromNodeId, toNodeId);
	}

	@Override
	public boolean enqueue(ItemTransferRequest request) {
		boolean accepted = queue.offer(request);
		if(accepted) {
			diagnostics.recordQueued();
		}
		return accepted;
	}

	@Override
	public Optional<ItemTransferReceipt> tick(long currentTick) {
		List<ItemTransferReceipt> receipts = processor.tick(currentTick);
		if(receipts.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(receipts.get(receipts.size() - 1));
	}

	public List<ItemTransferReceipt> tickBatch(long currentTick) {
		return processor.tick(currentTick);
	}

	@Override
	public boolean tryRouteMutation(ItemTransferRequest request) {
		return enqueue(request);
	}

	public String diagnosticsSnapshot() {
		return diagnostics.snapshot();
	}
}


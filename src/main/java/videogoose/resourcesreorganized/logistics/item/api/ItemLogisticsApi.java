package videogoose.resourcesreorganized.logistics.item.api;

import videogoose.resourcesreorganized.logistics.item.graph.ItemEdge;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;

import java.util.Optional;

public interface ItemLogisticsApi {

	void registerNode(ItemNode node);

	void removeNode(String nodeId);

	void connectNodes(ItemEdge edge);

	void disconnectNodes(String fromNodeId, String toNodeId);

	boolean enqueue(ItemTransferRequest request);

	Optional<ItemTransferReceipt> tick(long currentTick);
}


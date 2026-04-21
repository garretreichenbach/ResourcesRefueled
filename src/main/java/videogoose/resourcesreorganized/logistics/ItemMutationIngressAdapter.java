package videogoose.resourcesreorganized.logistics;

import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNodeType;
import videogoose.resourcesreorganized.logistics.item.graph.ItemEdge;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.runtime.InventoryReferenceRegistry;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemEndpointPolicyRegistry;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemLogisticsSystemModule;
import videogoose.resourcesreorganized.logistics.item.runtime.LiveTransferExecutor;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.manager.ItemLogisticsManager;

/**
 * Lightweight bridge from inventory mutation probes into the item logistics runtime.
 * Current behavior mirrors mutations as local inventory intents for early integration.
 */
public final class ItemMutationIngressAdapter {

	private ItemMutationIngressAdapter() {
	}

	public static void captureInventoryMutation(String operation, Inventory inventory, short type, int count, int metaId) {
		tryHandleInventoryMutation(operation, inventory, type, count, metaId);
	}

	public static boolean tryHandleInventoryMutation(String operation, Inventory inventory, short type, int count, int metaId) {
		if(!ConfigManager.isLogisticsInterceptEnabled()) {
			return false;
		}
		if(LiveTransferExecutor.isExecuting()) {
			return false;
		}
		if(inventory == null || type <= 0) {
			return false;
		}

		int normalizedCount = Math.max(1, Math.abs(count));
		String inventoryNodeId = inventoryNodeId(inventory);
		InventoryReferenceRegistry.register(inventoryNodeId, inventory);
		String adjacentNodeId = adjacentNodeId(inventory);
		long tick = System.currentTimeMillis() / 50L;
		boolean inbound = !"inc".equals(operation) || count >= 0;
		String sourceNodeId = inbound ? adjacentNodeId : inventoryNodeId;
		String destinationNodeId = inbound ? inventoryNodeId : adjacentNodeId;
		boolean sourceRequiresInventoryPort = ItemEndpointPolicyRegistry.requiresInventoryPort(sourceNodeId);
		boolean destinationRequiresInventoryPort = ItemEndpointPolicyRegistry.requiresInventoryPort(destinationNodeId);
		if(ConfigManager.isItemConveyorRequirePortForAdvanced()) {
			sourceRequiresInventoryPort = true;
			destinationRequiresInventoryPort = true;
		}

		try {
			ItemLogisticsSystemModule module = ItemLogisticsManager.getSystemModule();
			module.registerNode(new ItemNode(inventoryNodeId, ItemNodeType.INVENTORY_PORT, 64, TransportFamily.NEUTRAL, true, -1));
			module.registerNode(new ItemNode(adjacentNodeId, ItemNodeType.CONVEYOR, 16, TransportFamily.CONVEYOR, true, -1));
			module.connectNodes(new ItemEdge(inventoryNodeId, adjacentNodeId, 16, TransportFamily.CONVEYOR, false, -1));
			module.connectNodes(new ItemEdge(adjacentNodeId, inventoryNodeId, 16, TransportFamily.CONVEYOR, false, -1));

			ItemTransferRequest request = new ItemTransferRequest(
					sourceNodeId,
					destinationNodeId,
					type,
					metaId,
					normalizedCount,
					tick,
					true,
					TransportFamily.CONVEYOR,
					-1,
					false,
					false,
					true,
					sourceRequiresInventoryPort,
					destinationRequiresInventoryPort);

			if(module.enqueue(request)) {
				module.tickBatch(tick);
				if(ConfigManager.isDebugMode()) {
					ResourcesReorganized instance = ResourcesReorganized.getInstance();
					if(instance != null) {
						instance.logInfo("[ItemLogistics] ingress op=" + operation + " " + sourceNodeId + " -> " + destinationNodeId + " count=" + normalizedCount + " sourceRequirePort=" + sourceRequiresInventoryPort + " destinationRequirePort=" + destinationRequiresInventoryPort);
					}
				}
				return true;
			}
			return false;
		} catch(Exception exception) {
			ResourcesReorganized instance = ResourcesReorganized.getInstance();
			if(instance != null && ConfigManager.isDebugMode()) {
				instance.logWarning("[ItemLogistics] Ingress adapter failed for op=" + operation + " (" + exception.getClass().getSimpleName() + ")");
			}
			if(!ConfigManager.isLogisticsFailOpen()) {
				throw exception;
			}
			return false;
		}
	}

	private static String inventoryNodeId(Inventory inventory) {
		return "inv:" + Integer.toHexString(System.identityHashCode(inventory));
	}

	private static String adjacentNodeId(Inventory inventory) {
		return "adj:" + Integer.toHexString(System.identityHashCode(inventory));
	}
}


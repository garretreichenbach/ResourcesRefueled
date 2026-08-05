package videogoose.resourcesreorganized.logistics;

import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferOutcome;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.runtime.InventoryReferenceRegistry;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemEndpointPolicyRegistry;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemLogisticsSystemModule;
import videogoose.resourcesreorganized.logistics.item.runtime.LiveTransferExecutor;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.manager.ItemLogisticsManager;

import java.util.List;

/**
 * Lightweight bridge from inventory mutation probes into the item logistics runtime.
 * Intercept is gated on the inventory being a registered logistics endpoint
 * (see {@link InventoryReferenceRegistry}); unregistered inventories pass through unchanged.
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

		String inventoryNodeId = inventoryNodeId(inventory);
		if(!InventoryReferenceRegistry.isRegistered(inventoryNodeId)) {
			return false;
		}

		int normalizedCount = Math.max(1, Math.abs(count));
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
					true,
					sourceRequiresInventoryPort,
					destinationRequiresInventoryPort);

			if(!module.enqueue(request)) {
				return false;
			}
			List<ItemTransferReceipt> receipts = module.tickBatch(tick);
			boolean handled = false;
			for(ItemTransferReceipt receipt : receipts) {
				if(receipt.requestId().equals(request.requestId()) && receipt.outcome() == ItemTransferOutcome.SUCCESS) {
					handled = true;
					break;
				}
			}
			if(handled && ConfigManager.isDebugMode()) {
				ResourcesReorganized instance = ResourcesReorganized.getInstance();
				if(instance != null) {
					instance.logInfo("[ItemLogistics] ingress op=" + operation + " " + sourceNodeId + " -> " + destinationNodeId + " count=" + normalizedCount + " handled");
				}
			}
			return handled;
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


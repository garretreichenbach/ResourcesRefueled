package videogoose.resourcesreorganized.logistics;

import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNodeType;
import videogoose.resourcesreorganized.logistics.item.graph.TransportFamily;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemLogisticsSystemModule;
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
		if(!ConfigManager.isLogisticsInterceptEnabled()) {
			return;
		}
		if(inventory == null || type <= 0) {
			return;
		}

		int normalizedCount = Math.max(1, Math.abs(count));
		String inventoryNodeId = inventoryNodeId(inventory);

		try {
			ItemLogisticsSystemModule module = ItemLogisticsManager.getSystemModule();
			module.registerNode(new ItemNode(inventoryNodeId, ItemNodeType.INVENTORY_PORT, 64, TransportFamily.NEUTRAL, true, -1));

			ItemTransferRequest request = new ItemTransferRequest(
					inventoryNodeId,
					inventoryNodeId,
					type,
					metaId,
					normalizedCount,
					System.currentTimeMillis() / 50L,
					true,
					TransportFamily.CONVEYOR,
					-1,
					false,
					false,
					true,
					false);

			if(module.enqueue(request)) {
				module.tickBatch(System.currentTimeMillis() / 50L);
			}
		} catch(Exception exception) {
			ResourcesReorganized instance = ResourcesReorganized.getInstance();
			if(instance != null && ConfigManager.isDebugMode()) {
				instance.logWarning("[ItemLogistics] Ingress adapter failed for op=" + operation + " (" + exception.getClass().getSimpleName() + ")");
			}
		}
	}

	private static String inventoryNodeId(Inventory inventory) {
		return "inv:" + Integer.toHexString(System.identityHashCode(inventory));
	}
}


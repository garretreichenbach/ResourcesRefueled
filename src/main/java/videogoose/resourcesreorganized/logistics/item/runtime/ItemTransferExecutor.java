package videogoose.resourcesreorganized.logistics.item.runtime;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.planner.ItemRoute;

public interface ItemTransferExecutor {

	ItemTransferReceipt execute(ItemTransferRequest request, ItemRoute route, long currentTick);
}


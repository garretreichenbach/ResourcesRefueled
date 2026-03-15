package videogoose.resourcesreorganized.manager;

import videogoose.resourcesreorganized.logistics.item.runtime.InMemoryTransferExecutor;
import videogoose.resourcesreorganized.logistics.item.runtime.ItemLogisticsSystemModule;

public final class ItemLogisticsManager {

	private static ItemLogisticsSystemModule systemModule;

	private ItemLogisticsManager() {
	}

	public static void initialize() {
		if(systemModule != null) {
			return;
		}
		systemModule = new ItemLogisticsSystemModule(
				ConfigManager.getItemLogisticsQueueCapacity(),
				ConfigManager.getItemLogisticsTransfersPerTick(),
				ConfigManager.getItemLogisticsRetryDelayTicks(),
				ConfigManager.getItemLogisticsMaxAttempts(),
				ConfigManager::isLogisticsFailOpen,
				new InMemoryTransferExecutor());
	}

	public static ItemLogisticsSystemModule getSystemModule() {
		if(systemModule == null) {
			initialize();
		}
		return systemModule;
	}

	public static void shutdown() {
		systemModule = null;
	}
}


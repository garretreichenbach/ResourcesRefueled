package videogoose.resourcesreorganized.manager;

import videogoose.resourcesreorganized.logistics.item.runtime.ItemLogisticsSystemModule;
import videogoose.resourcesreorganized.logistics.item.runtime.LiveTransferExecutor;

public final class ItemLogisticsManager {

	private static ItemLogisticsSystemModule systemModule;

	private ItemLogisticsManager() {
	}

	public static void initialize() {
		if(systemModule != null) {
			return;
		}
		systemModule = new ItemLogisticsSystemModule(ConfigManager.getItemLogisticsQueueCapacity(), ConfigManager.getItemLogisticsTransfersPerTick(), ConfigManager.getItemLogisticsRetryDelayTicks(), ConfigManager.getItemLogisticsMaxAttempts(), ConfigManager::isLogisticsFailOpen, new LiveTransferExecutor());
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


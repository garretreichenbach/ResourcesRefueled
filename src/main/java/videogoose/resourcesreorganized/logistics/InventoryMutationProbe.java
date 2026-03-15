package videogoose.resourcesreorganized.logistics;

import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventoryMultMod;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.manager.ConfigManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary probe sink for validating inventory mutation hook coverage before interception logic is enabled.
 */
public final class InventoryMutationProbe {

	private static final long LOG_COOLDOWN_MS = 500L;
	private static final ConcurrentMap<String, Long> lastLogByKey = new ConcurrentHashMap<>();
	private static final AtomicInteger suppressedLogCount = new AtomicInteger(0);

	private InventoryMutationProbe() {
	}

	public static void logInc(Inventory inventory, int slot, short type, int count) {
		log("inc", inventory, "slot=" + slot + " type=" + type + " count=" + count);
	}

	public static void logPut(Inventory inventory, int slot, short type, int count, int meta) {
		log("put", inventory, "slot=" + slot + " type=" + type + " count=" + count + " meta=" + meta);
	}

	public static void logHandleReceived(Inventory inventory, InventoryMultMod mod, Object inventoryInterface) {
		String interfaceName = (inventoryInterface == null) ? "null" : inventoryInterface.getClass().getSimpleName();
		log("handleReceived", inventory, "mod=" + mod + " iface=" + interfaceName);
	}

	public static void logDeserialize(Inventory inventory) {
		log("deserialize", inventory, "");
	}

	public static void logDeserializeSlot(Inventory inventory) {
		log("deserializeSlot", inventory, "");
	}

	public static void logSwitchOrCombine(Inventory inventory, int slot, int otherSlot, int subSlotFromOther, Inventory otherInventory, int count) {
		String other = (otherInventory == null) ? "null" : otherInventory.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(otherInventory));
		log("doSwitchSlotsOrCombine", inventory, "slot=" + slot + " otherSlot=" + otherSlot + " subSlot=" + subSlotFromOther + " count=" + count + " other=" + other);
	}

	public static void logRemoteAddItem(Object remoteSector, Object pos, short type, int metaId, int count) {
		String holder = (remoteSector == null) ? "null" : remoteSector.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(remoteSector));
		log("remoteSectorAddItem", null, "remote=" + holder + " pos=" + pos + " type=" + type + " count=" + count + " meta=" + metaId);
	}

	private static void log(String operation, Inventory inventory, String details) {
		if(!ConfigManager.isLogisticsProbeEnabled()) {
			return;
		}

		String holder = (inventory == null) ? "null" : inventory.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(inventory));
		String key = operation + "|" + holder + "|" + details;
		long now = System.currentTimeMillis();
		Long previous = lastLogByKey.get(key);
		if(previous != null && now - previous < LOG_COOLDOWN_MS) {
			suppressedLogCount.incrementAndGet();
			return;
		}
		lastLogByKey.put(key, now);

		ResourcesReorganized instance = ResourcesReorganized.getInstance();
		if(instance == null) {
			return;
		}

		int suppressed = suppressedLogCount.getAndSet(0);
		String suffix = (suppressed > 0) ? " suppressed=" + suppressed : "";
		instance.logInfo("[INV-HOOK] op=" + operation + " holder=" + holder + " " + details + suffix);
	}
}


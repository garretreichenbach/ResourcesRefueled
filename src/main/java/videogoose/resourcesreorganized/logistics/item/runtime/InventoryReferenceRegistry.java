package videogoose.resourcesreorganized.logistics.item.runtime;

import org.schema.game.common.data.player.inventory.Inventory;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping logistics node IDs to live {@link Inventory} instances via weak references.
 * The ingress adapter registers inventories as mutations are intercepted; the executor resolves them at transfer time.
 * Only {@code "inv:"}-prefixed node IDs are stored — {@code "adj:"} nodes are virtual conveyor endpoints.
 */
public final class InventoryReferenceRegistry {

	private static final ConcurrentHashMap<String, WeakReference<Inventory>> REGISTRY = new ConcurrentHashMap<>();

	private InventoryReferenceRegistry() {
	}

	public static void register(String nodeId, Inventory inventory) {
		if(nodeId == null || inventory == null) {
			return;
		}
		REGISTRY.put(nodeId, new WeakReference<>(inventory));
	}

	public static Optional<Inventory> resolve(String nodeId) {
		if(nodeId == null) {
			return Optional.empty();
		}
		WeakReference<Inventory> ref = REGISTRY.get(nodeId);
		if(ref == null) {
			return Optional.empty();
		}
		Inventory inventory = ref.get();
		if(inventory == null) {
			REGISTRY.remove(nodeId);
			return Optional.empty();
		}
		return Optional.of(inventory);
	}

	public static void remove(String nodeId) {
		if(nodeId != null) {
			REGISTRY.remove(nodeId);
		}
	}

	public static void clear() {
		REGISTRY.clear();
	}
}

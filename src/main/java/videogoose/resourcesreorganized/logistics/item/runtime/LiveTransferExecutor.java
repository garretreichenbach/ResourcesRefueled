package videogoose.resourcesreorganized.logistics.item.runtime;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferOutcome;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferReceipt;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;
import videogoose.resourcesreorganized.logistics.item.planner.ItemRoute;
import videogoose.resourcesreorganized.manager.ConfigManager;

import java.util.Optional;

/**
 * Transfer executor that performs real inventory mutations through the StarMade {@link Inventory} API.
 * <p>
 * Uses a thread-local re-entrancy guard to prevent infinite recursion: inventory methods like
 * {@code decreaseBatch} and {@code incExistingOrNextFreeSlotWithoutException} call {@code inc}/{@code put}
 * internally, which would re-trigger the mixin hooks without this guard.
 */
public final class LiveTransferExecutor implements ItemTransferExecutor {

	private static final String PREFIX_INV = "inv:";
	private static final String PREFIX_ADJ = "adj:";

	private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/**
	 * Returns {@code true} if the current thread is inside an executor transfer.
	 * The ingress adapter checks this to avoid re-entrant interception.
	 */
	public static boolean isExecuting() {
		return EXECUTING.get();
	}

	@Override
	public ItemTransferReceipt execute(ItemTransferRequest request, ItemRoute route, long currentTick) {
		EXECUTING.set(Boolean.TRUE);
		try {
			return doExecute(request, route);
		} finally {
			EXECUTING.set(Boolean.FALSE);
		}
	}

	private ItemTransferReceipt doExecute(ItemTransferRequest request, ItemRoute route) {
		String sourceId = request.sourceNodeId();
		String destId = request.destinationNodeId();
		short type = request.itemType();
		int meta = request.metaId();
		int requested = Math.min(request.count(), route.maxItemsPerTick());

		boolean sourceIsInv = sourceId.startsWith(PREFIX_INV);
		boolean destIsInv = destId.startsWith(PREFIX_INV);

		if(sourceIsInv && destIsInv) {
			return executeCrossInventory(request, sourceId, destId, type, meta, requested);
		} else if(destIsInv) {
			return executeInbound(request, destId, type, meta, requested);
		} else if(sourceIsInv) {
			return executeOutbound(request, sourceId, type, meta, requested);
		} else {
			return fail(request, "both-virtual: " + sourceId + " -> " + destId);
		}
	}

	/**
	 * Inbound: adj → inv. Adds items to the destination inventory.
	 */
	private ItemTransferReceipt executeInbound(ItemTransferRequest request, String destId, short type, int meta, int requested) {
		Optional<Inventory> destOpt = InventoryReferenceRegistry.resolve(destId);
		if(destOpt.isEmpty()) {
			return fail(request, "destination-unresolved: " + destId);
		}
		Inventory dest = destOpt.get();

		int slot = dest.incExistingOrNextFreeSlotWithoutException(type, requested, meta);
		int moved = (slot >= 0) ? requested : 0;

		return receipt(request, moved, requested);
	}

	/**
	 * Outbound: inv → adj. Removes items from the source inventory.
	 */
	private ItemTransferReceipt executeOutbound(ItemTransferRequest request, String sourceId, short type, int meta, int requested) {
		Optional<Inventory> sourceOpt = InventoryReferenceRegistry.resolve(sourceId);
		if(sourceOpt.isEmpty()) {
			return fail(request, "source-unresolved: " + sourceId);
		}
		Inventory source = sourceOpt.get();

		int available = source.getOverallQuantity(type);
		if(available <= 0) {
			return fail(request, "source-empty");
		}

		int toRemove = Math.min(available, requested);
		IntOpenHashSet modified = new IntOpenHashSet();
		source.decreaseBatch(type, toRemove, modified);

		return receipt(request, toRemove, requested);
	}

	/**
	 * Cross-inventory: inv → inv. Removes from source, adds to destination.
	 */
	private ItemTransferReceipt executeCrossInventory(ItemTransferRequest request, String sourceId, String destId, short type, int meta, int requested) {
		Optional<Inventory> sourceOpt = InventoryReferenceRegistry.resolve(sourceId);
		Optional<Inventory> destOpt = InventoryReferenceRegistry.resolve(destId);
		if(sourceOpt.isEmpty()) {
			return fail(request, "source-unresolved: " + sourceId);
		}
		if(destOpt.isEmpty()) {
			return fail(request, "destination-unresolved: " + destId);
		}
		Inventory source = sourceOpt.get();
		Inventory dest = destOpt.get();

		int available = source.getOverallQuantity(type);
		if(available <= 0) {
			return fail(request, "source-empty");
		}

		int toMove = Math.min(available, requested);

		int slot = dest.incExistingOrNextFreeSlotWithoutException(type, toMove, meta);
		if(slot < 0) {
			return fail(request, "destination-full");
		}

		IntOpenHashSet modified = new IntOpenHashSet();
		source.decreaseBatch(type, toMove, modified);

		return receipt(request, toMove, requested);
	}

	private static ItemTransferReceipt receipt(ItemTransferRequest request, int moved, int requested) {
		if(moved <= 0) {
			return fail(request, "nothing-moved");
		}
		ItemTransferOutcome outcome = (moved >= requested) ? ItemTransferOutcome.SUCCESS : ItemTransferOutcome.PARTIAL;
		return ItemTransferReceipt.of(request, outcome, moved, "ok");
	}

	private static ItemTransferReceipt fail(ItemTransferRequest request, String reason) {
		if(ConfigManager.isDebugMode()) {
			ResourcesReorganized instance = ResourcesReorganized.getInstance();
			if(instance != null) {
				instance.logWarning("[ItemLogistics] Transfer failed: " + reason + " req=" + request.requestId());
			}
			return ItemTransferReceipt.of(request, ItemTransferOutcome.FALLBACK_TO_VANILLA, 0, reason);
		}
		return ItemTransferReceipt.of(request, ItemTransferOutcome.FAILED, 0, reason);
	}
}

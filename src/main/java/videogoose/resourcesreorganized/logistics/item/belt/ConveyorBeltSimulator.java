package videogoose.resourcesreorganized.logistics.item.belt;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;
import videogoose.resourcesreorganized.logistics.item.runtime.LiveTransferExecutor;
import videogoose.resourcesreorganized.logistics.item.topology.ItemTransportSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Server-side simulation of items physically travelling along conveyor belts.
 * <p>
 * Each belt cell holds at most one {@link BeltItem}. Items advance by {@code speed} per tick; when a
 * stack reaches the end of its cell it moves to the forward cell (if a belt and empty), is inserted
 * into a forward inventory port, or stalls (back-pressure). Empty cells whose backward face touches an
 * inventory pull a fresh stack from it.
 * <p>
 * All inventory mutations are wrapped in {@link LiveTransferExecutor}'s managed guard so the
 * {@code inc}/{@code put} mixin ingress does not re-route them through the legacy instant-transfer path.
 */
public final class ConveyorBeltSimulator {

	private ConveyorBeltSimulator() {
	}

	/**
	 * Advances one tick of belt simulation. Returns {@code true} if any cell state changed
	 * (item moved, picked up, or delivered) so the caller can flag a client sync.
	 */
	public static boolean tick(Map<Long, ItemTransportSegment> conveyorSegments,
							   Map<Long, BeltItem> cellItems,
							   ManagerContainer<?> managerContainer,
							   float speed,
							   int maxPull,
							   Consumer<String> debugLogger) {
		boolean changed = false;

		// Advance leading items first so a queue can shuffle forward in a single tick.
		List<Long> order = new ArrayList<>(cellItems.keySet());
		order.sort((a, b) -> Float.compare(cellItems.get(b).progress, cellItems.get(a).progress));

		for(long cellIndex : order) {
			BeltItem item = cellItems.get(cellIndex);
			if(item == null) {
				continue;
			}
			ItemTransportSegment seg = conveyorSegments.get(cellIndex);
			if(seg == null) {
				// Belt was removed out from under the item; drop it (eject handling is a later concern).
				cellItems.remove(cellIndex);
				changed = true;
				continue;
			}

			float next = item.progress + speed;
			if(next < 1.0f) {
				item.progress = next;
				changed = true;
				continue;
			}

			long forward = BeltDirection.forwardIndex(cellIndex, seg.orientation());
			if(conveyorSegments.containsKey(forward)) {
				if(!cellItems.containsKey(forward)) {
					cellItems.remove(cellIndex);
					item.progress = 0.0f;
					cellItems.put(forward, item);
					changed = true;
				} else if(item.progress != 1.0f) {
					item.progress = 1.0f;
					changed = true;
				}
			} else {
				Inventory dest = managerContainer.getInventory(forward);
				if(dest != null && insert(dest, item)) {
					cellItems.remove(cellIndex);
					changed = true;
					if(debugLogger != null) {
						debugLogger.accept("[Conveyor] delivered type=" + item.type + " x" + item.count + " -> inv@" + forward);
					}
				} else if(item.progress != 1.0f) {
					item.progress = 1.0f;
					changed = true;
				}
			}
		}

		// Pull fresh stacks onto empty cells from a backward-adjacent inventory.
		for(ItemTransportSegment seg : conveyorSegments.values()) {
			long cellIndex = seg.blockIndex();
			if(cellItems.containsKey(cellIndex)) {
				continue;
			}
			long input = BeltDirection.backwardIndex(cellIndex, seg.orientation());
			Inventory source = managerContainer.getInventory(input);
			if(source == null) {
				continue;
			}
			BeltItem pulled = extract(source, maxPull);
			if(pulled != null) {
				cellItems.put(cellIndex, pulled);
				changed = true;
				if(debugLogger != null) {
					debugLogger.accept("[Conveyor] pulled type=" + pulled.type + " x" + pulled.count + " from inv@" + input + " -> cell@" + cellIndex);
				}
			}
		}

		return changed;
	}

	/**
	 * Tries to move the stack into the destination, respecting its volume capacity.
	 * Inserts as much as fits (draining the stack over multiple ticks when nearly full) and
	 * returns {@code true} only once the stack is fully delivered. When nothing fits the item
	 * stays on the belt and the caller stalls it — back-pressure until space frees up.
	 */
	private static boolean insert(Inventory dest, BeltItem item) {
		int fits = dest.canPutInHowMuch(item.type, item.count, item.metaId);
		if(fits <= 0) {
			return false;
		}
		int toInsert = Math.min(fits, item.count);
		LiveTransferExecutor.beginManaged();
		try {
			int slot = dest.incExistingOrNextFreeSlotWithoutException(item.type, toInsert, item.metaId);
			if(slot < 0) {
				return false;
			}
			dest.sendInventoryModification(slot);
			item.count -= toInsert;
			return item.count <= 0;
		} finally {
			LiveTransferExecutor.endManaged();
		}
	}

	private static BeltItem extract(Inventory source, int maxPull) {
		for(InventorySlot slot : source.getMap().values()) {
			if(slot == null || slot.isEmpty() || slot.isMetaItem()) {
				continue;
			}
			short type = slot.getType();
			if(type <= 0) {
				continue;
			}
			int available = slot.count();
			if(available <= 0) {
				continue;
			}
			int take = Math.min(available, maxPull);
			IntOpenHashSet modified = new IntOpenHashSet();
			LiveTransferExecutor.beginManaged();
			try {
				source.decreaseBatch(type, take, modified);
			} finally {
				LiveTransferExecutor.endManaged();
			}
			if(!modified.isEmpty()) {
				source.sendInventoryModification(modified);
			}
			return new BeltItem(type, slot.metaId, take, 0.0f);
		}
		return null;
	}
}

package videogoose.resourcesreorganized.logistics.item.belt;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
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
 * stack reaches the end of its cell it moves past the cell's exit face &mdash; into the next belt (if
 * that belt's entry face points back at us and it is empty), into an inventory port, or it stalls
 * (back-pressure). Empty cells whose entry face touches an inventory pull a fresh stack from it.
 * <p>
 * Which faces those are comes from the cell's {@link BeltShape}, i.e. from which belt block the player
 * placed &mdash; a straight belt runs back-to-front, a turn enters through a side or leaves through the
 * top.
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

			BeltShape shape = BeltShape.orStraight(seg.blockType());
			Stall stall = advance(shape, seg, cellIndex, item, conveyorSegments, cellItems, managerContainer, debugLogger);
			if(stall == null) {
				item.stall = StallReason.NONE;
				changed = true;
			} else {
				// Nothing took the stack: stall it at the exit face. Back-pressure, not a dropped item.
				// A change is only flagged the first tick it stalls (or when the reason changes) — a
				// permanently blocked belt must not keyframe every tick.
				boolean firstStall = item.progress != 1.0f;
				boolean reasonChanged = item.stall != stall.reason();
				item.progress = 1.0f;
				item.stall = stall.reason();
				if(firstStall || reasonChanged) {
					changed = true;
					// Something refused this stack, so verify both ends of the failed hand-off against the
					// world before believing it. If the topology had gone stale the repair lands here and the
					// stack moves next tick; if the belt really is mis-aimed nothing changes.
					String repairs = reconcile(conveyorSegments, managerContainer, cellIndex)
							+ reconcile(conveyorSegments, managerContainer, stall.at());
					if(debugLogger != null) {
						debugLogger.accept("[Conveyor] STALLED at " + describe(cellIndex) + " type=" + item.type
								+ " x" + item.count + " shape=" + shape + " — " + stall.reason().message()
								+ " (blocked at " + describe(stall.at()) + ")" + repairs);
					}
				}
			}
		}

		// Pull fresh stacks onto empty cells from an inventory behind any of their entry faces.
		for(ItemTransportSegment seg : conveyorSegments.values()) {
			long cellIndex = seg.blockIndex();
			if(cellItems.containsKey(cellIndex)) {
				continue;
			}
			BeltShape shape = BeltShape.orStraight(seg.blockType());
			for(int i = 0, n = shape.entryCount(); i < n; i++) {
				long input = shape.inputIndex(cellIndex, seg.orientation(), i);
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
					break; // cell holds one stack; the remaining entry faces get a turn next tick
				}
			}
		}

		return changed;
	}

	/**
	 * Tries to move {@code item} out of {@code cellIndex} through each of its shape's exit faces in
	 * turn, taking the first that accepts.
	 * <p>
	 * A shape with several exits (a splitter) is served in declaration order here; choosing <i>which</i>
	 * exit on policy grounds — round-robin, priority, filtering — is the routing hook that belongs to
	 * the individual block, not to this loop.
	 *
	 * @return {@code null} if the stack moved on or was delivered, otherwise why every exit refused it
	 * and which position blocked. A stalled belt is visually identical to a broken one — the item simply
	 * parks at the exit face — so the reason is the only way to tell "correctly waiting" from
	 * "misconfigured".
	 */
	private static Stall advance(BeltShape shape, ItemTransportSegment seg, long cellIndex, BeltItem item,
								 Map<Long, ItemTransportSegment> conveyorSegments,
								 Map<Long, BeltItem> cellItems,
								 ManagerContainer<?> managerContainer,
								 Consumer<String> debugLogger) {
		Stall first = null;
		for(int i = 0, n = shape.exitCount(); i < n; i++) {
			long forward = shape.outputIndex(cellIndex, seg.orientation(), i);
			StallReason refusal;
			ItemTransportSegment forwardSeg = conveyorSegments.get(forward);
			if(forwardSeg != null) {
				// The next belt only takes the stack through one of its own entry faces: a turn pointed
				// the wrong way is a dead end that backs the line up, not a silent side-load.
				BeltShape forwardShape = BeltShape.orStraight(forwardSeg.blockType());
				if(!forwardShape.acceptsFrom(forward, forwardSeg.orientation(), cellIndex)) {
					refusal = StallReason.NEXT_BELT_MISAIMED;
				} else if(cellItems.containsKey(forward)) {
					refusal = StallReason.NEXT_BELT_OCCUPIED;
				} else {
					cellItems.remove(cellIndex);
					item.progress = 0.0f;
					cellItems.put(forward, item);
					return null;
				}
			} else {
				Inventory dest = managerContainer.getInventory(forward);
				if(dest == null) {
					refusal = StallReason.NO_DESTINATION;
				} else if(dest.canPutInHowMuch(item.type, item.count, item.metaId) <= 0) {
					refusal = StallReason.DESTINATION_FULL;
				} else if(insert(dest, item)) {
					cellItems.remove(cellIndex);
					if(debugLogger != null) {
						debugLogger.accept("[Conveyor] delivered type=" + item.type + " x" + item.count + " -> inv@" + forward);
					}
					return null;
				} else {
					refusal = StallReason.DESTINATION_PARTIAL;
				}
			}
			// Report the first exit's refusal: with one exit that is the only one, and for a splitter the
			// primary exit is the one the player most likely meant to work.
			if(first == null) {
				first = new Stall(refusal, forward);
			}
		}
		return first;
	}

	/** Why a stack could not leave its cell, and the position that refused it. */
	private record Stall(StallReason reason, long at) {
	}

	/**
	 * Re-reads a cell's block type and orientation from the world and updates the topology map if they
	 * have drifted, returning a note for the log when something was actually repaired.
	 * <p>
	 * The topology stores what the block-add event reported, while everything the player sees — models,
	 * the build arrow, the mod's debug arrows — reads the segment buffer. If a belt is swapped or
	 * re-placed without a remove event reaching us, the two disagree and the simulator routes items by a
	 * block that is no longer there. That failure is undiagnosable in game, because every visual cue
	 * reads the world and all of them look correct.
	 * <p>
	 * Called only when a stack has already failed to move, so the segment-buffer lookups cost nothing on
	 * a healthy belt. A genuinely mis-aimed belt reconciles to no change and stays stalled, which is
	 * correct; a stale one is repaired and the stack moves on the next tick.
	 */
	private static String reconcile(Map<Long, ItemTransportSegment> conveyorSegments,
									ManagerContainer<?> managerContainer, long index) {
		ItemTransportSegment stored = conveyorSegments.get(index);
		if(stored == null) {
			return "";
		}
		try {
			SegmentPiece live = managerContainer.getSegmentController()
					.getSegmentBuffer().getPointUnsave(posOf(index));
			if(live == null) {
				conveyorSegments.remove(index);
				return "  [repaired: topology had a belt at " + describe(index) + " but the world has none]";
			}
			if(live.getType() == stored.blockType() && live.getOrientation() == stored.orientation()) {
				return "";
			}
			if(!BeltShape.isBelt(live.getType())) {
				conveyorSegments.remove(index);
				return "  [repaired: " + describe(index) + " is no longer a belt]";
			}
			conveyorSegments.put(index, new ItemTransportSegment(
					index, live.getType(), live.getOrientation(), stored.family()));
			return "  [repaired stale topology at " + describe(index)
					+ ": type " + stored.blockType() + "->" + live.getType()
					+ " orient " + stored.orientation() + "->" + live.getOrientation() + "]";
		} catch(Exception e) {
			return "";
		}
	}

	private static Vector3i posOf(long index) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		return pos;
	}

	/** {@code (x, y, z)} for a block index, so log lines can be matched against what the player sees. */
	private static String describe(long index) {
		Vector3i pos = new Vector3i();
		ElementCollection.getPosFromIndex(index, pos);
		return "(" + pos.x + ", " + pos.y + ", " + pos.z + ")";
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

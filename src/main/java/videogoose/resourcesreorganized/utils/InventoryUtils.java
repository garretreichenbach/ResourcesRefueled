package videogoose.resourcesreorganized.utils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;

public class InventoryUtils {

	public static int removeItems(Inventory inventory, short type, int amount) {
		IntOpenHashSet slotSet = inventory.getAllSlots();
		int removed = 0;
		for(int slotId : slotSet) {
			if(removed >= amount) {
				break;
			}
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() == type) {
				int toRemove = Math.min(slot.count(), amount - removed);
				slot.setCount(slot.count() - toRemove);
				removed += toRemove;
			}
		}
		return removed;
	}
}

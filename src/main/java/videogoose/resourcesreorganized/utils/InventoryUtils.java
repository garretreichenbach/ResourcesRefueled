package videogoose.resourcesreorganized.utils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.json.JSONObject;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;

import javax.annotation.Nullable;

public class InventoryUtils {

	public static int countItems(Inventory inventory, short type, @Nullable JSONObject customData) {
		IntOpenHashSet slotSet = inventory.getAllSlots();
		int count = 0;
		for(int slotId : slotSet) {
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() == type) {
				if(slot.hasCustomData() && customData != null) {
					JSONObject slotData = slot.getCustomData();
					boolean matches = slotData.equals(customData);
					if(matches) {
						count += slot.count();
					}
				} else if((!slot.hasCustomData() || customData != null) && (slot.hasCustomData() || customData == null)) {
					// Slot matches type and we don't care about custom data, or slot matches type and doesn't have custom data which is what we require - count items
					count += slot.count();
				}
			}
		}
		return count;
	}

	public static void putItems(Inventory inventory, short type, int amount, @Nullable JSONObject customData) {
		IntOpenHashSet slotSet = inventory.getAllSlots();
		for(int slotId : slotSet) {
			if(amount <= 0) {
				break;
			}
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() == type) {
				if(slot.hasCustomData() && customData != null) {
					JSONObject slotData = slot.getCustomData();
					boolean matches = slotData.equals(customData);
					if(matches) {
						slot.setCount(slot.count() + amount);
						amount = 0;
						break;
					}
				} else if((!slot.hasCustomData() || customData != null) && (slot.hasCustomData() || customData == null)) {
					// Slot matches type and we don't care about custom data, or slot matches type and doesn't have custom data which is what we require - add items
					slot.setCount(slot.count() + amount);
					amount = 0;
					break;
				}
			}
		}
		inventory.sendAll();
	}

	public static int removeItems(Inventory inventory, short type, int amount, @Nullable JSONObject customData) {
		IntOpenHashSet slotSet = inventory.getAllSlots();
		int removed = 0;
		for(int slotId : slotSet) {
			if(removed >= amount) {
				break;
			}
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() == type) {
				if(slot.hasCustomData() && customData != null) {
					JSONObject slotData = slot.getCustomData();
					boolean matches = slotData.equals(customData);
					if(matches) {
						int toRemove = Math.min(slot.count(), amount - removed);
						slot.setCount(slot.count() - toRemove);
						removed += toRemove;
					}
				} else if((!slot.hasCustomData() || customData != null) && (slot.hasCustomData() || customData == null)) {
					// Slot matches type and we don't care about custom data, or slot matches type and doesn't have custom data which is what we require - remove items
					int toRemove = Math.min(slot.count(), amount - removed);
					slot.setCount(slot.count() - toRemove);
					removed += toRemove;
				}
			}
		}
		inventory.sendAll();
		return removed;
	}
}

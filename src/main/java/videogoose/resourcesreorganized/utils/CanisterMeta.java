package videogoose.resourcesreorganized.utils;

import org.json.JSONObject;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;
import videogoose.resourcesreorganized.element.ElementRegistry;

/**
 * Central utility for reading and writing canister slot metadata.
 * <p>
 * All canisters are represented by a single item type ({@link ElementRegistry#FLUID_CANISTER}).
 * Their filled/empty state and the fluid they contain is encoded entirely via
 * {@link InventorySlot#getCustomData()} using the following JSON keys:
 * <ul>
 *   <li>{@code "fluid_id"}     — {@code short} element ID of the stored fluid. 0 = empty.</li>
 *   <li>{@code "fluid_amount"} — {@code double} current fluid units stored in this canister.</li>
 *   <li>{@code "capacity"}     — {@code double} maximum fluid units this canister can hold.</li>
 *   <li>{@code "name"}         — engine-recognised display-name override shown in the tooltip.</li>
 *   <li>{@code "description"}  — engine-recognised description override shown in the tooltip.</li>
 * </ul>
 * An empty canister has no metadata (or has {@code fluid_id == 0}).
 */
public final class CanisterMeta {

	// ---- JSON key constants ------------------------------------------------
	public static final String KEY_FLUID_ID     = "fluid_id";
	public static final String KEY_FLUID_AMOUNT = "fluid_amount";
	public static final String KEY_CAPACITY     = "capacity";

	private CanisterMeta() {}

	// =========================================================================
	// Writers
	// =========================================================================

	/**
	 * Stamps the slot at {@code slotIndex} with filled-canister metadata.
	 *
	 * @param inventory   The inventory that owns the slot.
	 * @param slotIndex   The slot to stamp.
	 * @param fluidId     Element ID of the fluid stored.
	 * @param amount      Fluid units stored.
	 * @param capacity    Maximum fluid units the canister can hold.
	 */
	public static void writeFilled(Inventory inventory, int slotIndex, short fluidId, double amount, double capacity) {
		InventorySlot slot = inventory.getSlot(slotIndex);
		if(slot == null) return;
		writeFilledSlot(slot, fluidId, amount, capacity);
	}

	/**
	 * Stamps {@code slot} directly with filled-canister metadata.
	 */
	public static void writeFilledSlot(InventorySlot slot, short fluidId, double amount, double capacity) {
		JSONObject meta = slot.getOrCreateCustomData();
		meta.put(KEY_FLUID_ID,     (int) fluidId);   // JSONObject stores numbers as int/double
		meta.put(KEY_FLUID_AMOUNT, amount);
		meta.put(KEY_CAPACITY,     capacity);

		// Compute display strings
		String fluidName = resolveFluidName(fluidId);
		meta.put("name",        "Fluid Canister (" + fluidName + ")");
		meta.put("description", String.format("%.1f / %.1f L of %s", amount, capacity, fluidName));
	}

	/**
	 * Clears all canister metadata from the slot, making it an empty canister.
	 */
	public static void writeEmpty(InventorySlot slot) {
		slot.clearCustomData();
	}

	// =========================================================================
	// Readers
	// =========================================================================

	/** Returns {@code true} if the slot holds a canister with fluid inside. */
	public static boolean isFilled(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return false;
		JSONObject meta = slot.getCustomData();
		return meta.optInt(KEY_FLUID_ID, 0) != 0 && meta.optDouble(KEY_FLUID_AMOUNT, 0.0) > 0.0;
	}

	/** Returns {@code true} if the slot holds an empty canister (no fluid metadata). */
	public static boolean isEmpty(InventorySlot slot) {
		return !isFilled(slot);
	}

	/**
	 * Returns the fluid element ID stored in this canister slot, or {@code 0} if empty.
	 */
	public static short getFluidId(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return 0;
		return (short) slot.getCustomData().optInt(KEY_FLUID_ID, 0);
	}

	/**
	 * Returns the fluid amount stored in this canister slot, or {@code 0.0} if empty.
	 */
	public static double getFluidAmount(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return 0.0;
		return slot.getCustomData().optDouble(KEY_FLUID_AMOUNT, 0.0);
	}

	/**
	 * Returns the capacity stored in this canister slot, or the config default if absent.
	 */
	public static double getCapacity(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return 0.0;
		return slot.getCustomData().optDouble(KEY_CAPACITY, 0.0);
	}

	// =========================================================================
	// Inventory-level helpers
	// =========================================================================

	/**
	 * Counts the number of filled canister slots (determined by metadata) in the given inventory.
	 */
	public static int countFilled(Inventory inventory, short canisterId) {
		int count = 0;
		for(int slotId : inventory.getAllSlots()) {
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() == canisterId && isFilled(slot)) count++;
		}
		return count;
	}

	/**
	 * Finds the first slot in {@code inventory} that is a {@link ElementRegistry#FLUID_CANISTER}
	 * and has fluid metadata matching {@code fluidId} (or any fluid if {@code fluidId == 0}).
	 * Returns {@code -1} if none found.
	 */
	public static int findFilledSlot(Inventory inventory, short canisterId, short fluidId) {
		for(int slotId : inventory.getAllSlots()) {
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() != canisterId) continue;
			if(!isFilled(slot)) continue;
			if(fluidId != 0 && getFluidId(slot) != fluidId) continue;
			return slotId;
		}
		return -1;
	}

	/**
	 * Finds the first slot in {@code inventory} that is a {@link ElementRegistry#FLUID_CANISTER}
	 * and is empty (no fluid metadata).
	 * Returns {@code -1} if none found.
	 */
	public static int findEmptySlot(Inventory inventory, short canisterId) {
		for(int slotId : inventory.getAllSlots()) {
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() != canisterId) continue;
			if(isEmpty(slot)) return slotId;
		}
		return -1;
	}

	// =========================================================================
	// Internal helpers
	// =========================================================================

	private static String resolveFluidName(short fluidId) {
		if(fluidId == 0) return "Empty";
		if(ElementKeyMap.isValidType(fluidId)) return ElementKeyMap.getInfo(fluidId).getName();
		return "Unknown Fluid";
	}
}


package videogoose.resourcesreorganized.data;

import org.json.JSONObject;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventorySlot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Central utility for fluid-type properties and per-slot fluid metadata.
 * <p>
 * <b>Fluid-type registry</b> — properties that apply to every container (tank network
 * or canister) holding a given fluid element ID, registered once at startup:
 * <ul>
 *   <li>Volatile fluids cause tank explosions when the hosting block is destroyed.</li>
 * </ul>
 * <p>
 * <b>Slot metadata</b> — all canisters share a single item type; their fill state is
 * encoded in {@link InventorySlot#getCustomData()} using these JSON keys:
 * <ul>
 *   <li>{@code "fluid_id"}     — {@code short} element ID of the stored fluid. 0 = empty.</li>
 *   <li>{@code "fluid_amount"} — {@code double} current fluid units stored.</li>
 *   <li>{@code "capacity"}     — {@code double} maximum fluid units the canister holds.</li>
 *   <li>{@code "is_volatile"}  — {@code boolean} whether the stored fluid is volatile.</li>
 *   <li>{@code "name"}         — engine display-name override shown in the tooltip.</li>
 *   <li>{@code "description"}  — engine description override shown in the tooltip.</li>
 * </ul>
 * An empty canister has no metadata (or {@code fluid_id == 0}).
 */
public final class FluidMeta {

	// =========================================================================
	// JSON key constants
	// =========================================================================

	public static final String KEY_FLUID_ID     = "fluid_id";
	public static final String KEY_FLUID_AMOUNT = "fluid_amount";
	public static final String KEY_CAPACITY     = "capacity";
	public static final String KEY_IS_VOLATILE  = "is_volatile";

	// =========================================================================
	// Fluid-type registry
	// =========================================================================

	/** Element IDs of fluids that are volatile (cause explosions on tank destruction). */
	private static final Set<Short> VOLATILE_FLUIDS = new HashSet<>();

	private FluidMeta() {}

	/**
	 * Marks {@code fluidId} as a volatile fluid.
	 * Call from the fluid element's {@code postInitData()}.
	 */
	public static void registerVolatile(short fluidId) {
		VOLATILE_FLUIDS.add(fluidId);
	}

	/** Returns an unmodifiable view of all registered volatile fluid IDs. */
	public static Set<Short> getVolatileFluids() {
		return Collections.unmodifiableSet(VOLATILE_FLUIDS);
	}

	/**
	 * Returns {@code true} if {@code fluidId} is registered as volatile.
	 * {@code 0} (empty / no fluid) is never volatile.
	 */
	public static boolean isVolatile(short fluidId) {
		if(fluidId == 0) return false;
		return VOLATILE_FLUIDS.contains(fluidId);
	}

	// =========================================================================
	// Slot writers
	// =========================================================================

	/**
	 * Stamps the slot at {@code slotIndex} in {@code inventory} with filled-canister metadata.
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
		meta.put(KEY_FLUID_ID,     (int) fluidId);
		meta.put(KEY_FLUID_AMOUNT, amount);
		meta.put(KEY_CAPACITY,     capacity);
		meta.put(KEY_IS_VOLATILE,  isVolatile(fluidId));

		String fluidName = resolveFluidName(fluidId);
		meta.put("name",        "Fluid Canister (" + fluidName + ")");
		meta.put("description", String.format("%.1f / %.1f L of %s%s",
				amount, capacity, fluidName,
				isVolatile(fluidId) ? " [Volatile]" : ""));
	}

	/**
	 * Clears all slot metadata, making the canister empty.
	 */
	public static void writeEmpty(InventorySlot slot) {
		slot.clearCustomData();
	}

	// =========================================================================
	// Slot readers
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

	/** Returns the fluid element ID stored in this slot, or {@code 0} if empty. */
	public static short getFluidId(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return 0;
		return (short) slot.getCustomData().optInt(KEY_FLUID_ID, 0);
	}

	/** Returns the fluid amount stored in this slot, or {@code 0.0} if empty. */
	public static double getFluidAmount(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return 0.0;
		return slot.getCustomData().optDouble(KEY_FLUID_AMOUNT, 0.0);
	}

	/** Returns the capacity stored in this slot, or {@code 0.0} if absent. */
	public static double getCapacity(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return 0.0;
		return slot.getCustomData().optDouble(KEY_CAPACITY, 0.0);
	}

	/**
	 * Returns {@code true} if the slot's canister contains a volatile fluid.
	 * Always {@code false} for empty canisters.
	 */
	public static boolean isVolatile(InventorySlot slot) {
		if(slot == null || !slot.hasCustomData()) return false;
		return slot.getCustomData().optBoolean(KEY_IS_VOLATILE, false);
	}

	// =========================================================================
	// Inventory-level helpers
	// =========================================================================

	/** Counts filled canister slots (by metadata) in the given inventory. */
	public static int countFilled(Inventory inventory, short canisterId) {
		int count = 0;
		for(int slotId : inventory.getAllSlots()) {
			InventorySlot slot = inventory.getSlot(slotId);
			if(slot.getType() == canisterId && isFilled(slot)) count++;
		}
		return count;
	}

	/**
	 * Finds the first filled-canister slot matching {@code fluidId} (pass {@code 0} for any fluid).
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
	 * Finds the first empty-canister slot (no fluid metadata).
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

package videogoose.resourcesreorganized.element.block.systems;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.player.inventory.InventorySlot;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.element.block.Block;
import videogoose.resourcesreorganized.data.FluidMeta;

public class FluidPort extends Block {

	public FluidPort() {
		super("Fluid Port");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.description = "A port for inputting or outputting fluids to/from machines and tank. " +
				"Connect to pipes and pumps to transfer fluids in or out of a machine or tank.";
		blockInfo.price = (int) (Blocks.STORAGE.getInfo().price);
		blockInfo.mass = Blocks.STORAGE.getInfo().mass;
		blockInfo.volume = Blocks.STORAGE.getInfo().volume;
		blockInfo.maxHitPointsFull = Blocks.STORAGE.getInfo().maxHitPointsFull;
		blockInfo.shoppable = true;
		blockInfo.blended = true;
		blockInfo.canActivate = true;
		blockInfo.inventoryType = 6; //Other
	}

	@Override
	public void postInitData() {

	}

	@Override
	public void initResources() {

	}

	/**
	 * Type-only pre-check for drag-drop: any canister could potentially be an input.
	 * Use {@link #isInputItem(InventorySlot)} for the definitive metadata check.
	 */
	public static boolean isInputItem(short id) {
		return id == ElementRegistry.FLUID_CANISTER.getId();
	}

	/**
	 * Definitive check: a canister slot is a valid input only if it contains fluid.
	 */
	public static boolean isInputItem(InventorySlot slot) {
		if(slot == null) return false;
		if(slot.getType() != ElementRegistry.FLUID_CANISTER.getId()) return false;
		return FluidMeta.isFilled(slot);
	}

	/**
	 * Type-only pre-check for drag-drop: any canister could potentially be an output.
	 * Use {@link #isOutputItem(InventorySlot)} for the definitive metadata check.
	 */
	public static boolean isOutputItem(short id) {
		return id == ElementRegistry.FLUID_CANISTER.getId();
	}

	/**
	 * Definitive check: a canister slot is a valid output only if it is empty.
	 */
	public static boolean isOutputItem(InventorySlot slot) {
		if(slot == null) return false;
		if(slot.getType() != ElementRegistry.FLUID_CANISTER.getId()) return false;
		return FluidMeta.isEmpty(slot);
	}
}

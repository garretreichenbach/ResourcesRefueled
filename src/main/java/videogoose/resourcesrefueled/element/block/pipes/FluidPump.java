package videogoose.resourcesrefueled.element.block.pipes;

import videogoose.resourcesrefueled.element.block.Block;

public class FluidPump extends Block {

	public FluidPump() {
		super("Fluid Pump");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.description = "An active pump that moves fluids through a connected pipe network.\n" +
				"Requires power to operate. Can be toggled on or off via a logic signal.";
		blockInfo.placable = true;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "FluidPipes";
	}

	@Override
	public void postInitData() {}

	@Override
	public void initResources() {
		// TODO: custom pump textures
	}
}

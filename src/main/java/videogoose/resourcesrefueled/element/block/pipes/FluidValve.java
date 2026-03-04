package videogoose.resourcesrefueled.element.block.pipes;

import videogoose.resourcesrefueled.element.block.Block;

public class FluidValve extends Block {

	public FluidValve() {
		super("Fluid Valve");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.description = "A passive gate that controls fluid flow direction in a pipe network.\n" +
				"Can be opened or closed via a logic signal.";
		blockInfo.placable = true;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "FluidPipes";
	}

	@Override
	public void postInitData() {}

	@Override
	public void initResources() {
		// TODO: custom valve textures
	}
}

package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.element.block.Block;

public class FluidValve extends Block {

	public FluidValve() {
		super("Fluid Valve");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = ElementRegistry.pipesCategory;
		blockInfo.description = "A passive gate that controls fluid flow direction in a pipe network.\nCan be opened or closed via a logic signal.";
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

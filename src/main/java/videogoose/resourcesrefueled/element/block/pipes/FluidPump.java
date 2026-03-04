package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.element.block.Block;

public class FluidPump extends Block {

	public FluidPump() {
		super("Fluid Pump");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = ElementRegistry.pipesCategory;
		blockInfo.description = "An active pump that moves fluids through a connected pipe network.\nRequires power to operate. Can be toggled on or off via a logic signal.";
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

package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

public class FluidPipe extends Block {

	public FluidPipe() {
		super("Fluid Pipe");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.description = "A conduit for transporting fluids between tanks, pumps, valves, and filters.\nConnect blocks of the same fluid network together to form a pipe system.";
		blockInfo.placable = true;
		blockInfo.canActivate = false;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "FluidPipes";
	}

	@Override
	public void postInitData() {
	}

	@Override
	public void initResources() {
		// TODO: custom pipe textures
	}
}

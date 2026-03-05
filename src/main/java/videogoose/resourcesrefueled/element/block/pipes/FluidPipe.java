package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

public class FluidPipe extends Block {

	public FluidPipe() {
		super("Fluid Pipe");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.BASIC_FACTORY.getInfo().type;
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
		// Pipe blocks use 3D mesh models for visual connectivity (straight, elbow, T-junction, etc.)
		// rather than CTM. Model variants and textures go here once assets are ready.
	}
}

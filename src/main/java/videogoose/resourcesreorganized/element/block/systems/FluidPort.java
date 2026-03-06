package videogoose.resourcesreorganized.element.block.systems;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

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
	}

	@Override
	public void postInitData() {

	}

	@Override
	public void initResources() {

	}
}

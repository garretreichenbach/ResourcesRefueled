package videogoose.resourcesreorganized.element.block.inventory;

import api.config.BlockConfig;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

public class ItemTube extends Block {

	public ItemTube() {
		super("Item Tube");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
	}

	@Override
	public void postInitData() {}

	@Override
	public void initResources() {

	}
}

package videogoose.resourcesreorganized.element.block.inventory;

import api.config.BlockConfig;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

public class ItemPump extends Block {

	public ItemPump() {
		super("Item Pump");
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

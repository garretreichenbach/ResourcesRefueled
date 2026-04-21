package videogoose.resourcesreorganized.element.block.inventory;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

public class ConveyorBelt extends Block {

	public ConveyorBelt() {
		super("Conveyor Belt");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = """
				Basic item transport line.
				Extracts from adjacent inventories without a pump.
				Supports horizontal and diagonal routing only (no vertical transfer).
				Use inventory ports for advanced filtering, splitting, and combining behavior.""";
		blockInfo.placable = true;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "ItemTransport";
		blockInfo.blockStyle = BlockStyle.SPRITE;
		blockInfo.blended = true;
	}

	@Override
	public void postInitData() {}

	@Override
	public void initResources() {

	}
}

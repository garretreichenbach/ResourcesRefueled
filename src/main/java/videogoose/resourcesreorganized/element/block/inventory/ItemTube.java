package videogoose.resourcesreorganized.element.block.inventory;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

public class ItemTube extends Block {

	public ItemTube() {
		super("Item Tube");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = """
				High-speed item transport conduit.
				Supports vertical movement in addition to horizontal runs.
				Faster than conveyor belts, but intended for simpler, more restrictive routing behavior.
				Pair with Item Pumps to drive extraction and throughput.""";
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

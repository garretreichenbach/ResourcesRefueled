package videogoose.resourcesreorganized.element.block.pipes;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

public class PipeValve extends Block {

	public PipeValve() {
		super("Pipe Valve");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[]{0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = "A passive gate that controls fluid flow direction in a pipe network.\nCan be opened or closed via a logic signal.";
		blockInfo.placable = true;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "FluidPipes";
		blockInfo.blockStyle = BlockStyle.SPRITE;
		blockInfo.blended = true;
		blockInfo.drawOnlyInBuildMode = true;
		//Todo: Custom model for this, for now just use the regular pipe model
		blockInfo.lodShapeString = Blocks.YELLOW_PAINT.getInfo().lodShapeString;
	}

	@Override
	public void postInitData() {

	}

	@Override
	public void initResources() {

	}
}

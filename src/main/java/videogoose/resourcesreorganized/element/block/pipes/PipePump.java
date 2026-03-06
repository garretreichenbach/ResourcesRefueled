package videogoose.resourcesreorganized.element.block.pipes;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;

public class PipePump extends Block {

	public PipePump() {
		super("Pipe Pump");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = "An active pump that moves fluids through a connected pipe network.\nRequires power to operate. Can be toggled on or off via a logic signal.";
		blockInfo.placable = true;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "FluidPipes";
		blockInfo.blockStyle = BlockStyle.SPRITE;
		blockInfo.blended = true;
		blockInfo.drawOnlyInBuildMode = true;
		//Todo: Custom model for this, for now just use the regular pipe model
		blockInfo.lodShapeString = Blocks.ORANGE_PAINT.getInfo().lodShapeString;
		blockInfo.lodShapeStringActive = Blocks.BLUE_PAINT.getInfo().lodShapeString; //Swap model for visualization during testing
		blockInfo.lodActivationAnimationStyle = 1;
	}

	@Override
	public void postInitData() {}

	@Override
	public void initResources() {

	}
}

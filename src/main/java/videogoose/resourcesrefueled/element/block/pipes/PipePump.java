package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

public class PipePump extends Block {

	public PipePump() {
		super("Pipe Pump");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
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
		//Todo: Custom model for this, for now just use the regular pipe model
		blockInfo.lodShapeStyle = Blocks.ORANGE_PAINT.getInfo().lodShapeStyle;
		blockInfo.lodActivationAnimationStyle = Blocks.ORANGE_PAINT.getInfo().lodActivationAnimationStyle;
		blockInfo.lodCollision = Blocks.ORANGE_PAINT.getInfo().lodCollision;
		blockInfo.lodCollisionPhysical = Blocks.ORANGE_PAINT.getInfo().lodCollisionPhysical;
		blockInfo.lodShapeString = Blocks.ORANGE_PAINT.getInfo().lodShapeString;
		blockInfo.lodUseDetailCollision = Blocks.ORANGE_PAINT.getInfo().lodUseDetailCollision;
		blockInfo.cubeCubeCollision = Blocks.ORANGE_PAINT.getInfo().cubeCubeCollision;
	}
}

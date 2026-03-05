package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

public class PipeValve extends Block {

	public PipeValve() {
		super("Pipe Valve");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[]{0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = "A passive gate that controls fluid flow direction in a pipe network.\nCan be opened or closed via a logic signal.";
		blockInfo.placable = true;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = "FluidPipes";
	}

	@Override
	public void postInitData() {

	}

	@Override
	public void initResources() {
		//Todo: Custom model for this, for now just use the regular pipe model
		blockInfo.lodShapeStyle = Blocks.YELLOW_PAINT.getInfo().lodShapeStyle;
		blockInfo.lodActivationAnimationStyle = Blocks.YELLOW_PAINT.getInfo().lodActivationAnimationStyle;
		blockInfo.lodCollision = Blocks.YELLOW_PAINT.getInfo().lodCollision;
		blockInfo.lodCollisionPhysical = Blocks.YELLOW_PAINT.getInfo().lodCollisionPhysical;
		blockInfo.lodShapeString = Blocks.YELLOW_PAINT.getInfo().lodShapeString;
		blockInfo.lodUseDetailCollision = Blocks.YELLOW_PAINT.getInfo().lodUseDetailCollision;
		blockInfo.cubeCubeCollision = Blocks.YELLOW_PAINT.getInfo().cubeCubeCollision;
	}
}

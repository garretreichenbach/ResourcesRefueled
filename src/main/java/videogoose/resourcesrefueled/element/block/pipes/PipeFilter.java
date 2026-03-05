package videogoose.resourcesrefueled.element.block.pipes;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

public class PipeFilter extends Block {

	public PipeFilter() {
		super("Pipe Filter");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = "A filter that restricts which fluid types may pass through a section of pipe network.\nConfigure via the block's interface to whitelist or blacklist specific fluids.";
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
		blockInfo.lodShapeStyle = Blocks.RED_PAINT.getInfo().lodShapeStyle;
		blockInfo.lodActivationAnimationStyle = Blocks.RED_PAINT.getInfo().lodActivationAnimationStyle;
		blockInfo.lodCollision = Blocks.RED_PAINT.getInfo().lodCollision;
		blockInfo.lodCollisionPhysical = Blocks.RED_PAINT.getInfo().lodCollisionPhysical;
		blockInfo.lodShapeString = Blocks.RED_PAINT.getInfo().lodShapeString;
		blockInfo.lodUseDetailCollision = Blocks.RED_PAINT.getInfo().lodUseDetailCollision;
		blockInfo.cubeCubeCollision = Blocks.RED_PAINT.getInfo().cubeCubeCollision;
	}
}

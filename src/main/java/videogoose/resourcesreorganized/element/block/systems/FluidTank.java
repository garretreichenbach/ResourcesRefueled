package videogoose.resourcesreorganized.element.block.systems;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;
import videogoose.resourcesreorganized.manager.ResourceManager;

/**
 * A generic pressurised fluid storage block with no special properties or interactions beyond being a storage tank.
 * Intended for use in a variety of fluid storage applications, and as a base for more specialised tank types in the future (e.g. cryogenic tanks, high-pressure tanks, etc.).
 */
public class FluidTank extends Block {

	/** Convenience constructor with default capacity and volatility. */
	public FluidTank() {
		super("Fluid Tank");
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.description = "A pressurized storage tank for holding fluids.\nConnect to pipes and pumps to fill or drain.";
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
		short textureId = (short) ResourceManager.getTexture("fluid_tank").getTextureId();
		blockInfo.setTextureId(new short[] {textureId, textureId, textureId, textureId, textureId, textureId});
	}
}


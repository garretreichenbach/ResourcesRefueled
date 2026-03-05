package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

/**
 * A generic pressurised fluid storage block with no special properties or interactions beyond being a storage tank.
 * Intended for use in a variety of fluid storage applications, and as a base for more specialised tank types in the future (e.g. cryogenic tanks, high-pressure tanks, etc.).
 */
public class FluidTank extends Block {

	/** Convenience constructor with default capacity and volatility. */
	public FluidTank() {
		super("Fluid Tank");
	}

	// -------------------------------------------------------------------------

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.BASIC_FACTORY.getInfo().type;
		blockInfo.description = "A pressurized storage tank for holding fluids.\nConnect to pipes and pumps to fill or drain.";
		blockInfo.price = (int) (Blocks.BASIC_FACTORY.getInfo().price * 2);
		blockInfo.mass = Blocks.BASIC_FACTORY.getInfo().mass * 1.5f;
		blockInfo.volume = Blocks.BASIC_FACTORY.getInfo().volume * 0.5f;   // compact pressure vessel
		blockInfo.maxHitPointsFull = Blocks.BASIC_FACTORY.getInfo().maxHitPointsFull * 2; // reinforced, but volatile
		blockInfo.shoppable = true;
		blockInfo.canActivate = false; // activated through the manager module
		blockInfo.systemBlock = true;
	}


	@Override
	public void postInitData() {

	}

	@Override
	public void initResources() {
		// CTM (Connected Texture Method) for tank faces is set up in ResourceManager.loadResources()
		// using StarLoaderTexture.newCTMRegionFromSheet() + tankRegion.buildHelper(FLUID_TANK.getId()).
		// This block uses a 47-variant sprite sheet (fluid_tank_ctm47.png) so each face shows the
		// correct sealed-plate or flange/port tile depending on which neighbours are present.
		// blockInfo.extendedTexture = true and the base tile index from tankRegion.baseIndex are
		// applied in initData() once the region is available.
		// Pipe blocks (FluidPipe etc.) use 3D mesh models and do not need CTM.
	}
}


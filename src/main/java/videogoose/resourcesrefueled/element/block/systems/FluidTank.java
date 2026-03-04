package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
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
		ElementInformation vanillaFactory = ElementKeyMap.getInfo(ElementKeyMap.FACTORY_BASIC_ID);
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.description = "A pressurized storage tank for holding fluids.\nConnect to pipes and pumps to fill or drain.";
		blockInfo.price = (int) (vanillaFactory.price * 2);
		blockInfo.mass = vanillaFactory.mass * 1.5f;
		blockInfo.volume = vanillaFactory.volume * 0.5f;   // compact pressure vessel
		blockInfo.maxHitPointsFull = vanillaFactory.maxHitPointsFull * 2; // reinforced, but volatile
		blockInfo.shoppable = true;
		blockInfo.canActivate = false; // activated through the manager module
		blockInfo.systemBlock = true;
		blockInfo.type = vanillaFactory.type;
	}


	@Override
	public void postInitData() {

	}

	@Override
	public void initResources() {
		// TODO: custom tank textures (sealed metal cylinder aesthetic)
	}
}


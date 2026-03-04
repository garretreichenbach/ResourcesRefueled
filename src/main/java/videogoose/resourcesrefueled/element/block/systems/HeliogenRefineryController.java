package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.element.block.Block;

/**
 * The Heliogen Refinery Controller is the computer block of the Refinery multiblock.
 * Link Heliogen Refinery modules to this block to form a complete refinery system.
 * The controller is what activates the refinery; the modules provide throughput capacity.
 */
public class HeliogenRefineryController extends Block {

	public HeliogenRefineryController() {
		super("Heliogen Refinery Controller");
	}

	@Override
	public void initData() {
		ElementInformation vanillaFactory = ElementKeyMap.getInfo(ElementKeyMap.FACTORY_BASIC_ID);
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), "Heliogen Refinery Controller", vanillaFactory.getTextureIds());
		blockInfo.description = "The controller block for a Heliogen Refinery system. Place this on a station and link. Heliogen Refinery blocks to it. More linked refinery blocks increase processing throughput.";
		blockInfo.price = vanillaFactory.price;
		blockInfo.mass = vanillaFactory.mass;
		blockInfo.volume = vanillaFactory.volume;
		blockInfo.maxHitPointsFull = vanillaFactory.maxHitPointsFull;
		blockInfo.shoppable = true;
		blockInfo.canActivate = true;
		blockInfo.systemBlock = true;
		blockInfo.type = vanillaFactory.type;
		BlockConfig.setRestrictedBlock(blockInfo, true);
	}

	@Override
	public void postInitData() {
		// Wire computer module pair
		try {
			BlockConfig.registerComputerModulePair(blockInfo.id, ElementRegistry.HELIOGEN_REFINERY.getId());
		} catch(Exception e) {
			ResourcesRefueled.getInstance().logException("[ResourcesRefueled] Failed to register Heliogen Refinery computer/module pair", e);
		}
	}

	@Override
	public void initResources() {
		// TODO: custom textures
	}
}

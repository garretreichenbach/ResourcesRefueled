package videogoose.resourcesreorganized.element.block.systems;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.ithirahad.resourcesresourced.RRSElementInfoManager;
import org.schema.game.common.data.element.FactoryResource;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.element.block.Block;

/**
 * The Heliogen Condenser is a factory-type station block that converts Anbaric Vapor
 * and Parsyne Plasma into raw Heliogen Plasma. Its yield is boosted by proximity to the
 * local star (handled in SolarCondenserTickListener via StellarFuelSupplier).
 *
 * Must be placed on a station; station-only restriction is enforced by setRestrictedBlock.
 */
public class HeliogenCondenser extends Block {

	public HeliogenCondenser() {
		super("Heliogen Condenser");
	}

	@Override
	public void initData() {
		// Reuse vanilla factory textures as a placeholder until custom art is added.
		blockInfo = BlockConfig.registerFactory(ResourcesReorganized.getInstance(), name, new short[6]);
		blockInfo.type = Blocks.BASIC_FACTORY.getInfo().type;
		blockInfo.description = "A station-mounted condenser that uses stellar radiation to catalyze a reaction between Anbaric Vapor and Parsyne Plasma, producing raw Heliogen Plasma.\nYield increases the closer the station is to its local star.\nConnect Factory Enhancers to boost throughput.";
		blockInfo.price = Blocks.BASIC_FACTORY.getInfo().price;
		blockInfo.mass = Blocks.BASIC_FACTORY.getInfo().mass;
		blockInfo.volume = Blocks.BASIC_FACTORY.getInfo().volume;
		blockInfo.maxHitPointsFull = Blocks.BASIC_FACTORY.getInfo().maxHitPointsFull;
		blockInfo.shoppable = true;
		blockInfo.canActivate = true;
		BlockConfig.setRestrictedBlock(blockInfo, true);
	}

	@Override
	public void postInitData() {
		try {
			short anbaricId = RRSElementInfoManager.elementEntries.get("Anbaric Vapor").id;
			short parsyneId = RRSElementInfoManager.elementEntries.get("Parsyne Plasma").id;

			BlockConfig.addRecipe(ElementRegistry.HELIOGEN_PLASMA.getInfo(), BlockConfig.customFactories.get(blockInfo.id), 20, // bake time in ticks
					new FactoryResource(1, anbaricId), new FactoryResource(1, parsyneId));
		} catch(Exception e) {
			ResourcesReorganized.getInstance().logException("[ResourcesReorganized] Failed to register Heliogen Condenser recipe", e);
		}
	}

	@Override
	public void initResources() {
		// TODO: load custom block textures once art assets are available.
	}
}
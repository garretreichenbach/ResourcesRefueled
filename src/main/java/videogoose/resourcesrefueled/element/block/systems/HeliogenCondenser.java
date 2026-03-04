package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import org.ithirahad.resourcesresourced.RRSElementInfoManager;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.element.FactoryResource;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.element.block.Block;

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
		ElementInformation vanillaFactory = ElementKeyMap.getInfo(ElementKeyMap.FACTORY_BASIC_ID);
		// Reuse vanilla factory textures as a placeholder until custom art is added.
		blockInfo = BlockConfig.newFactory(ResourcesRefueled.getInstance(), "Heliogen Condenser", vanillaFactory.getTextureIds());
		blockInfo.description = "A station-mounted condenser that uses stellar radiation to catalyze a reaction between Anbaric Vapor and Parsyne Plasma, producing raw Heliogen Plasma.\nYield increases the closer the station is to its local star.\nConnect Factory Enhancers to boost throughput.";
		blockInfo.price = vanillaFactory.price;
		blockInfo.mass = vanillaFactory.mass;
		blockInfo.volume = vanillaFactory.volume;
		blockInfo.maxHitPointsFull = vanillaFactory.maxHitPointsFull;
		blockInfo.shoppable = true;
		blockInfo.canActivate = true;
		blockInfo.type = vanillaFactory.type;
		BlockConfig.setRestrictedBlock(blockInfo, true); // Station/planet only
	}

	@Override
	public void postInitData() {
		// Recipe: 1 Anbaric Vapor + 1 Parsyne Plasma -> 1 Heliogen Plasma (raw output).
		// The SolarCondenserTickListener multiplies the actual yield by star proximity at runtime.
		// RRS element IDs are safely resolved here because postInitData() runs after all elements register.
		try {
			short anbaricId = RRSElementInfoManager.elementEntries.get("Anbaric Vapor").id;
			short parsyneId = RRSElementInfoManager.elementEntries.get("Parsyne Plasma").id;
			short heliogenId = ElementRegistry.HELIOGEN_PLASMA.getId();
			BlockConfig.addRecipe(ElementKeyMap.getInfo(heliogenId), BlockConfig.customFactories.get(blockInfo.id), 20, // bake time in ticks
					new FactoryResource(1, anbaricId), new FactoryResource(1, parsyneId));
		} catch(Exception e) {
			ResourcesRefueled.getInstance().logException("[ResourcesRefueled] Failed to register Heliogen Condenser recipe", e);
		}
	}

	@Override
	public void initResources() {
		// TODO: load custom block textures once art assets are available.
	}
}
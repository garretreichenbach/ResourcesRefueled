package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import api.utils.element.CustomModRefinery;
import org.schema.game.common.data.element.*;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.element.block.Block;

/**
 * The Heliogen Refinery is a refinery-type block that converts raw Heliogen Plasma into
 * filled Heliogen Canisters. It is also responsible for filling empty canisters.
 * It must be placed on a station (setRestrictedBlock).
 */
public class HeliogenRefinery extends Block {

	public static final FixedRecipe condensingRecipe = new FixedRecipe();

	static {
		condensingRecipe.name = "Heliogen Condensing";
	}

	/** Ticks to process one unit. Matches RRS capsule bake time. */
	private static final int BAKE_TIME = 100;
	/** How many filled canisters per unit of raw plasma. */
	public static final int PLASMA_PER_CANISTER = 5;

	public HeliogenRefinery() {
		super("Heliogen Refinery");
	}

	@Override
	public void initData() {
		ElementInformation vanillaFactory = ElementKeyMap.getInfo(ElementKeyMap.FACTORY_BASIC_ID);
		blockInfo = BlockConfig.registerRefinery(ResourcesRefueled.getInstance(), name, vanillaFactory.getTextureIds(), new CustomModRefinery(condensingRecipe, "Heliogen Refinery", "Condensing Heliogen Plasma...", BAKE_TIME));
		blockInfo.description = "A refinery that compresses raw Heliogen Plasma into portable Heliogen Canisters. " + "Produces " + PLASMA_PER_CANISTER + " filled canisters per unit of plasma input. " + "Also accepts empty canisters as an additional input to fill them directly.";
		blockInfo.price = vanillaFactory.price;
		blockInfo.mass = vanillaFactory.mass;
		blockInfo.volume = vanillaFactory.volume;
		blockInfo.maxHitPointsFull = vanillaFactory.maxHitPointsFull;
		blockInfo.shoppable = true;
		blockInfo.canActivate = true;
		blockInfo.type = vanillaFactory.type;
	}

	@Override
	public void postInitData() {
		try {
			short plasmaId = ElementRegistry.HELIOGEN_PLASMA.getId();
			short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
			short emptyId = ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId();

			FixedRecipeProduct product = new FixedRecipeProduct();
			product.input = new FactoryResource[]{new FactoryResource(PLASMA_PER_CANISTER, plasmaId), new FactoryResource(1, emptyId)};
			product.output = new FactoryResource[]{new FactoryResource(PLASMA_PER_CANISTER, filledId)};
			condensingRecipe.recipeProducts = new FixedRecipeProduct[]{product};
		} catch(Exception e) {
			ResourcesRefueled.getInstance().logException("[ResourcesRefueled] Failed to register Heliogen Refinery recipe", e);
		}
	}

	@Override
	public void initResources() {
		// TODO: custom textures
	}
}

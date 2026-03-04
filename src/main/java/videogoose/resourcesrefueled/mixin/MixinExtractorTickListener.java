package videogoose.resourcesrefueled.mixin;

import it.unimi.dsi.fastutil.ints.IntCollection;
import org.ithirahad.resourcesresourced.listeners.ExtractorTickFastListener;
import org.schema.game.common.controller.elements.factory.FactoryProducerInterface;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.FactoryResource;
import org.schema.game.common.data.element.meta.RecipeInterface;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.server.data.GameServerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.utils.InventoryUtils;

import static org.ithirahad.resourcesresourced.listeners.BlockRemovalLogic.isExtractor;

/**
 * Injects Heliogen fuel as an output booster for RRS extractors.
 * <p>
 * Heliogen does NOT gate extraction — extractors always run at full base efficiency.
 * Instead, each completed extraction cycle checks for filled canisters and, if present,
 * consumes some and adds bonus resources proportional to the fueled_extraction_bonus
 * config value. Empty canisters are returned in the same inventory update batch.
 * <p>
 * This makes Heliogen a reward for players who invest in the fuel supply chain,
 * rather than a penalty for those who haven't set it up yet.
 * <p>
 * Fuel cost scales with cycle output quantity, so enhanced/stronger extractors
 * consume more but also receive proportionally more bonus resources.
 */
@Mixin(value = ExtractorTickFastListener.class, remap = false)
public class MixinExtractorTickListener {

	@Inject(method = "onProduceItem", at = @At("HEAD"), remap = false)
	private void heliogenOutputBoost(RecipeInterface recipeInterface, Inventory inventory, FactoryProducerInterface producer, FactoryResource product, int quantity, IntCollection changeSet, CallbackInfo ci) {
		if(GameServerState.instance == null) return;

		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || !isExtractor(block.getType())) return;

		short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		short emptyId = ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId();

		int available = inventory.getOverallQuantity(filledId);
		if(available <= 0) return; // no fuel present — base output unchanged, no penalty

		// Consume one canister per FUEL_COST_PER_STRENGTH_UNIT units of base output.
		// Scale with quantity so stronger/enhanced extractors cost and gain proportionally more.
		int canistersToConsume = Math.min(available, Math.max(1, (int) Math.ceil(quantity * ConfigManager.getFuelCostPerStrengthUnit())));

		int removed = InventoryUtils.removeItems(inventory, filledId, canistersToConsume);
		if(removed <= 0) return;

		// Return empty canisters
		int returned = inventory.incExistingOrNextFreeSlotWithoutException(emptyId, removed);
		changeSet.add(returned);

		// Add bonus output: fueled_extraction_bonus fraction of base quantity, per canister consumed.
		// e.g. bonus=0.5, quantity=10, consumed=2 → +10 bonus resources
		int bonus = (int) Math.floor(quantity * ConfigManager.getFueledExtractionBonus() * removed);
		if(bonus <= 0) return;

		int bonusChange = inventory.incExistingOrNextFreeSlotWithoutException(product.type, bonus);
		changeSet.add(bonusChange);
	}
}

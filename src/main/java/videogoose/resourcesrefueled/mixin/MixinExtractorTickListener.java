package videogoose.resourcesrefueled.mixin;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ithirahad.resourcesresourced.listeners.ExtractorTickFastListener;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.factory.FactoryCollectionManager;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.fuel.FuelTickState;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.utils.InventoryUtils;

import static org.ithirahad.resourcesresourced.listeners.BlockRemovalLogic.isExtractor;

/**
 * Injects Heliogen fuel consumption into RRS's extractor tick.
 * <p>
 * Two injection points:
 * <p>
 * 1. onPreManufacture (HEAD) — checks whether any fuel is present and writes to
 *    FuelTickState each tick. No consumption happens here. This feeds
 *    HarvesterFuelEfficiencyListener, which scales extraction strength down when
 *    unfueled so output is reduced but not stopped entirely.
 * <p>
 * 2. onProduceItem (HEAD) — fires once per completed extraction cycle, when
 *    resources are actually deposited into the inventory. Consumes canisters here
 *    because:
 *      - Players physically transport canisters from a condenser station to the
 *        extractor site — this is the manual supply chain the mod is built around.
 *      - Per-tick consumption would drain a full stack in seconds at factory speed.
 *      - Per-cycle consumption is proportional to output quantity, so a stronger
 *        or enhancer-boosted extractor costs proportionally more fuel.
 * <p>
 * Void systems are not short-circuited here — RRS handles void resource availability.
 * The Heliogen Condenser's void block is handled in SolarCondenserTickListener.
 * <p>
 * Shared state lives in FuelTickState because Mixin disallows non-private statics.
 */
@Mixin(value = ExtractorTickFastListener.class, remap = false)
public class MixinExtractorTickListener {

	// -------------------------------------------------------------------------
	// 1. Pre-manufacture: check availability, write unfueled flag
	// -------------------------------------------------------------------------

	@Inject(method = "onPreManufacture", at = @At("HEAD"), remap = false)
	private void heliogenFuelCheck(FactoryCollectionManager factoryCollectionManager, Inventory inventory, LongOpenHashSet[] longOpenHashSets, CallbackInfoReturnable<Boolean> cir) {
		if(GameServerState.instance == null) return;

		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || !isExtractor(block.getType())) return;

		ManagedUsableSegmentController<?> entity = (ManagedUsableSegmentController<?>) factoryCollectionManager.getSegmentController();

		boolean hasFuel = inventory.getOverallQuantity(ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()) > 0;
		FuelTickState.unfueledThisTick.put(entity.getUniqueIdentifier(), !hasFuel);
	}

	// -------------------------------------------------------------------------
	// 2. Post-produce: consume fuel proportional to cycle output
	// -------------------------------------------------------------------------

	@Inject(method = "onProduceItem", at = @At("HEAD"), remap = false)
	private void heliogenFuelConsume(RecipeInterface recipeInterface, Inventory inventory, FactoryProducerInterface producer, FactoryResource product, int quantity, IntCollection changeSet, CallbackInfo ci) {
		if(GameServerState.instance == null) return;

		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || !isExtractor(block.getType())) return;

		short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		short emptyId = ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId();

		// Cost scales with output: more resources extracted = more fuel burned.
		// Minimum 1 canister per cycle so there is always a tangible supply need.
		int canistersNeeded = Math.max(1, (int) Math.ceil(quantity * ConfigManager.getFuelCostPerStrengthUnit()));

		// Consume only what's available — the efficiency penalty from FuelTickState
		// already reduced the output if we were underfueled this tick.
		int toConsume = Math.min(canistersNeeded, inventory.getOverallQuantity(filledId));
		if(toConsume <= 0) return;

		int removed = InventoryUtils.removeItems(inventory, filledId, toConsume);
		if(removed > 0) {
			// Return empties in the same inventory update batch as the produced resources.
			int returned = inventory.incExistingOrNextFreeSlotWithoutException(emptyId, removed);
			changeSet.add(returned);
		}
	}
}

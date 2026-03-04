package videogoose.resourcesrefueled.mixin;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ithirahad.resourcesresourced.RRSConfiguration;
import org.ithirahad.resourcesresourced.listeners.ExtractorTickFastListener;
import org.ithirahad.resourcesresourced.universe.starsystem.SystemSheet;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.factory.FactoryCollectionManager;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.server.data.GameServerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.fuel.FuelTickState;
import videogoose.resourcesrefueled.fuel.StellarFuelManager;
import videogoose.resourcesrefueled.fuel.StellarFuelSupplier;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.utils.InventoryUtils;

import static org.ithirahad.resourcesresourced.listeners.BlockRemovalLogic.isExtractor;

/**
 * Injects Heliogen fuel consumption into RRS's extractor tick.
 * <p>
 * Flow:
 *  1. Detect how many Heliogen Canister (Filled) are in the factory inventory.
 *  2. Calculate fuel cost = harvesterStrength * FUEL_COST_PER_STRENGTH_UNIT.
 *  3. If sufficient canisters: consume them and return empties — the "recipe" side of the fuel loop.
 *  4. If insufficient: tag the entity as unfueled in FuelTickState. RRS's
 *     HarvesterStrengthUpdateEvent fires after this returns, where
 *     HarvesterFuelEfficiencyListener scales down effective strength.
 * <p>
 * Shared state lives in FuelTickState (not here) because Mixin disallows
 * non-private static members, and private members can't be read by other classes.
 */
@Mixin(value = ExtractorTickFastListener.class, remap = false)
public class MixinExtractorTickListener {

	@Inject(method = "onPreManufacture", at = @At("HEAD"), remap = false)
	private void heliogenFuelCheck(FactoryCollectionManager factoryCollectionManager, Inventory inventory, LongOpenHashSet[] longOpenHashSets, CallbackInfoReturnable<Boolean> cir) {
		if(GameServerState.instance == null) return;

		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || !isExtractor(block.getType())) return;

		ManagedUsableSegmentController<?> entity = (ManagedUsableSegmentController<?>) factoryCollectionManager.getSegmentController();
		String uid = entity.getUniqueIdentifier();

		short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		short emptyId = ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId();

		int filledCount = inventory.getOverallQuantity(filledId);

		float enhancedStrength = factoryCollectionManager.getFactoryCapability() * RRSConfiguration.EXTRACTION_RATE_PER_SECOND_PER_BLOCK;
		float costPerTick = enhancedStrength * (float) ConfigManager.getFuelCostPerStrengthUnit();
		int canistersNeeded = Math.max(1, (int) Math.ceil(costPerTick));

		Vector3i sector = entity.getSector(new Vector3i());
		float proximity = SystemSheet.getTemperature(sector);
		StellarFuelSupplier supplier = StellarFuelManager.getSupplierForSector(sector);
		boolean inVoid = (supplier == null || proximity <= 0.0f);

		if(inVoid) {
			FuelTickState.unfueledThisTick.put(uid, true);
			return;
		}

		if(filledCount >= canistersNeeded) {
			int removed = InventoryUtils.removeItems(inventory, filledId, canistersNeeded);
			if(removed > 0) {
				int returned = inventory.incExistingOrNextFreeSlotWithoutException(emptyId, removed);
				inventory.sendInventoryModification(returned);
			}
			FuelTickState.unfueledThisTick.put(uid, false);
		} else if(filledCount > 0) {
			int removed = InventoryUtils.removeItems(inventory, filledId, filledCount);
			if(removed > 0) {
				int returned = inventory.incExistingOrNextFreeSlotWithoutException(emptyId, removed);
				inventory.sendInventoryModification(returned);
			}
			FuelTickState.unfueledThisTick.put(uid, true);
		} else {
			FuelTickState.unfueledThisTick.put(uid, true);
		}
	}
}

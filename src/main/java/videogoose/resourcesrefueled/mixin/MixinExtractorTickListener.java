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
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;
import videogoose.resourcesrefueled.utils.InventoryUtils;

import static org.ithirahad.resourcesresourced.listeners.BlockRemovalLogic.isExtractor;

/**
 * Two injection points into RRS's ExtractorTickFastListener:
 * <p>
 * onPreManufacture — resolves total available Heliogen from ALL sources on the entity:
 *   1. FluidTankSystemModule (bulk tank supply via pipes)
 *   2. Heliogen Canisters in the factory inventory (portable fallback)
 * Writes the combined total to FuelTickState.availableFuelUnits.
 * The inventory is guaranteed live here; the tank module may be null if the
 * system isn't loaded, in which case only canisters are counted.
 * <p>
 * onProduceItem — physically drains fuel proportional to cycle output.
 * Drains tanks first (bulk, no item churn), then canisters for any remainder.
 * Skips units already spent by HarvesterEnhancerOverrideListener (spentFuelUnits).
 * The bonus output is applied here proportional to total fuel consumed this cycle.
 */
@Mixin(value = ExtractorTickFastListener.class, remap = false)
public class MixinExtractorTickListener {

	// -------------------------------------------------------------------------
	// onPreManufacture — resolve and cache total available fuel
	// -------------------------------------------------------------------------

	@Inject(method = "onPreManufacture", at = @At("HEAD"), remap = false)
	private void resolveAvailableFuel(FactoryCollectionManager factoryCollectionManager, Inventory inventory, LongOpenHashSet[] longOpenHashSets, CallbackInfoReturnable<Boolean> cir) {
		if(GameServerState.instance == null) return;
		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || !isExtractor(block.getType())) return;

		ManagedUsableSegmentController<?> entity = (ManagedUsableSegmentController<?>) factoryCollectionManager.getSegmentController();
		String uid = entity.getUniqueIdentifier();

		double totalFuel = 0;

		// Source 1: FluidTankSystemModule (may be null if system unloaded)
		FluidTankSystemModule tankModule = (FluidTankSystemModule) entity.getManagerContainer().getModMCModule(ElementRegistry.HELIOGEN_TANK.getId());
		if(tankModule != null && tankModule.getFluidId() == ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()) {
			totalFuel += tankModule.getCurrentFluidLevel();
		}

		// Source 2: Canisters in factory inventory (always available here)
		int canisters = inventory.getOverallQuantity(ElementRegistry.HELIOGEN_CANISTER_FILLED.getId());
		totalFuel += canisters * ConfigManager.getFuelPerCanister();

		FuelTickState.availableFuelUnits.put(uid, totalFuel);
		FuelTickState.spentFuelUnits.put(uid, 0.0);
	}

	// -------------------------------------------------------------------------
	// onProduceItem — drain fuel, return empties, apply bonus output
	// -------------------------------------------------------------------------

	@Inject(method = "onProduceItem", at = @At("HEAD"), remap = false)
	private void heliogenOutputBoost(RecipeInterface recipeInterface, Inventory inventory, FactoryProducerInterface producer, FactoryResource product, int quantity, IntCollection changeSet, CallbackInfo ci) {
		if(GameServerState.instance == null) return;
		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || !isExtractor(block.getType())) return;

		ManagedUsableSegmentController<?> entity = (ManagedUsableSegmentController<?>) ((FactoryCollectionManager) producer).getSegmentController();
		String uid = entity.getUniqueIdentifier();

		double available = FuelTickState.availableFuelUnits.getOrDefault(uid, 0.0);
		if(available <= 0) return;

		// Cost for this cycle
		double costUnits = Math.max(ConfigManager.getFuelPerCanister(), quantity * ConfigManager.getFuelCostPerStrengthUnit() * ConfigManager.getFuelPerCanister());

		// Deduct what the strength listener already spent this tick
		double alreadySpent = FuelTickState.spentFuelUnits.getOrDefault(uid, 0.0);
		double fuelSpent = Math.max(0, Math.min(costUnits - alreadySpent, available - alreadySpent));
		if(fuelSpent <= 0) return;

		// Drain tanks first, then canisters for any remainder
		double remaining = fuelSpent;
		FluidTankSystemModule tankModule = (FluidTankSystemModule) entity.getManagerContainer().getModMCModule(ElementRegistry.HELIOGEN_TANK.getId());
		if(tankModule != null && tankModule.getFluidId() == ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()) {
			double fromTank = Math.min(tankModule.getCurrentFluidLevel(), remaining);
			if(fromTank > 0) {
				tankModule.setCurrentFluidLevel(tankModule.getCurrentFluidLevel() - fromTank);
				remaining -= fromTank;
			}
		}

		// Drain remaining from canisters
		if(remaining > 0) {
			int canistersToConsume = (int) Math.ceil(remaining / ConfigManager.getFuelPerCanister());
			int removed = InventoryUtils.removeItems(inventory, ElementRegistry.HELIOGEN_CANISTER_FILLED.getId(), canistersToConsume);
			if(removed > 0) {
				int returned = inventory.incExistingOrNextFreeSlotWithoutException(ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId(), removed);
				changeSet.add(returned);
			}
		}

		// Bonus output proportional to fuel actually spent
		int bonus = (int) Math.floor(quantity * ConfigManager.getFueledExtractionBonus() * (fuelSpent / costUnits));
		if(bonus > 0) {
			changeSet.add(inventory.incExistingOrNextFreeSlotWithoutException(product.type, bonus));
		}
	}
}
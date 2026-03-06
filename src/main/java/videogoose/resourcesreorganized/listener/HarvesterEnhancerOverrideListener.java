package videogoose.resourcesreorganized.listener;

import api.listener.Listener;
import org.ithirahad.resourcesresourced.events.HarvesterStrengthUpdateEvent;
import videogoose.resourcesreorganized.fuel.FuelTickState;
import videogoose.resourcesreorganized.manager.ConfigManager;

/**
 * Overrides RRS's factory-enhancer strength bonus on extractors, replacing it
 * with a Heliogen-fuel-based bonus instead.
 * <p>
 * RRS initialises harvester power as:
 *   power = factoryCapability (enhancer count) × configuredBaseRate
 * <p>
 * We reset power to the bare base rate (no enhancer scaling), then scale it
 * back up according to how much fuel is available this tick from FuelTickState.
 * This avoids any inventory access — the fuel total was already resolved safely
 * in MixinExtractorTickListener.onPreManufacture where the inventory is live.
 * <p>
 * Power formula (when fueled):
 *   fuelFraction = min(availableFuelUnits / maxFuelForFullBoost, 1.0)
 *   power = baseRate × (1 + fuelFraction × (enhancerCeiling - 1))
 * <p>
 * Where maxFuelForFullBoost = enhancerCount × fuelPerCanister.
 * This means a fully-fueled extractor matches what vanilla enhancers alone would
 * give — fuel *replaces* enhancers rather than stacking on top of them.
 * A partially-fueled extractor gets proportional boost between base and ceiling.
 * <p>
 * The fuel units "reserved" here are tracked in FuelTickState.spentFuelUnits so
 * onProduceItem doesn't double-consume them.
 */
public class HarvesterEnhancerOverrideListener extends Listener<HarvesterStrengthUpdateEvent> {

	@Override
	public void onEvent(HarvesterStrengthUpdateEvent event) {
		String uid = event.getExtractingEntity().getUniqueIdentifier();

		float baseRate     = event.getConfigStrengthMultiplier();
		float enhanceScale = event.getFactoryEnhancedScale(); // >= 1.0, reflects installed enhancers

		// How many fuel units are needed to fully replace the enhancer bonus?
		double maxFuelForFullBoost = enhanceScale * ConfigManager.getCapacityPerCanister();

		double available = FuelTickState.availableFuelUnits.getOrDefault(uid, 0.0);

		if(available <= 0) {
			// No fuel — strip enhancer bonus entirely, run at bare base rate.
			event.setPower(baseRate);
			FuelTickState.spentFuelUnits.put(uid, 0.0);
			return;
		}

		// Fraction of the enhancer ceiling achievable with available fuel
		double fuelFraction = Math.min(1.0, available / maxFuelForFullBoost);

		// Interpolate between base rate and vanilla enhancer ceiling
		float fueledPower = (float) (baseRate * (1.0 + fuelFraction * (enhanceScale - 1.0)));
		event.setPower(fueledPower);

		// Record how much fuel this strength decision "costs" so onProduceItem
		// doesn't spend it again. Cost scales with how much of the boost we used.
		double fuelSpent = fuelFraction * maxFuelForFullBoost;
		FuelTickState.spentFuelUnits.put(uid, fuelSpent);
	}
}


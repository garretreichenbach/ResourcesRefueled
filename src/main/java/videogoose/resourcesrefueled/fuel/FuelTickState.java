package videogoose.resourcesrefueled.fuel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state between MixinExtractorTickListener (which writes) and
 * HarvesterEnhancerOverrideListener / onProduceItem (which read).
 * <p>
 * Kept outside the mixin class because Mixin does not allow non-private
 * static members, so cross-class sharing requires an external holder.
 * <p>
 * Fuel can come from two sources, resolved once per tick in onPreManufacture:
 *   1. Heliogen Canisters in the factory inventory (portable supply)
 *   2. A connected FluidTankSystemModule on the same entity (bulk tank supply)
 * Both are expressed in the same unit (fluid units) so consumers don't need to
 * care about the source.
 */
public final class FuelTickState {

	private FuelTickState() {
	}

	/**
	 * Key: entity UID
	 * Value: total available Heliogen fuel units this tick, combining tanks and canisters.
	 *        0 = no fuel available.
	 *        Written by MixinExtractorTickListener.onPreManufacture (inventory is live there).
	 *        Read by HarvesterEnhancerOverrideListener and onProduceItem.
	 */
	public static final ConcurrentHashMap<String, Double> availableFuelUnits = new ConcurrentHashMap<>();

	/**
	 * Key: entity UID
	 * Value: fuel units already spent this tick by the strength override listener,
	 *        so onProduceItem doesn't double-consume.
	 *        Written by HarvesterEnhancerOverrideListener, read and cleared by onProduceItem.
	 */
	public static final ConcurrentHashMap<String, Double> spentFuelUnits = new ConcurrentHashMap<>();
}
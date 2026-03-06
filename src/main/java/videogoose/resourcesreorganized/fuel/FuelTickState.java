package videogoose.resourcesreorganized.fuel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state between {@link videogoose.resourcesreorganized.listener.ExtractorFuelListener}
 * (which writes) and {@link videogoose.resourcesreorganized.listener.HarvesterEnhancerOverrideListener}
 * / {@code onProduceItem} (which read).
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
	 *        Written by ExtractorFuelListener.onPreManufacture (inventory is live there).
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
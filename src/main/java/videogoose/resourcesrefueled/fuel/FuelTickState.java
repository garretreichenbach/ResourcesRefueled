package videogoose.resourcesrefueled.fuel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state between MixinExtractorTickListener (which writes) and
 * HarvesterFuelEfficiencyListener (which reads).
 * <p>
 * Kept outside the mixin class because Mixin does not allow non-private
 * static members, so cross-class sharing requires an external holder.
 */
public final class FuelTickState {

	private FuelTickState() {}

	/**
	 * Per-tick unfueled entity tracking.
	 * Key: entity UID → true if unfueled this tick, false if fueled.
	 * Written by MixinExtractorTickListener at HEAD of each extractor tick.
	 * Read by HarvesterFuelEfficiencyListener to scale extraction power.
	 */
	public static final ConcurrentHashMap<String, Boolean> unfueledThisTick = new ConcurrentHashMap<>();
}


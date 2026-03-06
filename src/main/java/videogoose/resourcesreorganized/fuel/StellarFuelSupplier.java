package videogoose.resourcesreorganized.fuel;

import org.ithirahad.resourcesresourced.universe.starsystem.SystemClass;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.manager.ConfigManager;

import java.io.Serializable;

/**
 * Represents a star's passive Heliogen supply well. Parallel to RRS's PassiveResourceSupplier.
 * <p>
 * Every star system gets one StellarFuelSupplier keyed by system Vector3i.
 * The regen rate is derived from the system's SystemClass (star type) and is static per
 * system — proximity boosts are applied on demand at claim-time using SystemSheet.getTemperature().
 * <p>
 * Unloaded accumulation works identically to RRS: lastPassiveUpdate tracks the last
 * server-side tick, and elapsed time is applied on next access.
 */
public class StellarFuelSupplier implements Serializable {

	/** Base regen rate in units/second, before proximity multiplier. */
	public float baseRegenRate;

	/** Accumulated fuel units ready to be claimed. */
	public double pool;

	/** Max pool size = baseRegenRate * POOL_SIZE_SECONDS seconds of regen. */
	public static final float POOL_SIZE_SECONDS = 600.0f; // 10 minutes of regen

	/**
	 * Temperature threshold above which ships take star damage.
	 * 1.0 = star center, 0.0 = system edge.
	 * Configurable via ConfigManager but stored here as the working value.
	 */
	public float damageThreshold = 0.85f;

	private transient long lastPassiveUpdate = -1;

	/** Construct a new supplier from a known SystemClass. */
	public StellarFuelSupplier(SystemClass systemClass) {
		baseRegenRate = getBaseRegenForClass(systemClass);
	}

	// -------------------------------------------------------------------------
	// Passive regen
	// -------------------------------------------------------------------------

	/**
	 * Update the passive pool based on elapsed real time.
	 * Called on demand (lazy) — safe to call even when sector is unloaded.
	 */
	public synchronized void updatePassivePool(long nowMs) {
		if (lastPassiveUpdate < 0) {
			lastPassiveUpdate = nowMs;
			return;
		}
		double elapsedSeconds = (nowMs - lastPassiveUpdate) / 1000.0;
		lastPassiveUpdate = nowMs;

		double maxPool = baseRegenRate * POOL_SIZE_SECONDS;
		pool = Math.min(maxPool, pool + baseRegenRate * elapsedSeconds);
	}

	/**
	 * Claim up to {@code amount} units from the pool, applying a proximity multiplier.
	 * The proximity multiplier is SystemSheet.getTemperature(entitySector), clamped to
	 * a safe collection band (below damageThreshold).
	 *
	 * @param requestedAmount  How many units the extractor wants.
	 * @param proximityFactor  SystemSheet.getTemperature(entitySector), range [0,1].
	 * @return Actual units granted (may be less than requested if pool is low or proximity is zero).
	 */
	public synchronized double claimFuel(double requestedAmount, float proximityFactor) {
		// Debug mode: allow claiming for testing anywhere without affecting the pool.
		try {
			if(ConfigManager.isDebugMode()) {
				ResourcesReorganized.getInstance().logInfo("[ResourcesRefueled] Debug mode: granting " + requestedAmount + " Heliogen units regardless of proximity.");
				return requestedAmount;
			}
		} catch(Exception ignored) { }

		updatePassivePool(System.currentTimeMillis());

		if (proximityFactor <= 0.0f || pool <= 0) return 0;

		double available = pool * proximityFactor; // proximity scales effective availability
		double granted = Math.min(requestedAmount, available);
		pool -= granted;
		if (pool < 0) pool = 0;
		return granted;
	}

	// -------------------------------------------------------------------------
	// Star class yield table
	// -------------------------------------------------------------------------

	/**
	 * Maps SystemClass to a base Heliogen regen rate (units/second).
	 * Stars that RRS treats as energy-rich give more; void systems give nothing.
	 */
	public static float getBaseRegenForClass(SystemClass cls) {
		if (cls == null) return 0.5f;
		switch (cls) {
			// Large, hot, or exotic stars — high yield, high risk
			case RR_ANBARIC:     return 2.5f;
			case RR_ENERGETIC:   return 2.0f;
			case RR_MISTY:       return 1.8f;
			case RR_FERRON:      return 1.5f; // hot, close-to-star asteroids imply an energetic star
			case RR_CORE:        return 1.5f;
			case BA_RELIC:       return 1.8f; // ancient engineered star — unusual output
			// Average systems
			case NORMAL:
			case RR_METAL_RICH:
			case RR_CRYSTAL_RICH:
			case RR_FRIGID:      return 1.0f;
			case RR_PARAMETRIC:  return 1.2f;
			case RR_EXTRADIMENSIONAL: return 3.0f; // extreme but dangerous
			// Pathfinder classes
			case PF_PYROCLASTIC:  return 2.0f;
			case PF_COMETARY:     return 0.7f; // cold/dim star
			case PF_EXOTIC:       return 1.5f;
			case PF_FLUX:         return 1.8f;
			case PF_DUSTY:
			case PF_RUDDY:        return 1.0f;
			case PF_TEMPERATE:    return 1.1f;
			case PF_FRIGID:       return 0.6f;
			// Void systems — no star, no Heliogen
			case NORMAL_VOID:
			case RR_MISTY_VOID:
			case RR_ENERGETIC_VOID:
			case RR_ANBARIC_VOID:
			case RR_METAL_VOID:
			case RR_FERRON_VOID:
			case RR_FRIGID_VOID:
			case RR_CRYSTAL_VOID:
			case RR_EXTRADIMENSIONAL_VOID:
			case WARP_VOID:
			case UNKNOWN:        return 0f;
			default:             return 0.8f;
		}
	}

	// -------------------------------------------------------------------------
	// Lifecycle (mirrors RRS PassiveResourceSupplier)
	// -------------------------------------------------------------------------

	public void beforeSerialize() {
		updatePassivePool(System.currentTimeMillis());
	}

	public void afterDeserialize() {
		lastPassiveUpdate = System.currentTimeMillis();
	}

	public boolean isEmpty() {
		return pool <= 0;
	}

	public float getBaseRegenRate() {
		return baseRegenRate;
	}

	@Override
	public String toString() {
		return "[StellarFuelSupplier | regen=" + baseRegenRate + "/s | pool=" + String.format("%.2f", pool) + "]";
	}
}


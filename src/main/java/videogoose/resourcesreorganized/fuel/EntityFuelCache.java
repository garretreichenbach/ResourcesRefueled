package videogoose.resourcesreorganized.fuel;

import java.io.Serializable;

/**
 * Virtualised per-entity Heliogen fuel state.
 * <p>
 * Stores a snapshot of the entity's tank fluid level and filled-canister count
 * so that fuel reads and drains can proceed even when the entity is not currently
 * loaded. The cache is refreshed from the live entity whenever it IS loaded (via
 * {@link EntityFuelManager#syncFromLive}) and written back on entity unload /
 * world-save (via {@link EntityFuelManager#writeBackToLive}).
 * <p>
 * All fuel in this cache is expressed in the same unit: fluid units.
 * Canisters are converted using {@link videogoose.resourcesreorganized.manager.ConfigManager#getFuelPerCanister()}.
 */
public class EntityFuelCache implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Fluid units currently stored in the entity's connected Heliogen tank. */
	public double tankFluidLevel;

	/**
	 * Snapshot of tankFluidLevel at the time of the last {@code syncFromLive} call.
	 * Used by writeBackToLive to compute the delta (how many fluid units were consumed
	 * from the tank) so it can call {@code tankModule.drain(delta)} directly rather
	 * than redistributing a flat level across all networks.
	 */
	public double snapshotTankFluidLevel;

	/** Number of filled Heliogen Canisters in the entity's factory inventory. */
	public int canisterCount;

	/**
	 * Snapshot of canisterCount at the time of the last {@code syncFromLive} call.
	 * Used by writeBackToLive to compute the delta (how many canisters were consumed)
	 * so it can remove exactly that many filled canisters and return the right number
	 * of empties — without doing a full inventory scan.
	 */
	public int snapshotCanisterCount;

	/**
	 * Element ID of the fluid currently held in the tank (or 0 if none).
	 * Stored here so we can verify the tank still holds Heliogen on write-back.
	 */
	public short fluidId;

	/**
	 * True when the in-memory state differs from the last live snapshot.
	 * writeBackToLive is a no-op when dirty is false.
	 */
	public transient boolean dirty;

	// -------------------------------------------------------------------------
	// Derived helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the total available fuel in fluid units, combining tank and canisters.
	 *
	 * @param fuelPerCanister How many fluid units one canister represents (from ConfigManager).
	 */
	public double totalFuelUnits(double fuelPerCanister) {
		return tankFluidLevel + canisterCount * fuelPerCanister;
	}

	/**
	 * Drains {@code units} fluid units from this cache, deducting from the tank first
	 * and then converting any remainder to whole canister removals.
	 * <p>
	 * Returns the actual units drained (may be less than {@code units} if the cache is
	 * nearly empty). Sets {@code dirty = true} if any fuel was consumed.
	 *
	 * @param units          How many fluid units to drain.
	 * @param fuelPerCanister Fluid units per filled canister (from ConfigManager).
	 * @return Actual units drained.
	 */
	public double drain(double units, double fuelPerCanister) {
		if(units <= 0) return 0;

		double drained = 0;

		// Tank first
		double fromTank = Math.min(tankFluidLevel, units);
		if(fromTank > 0) {
			tankFluidLevel -= fromTank;
			drained += fromTank;
			dirty = true;
		}

		double remaining = units - drained;
		if(remaining > 0 && canisterCount > 0) {
			// How many whole canisters do we need?
			int canistersNeeded = (int) Math.ceil(remaining / fuelPerCanister);
			int canistersUsed = Math.min(canisterCount, canistersNeeded);
			double fromCanisters = canistersUsed * fuelPerCanister;
			canisterCount -= canistersUsed;
			drained += Math.min(fromCanisters, remaining); // don't report more than requested
			dirty = true;
		}

		return Math.min(drained, units);
	}

	/** Returns true when both tank and canister cache are empty. */
	public boolean isEmpty() {
		return tankFluidLevel <= 0 && canisterCount <= 0;
	}

	/** Called before serialisation — mark as clean so dirty state isn't persisted. */
	public void beforeSerialize() {
		dirty = false;
	}

	/** Called after deserialisation — ensure transient fields are initialised. */
	public void afterDeserialize() {
		dirty = false;
	}
}


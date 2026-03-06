package videogoose.resourcesreorganized.fuel;

import org.ithirahad.resourcesresourced.ResourcesReSourced;
import org.ithirahad.resourcesresourced.universe.starsystem.SystemClass;
import org.ithirahad.resourcesresourced.universe.starsystem.SystemSheet;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.world.StellarSystem;
import videogoose.resourcesreorganized.ResourcesReorganized;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for all star system Heliogen fuel wells.
 * Keyed by system Vector3i (NOT sector — one supplier per star system).
 *
 * Parallels RRS's PassiveResourceSourcesContainer.
 * Serialized to mod persistence via PersistentObjectUtil in StellarFuelManager.
 */
public class StellarFuelSourcesContainer implements Serializable {

	private final HashMap<Vector3i, StellarFuelSupplier> systemMap = new HashMap<>();

	// -------------------------------------------------------------------------
	// Access & lazy init
	// -------------------------------------------------------------------------

	/**
	 * Returns the StellarFuelSupplier for the given system, creating it lazily
	 * from the RRS SystemSheet if it does not yet exist.
	 * Returns null only if the system is a void (no star → no Heliogen).
	 */
	public StellarFuelSupplier getOrCreate(Vector3i systemPos) {
		StellarFuelSupplier existing = systemMap.get(systemPos);
		if(existing != null) return existing;

		// Determine system class from RRS SystemSheet
		SystemClass cls = getSystemClass(systemPos);
		float regenRate = StellarFuelSupplier.getBaseRegenForClass(cls);

		if(regenRate <= 0.0f) return null; // void system, no supplier

		StellarFuelSupplier supplier = new StellarFuelSupplier(cls);
		systemMap.put(new Vector3i(systemPos), supplier); // copy key — Vector3i is mutable
		ResourcesReorganized.getInstance().logInfo("[ResourcesRefueled] Created StellarFuelSupplier for system " + systemPos + " (" + cls + ", regen=" + regenRate + "/s)");
		return supplier;
	}

	/** Get a supplier only if it already exists (no lazy creation). */
	public StellarFuelSupplier get(Vector3i systemPos) {
		return systemMap.get(systemPos);
	}

	public boolean contains(Vector3i systemPos) {
		return systemMap.containsKey(systemPos);
	}

	public Map<Vector3i, StellarFuelSupplier> getMap() {
		return systemMap;
	}

	public int size() {
		return systemMap.size();
	}

	// -------------------------------------------------------------------------
	// Serialization lifecycle (mirrors RRS pattern)
	// -------------------------------------------------------------------------

	public void beforeSerialize() {
		for(StellarFuelSupplier supplier : systemMap.values()) {
			supplier.beforeSerialize();
		}
	}

	public void afterDeserialize() {
		for(StellarFuelSupplier supplier : systemMap.values()) {
			supplier.afterDeserialize();
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Resolves the SystemClass for a system position via RRS's SystemSheet.
	 * Falls back to NORMAL if the sheet is not yet available.
	 */
	private static SystemClass getSystemClass(Vector3i systemPos) {
		try {
			SystemSheet sheet = ResourcesReSourced.getSystemSheet(systemPos);
			if(sheet != null) {
				return sheet.systemClass;
			}
		} catch(Exception e) {
			ResourcesReorganized.getInstance().logInfo("[ResourcesRefueled] Could not resolve SystemClass for " + systemPos + ", using NORMAL.");
		}
		return SystemClass.NORMAL;
	}

	/**
	 * Derives the system position (corner-origin coords) from a sector position.
	 * Mirrors VoidSystem.getPosFromSector used throughout RRS.
	 */
	public static Vector3i systemPosFromSector(Vector3i sectorPos) {
		Vector3i out = new Vector3i();
		StellarSystem.getPosFromSector(sectorPos, out);
		return out;
	}
}


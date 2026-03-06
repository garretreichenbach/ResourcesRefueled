package videogoose.resourcesreorganized.fuel;

import api.mod.config.PersistentObjectUtil;
import org.schema.common.util.linAlg.Vector3i;
import videogoose.resourcesreorganized.ResourcesReorganized;

import java.util.ArrayList;
import java.util.Map;

/**
 * Owns and manages the global StellarFuelSourcesContainer.
 * Handles persistence load/save via PersistentObjectUtil, mirroring the pattern
 * RRS uses in ResourcesReSourced.loadExtractorData() / saveExtractorData().
 * <p>
 * Call loadFuelData() on ServerInitializeEvent and saveFuelData() on WorldSaveEvent + onDisable().
 */
public class StellarFuelManager {

	/** The live, in-use container. All gameplay code reads/writes here. */
	public static StellarFuelSourcesContainer container = new StellarFuelSourcesContainer();

	/**
	 * The persistence copy — only active (non-empty) suppliers are copied in here
	 * before saving, matching RRS's copyOnlyActiveFrom pattern.
	 */
	private static StellarFuelSourcesContainer persistenceContainer = new StellarFuelSourcesContainer();

	// -------------------------------------------------------------------------
	// Load
	// -------------------------------------------------------------------------

	/**
	 * Restores Heliogen well data from the previous session.
	 * If no persistence data exists, starts with an empty container (suppliers
	 * will be lazily created on first access).
	 *
	 * Mirror of ResourcesReSourced.loadExtractorData().
	 */
	public static void loadFuelData() {
		ResourcesReorganized mod = ResourcesReorganized.getInstance();
		ArrayList<Object> saved = PersistentObjectUtil.getObjects(mod.getSkeleton(), StellarFuelSourcesContainer.class);

		if(saved.isEmpty()) {
			PersistentObjectUtil.addObject(mod.getSkeleton(), new StellarFuelSourcesContainer());
			mod.logInfo("[ResourcesRefueled] No existing Heliogen fuel data found — starting fresh.");
			return;
		}

		mod.logInfo("[ResourcesRefueled] Restoring Heliogen fuel data from previous session...");
		container = new StellarFuelSourcesContainer();
		try {
			persistenceContainer = (StellarFuelSourcesContainer) saved.get(0);
			if(persistenceContainer == null) {
				persistenceContainer = new StellarFuelSourcesContainer();
			} else {
				// Copy all persisted wells into the live container and call afterDeserialize
				// so timestamps are reset to now (time offline doesn't count toward regen).
				container.getMap().putAll(persistenceContainer.getMap());
				container.afterDeserialize();
				mod.logInfo("[ResourcesRefueled] Loaded " + container.size() + " Heliogen fuel well(s).");
			}
			// Ensure the persistence slot holds our reference
			saved.clear();
			saved.add(persistenceContainer);
		} catch(Exception e) {
			mod.logException("[ResourcesRefueled] Failed to load Heliogen fuel data — starting fresh. " + "Wells will be regenerated on demand.", e);
			container = new StellarFuelSourcesContainer();
			persistenceContainer = new StellarFuelSourcesContainer();
		}
	}

	// -------------------------------------------------------------------------
	// Save
	// -------------------------------------------------------------------------

	/**
	 * Saves Heliogen well data to mod persistence.
	 * Only copies suppliers that actually have extractors / accumulated fuel,
	 * preventing unbounded save file growth.
	 *
	 * Mirror of ResourcesReSourced.saveExtractorData().
	 */
	public static void saveFuelData() {
		ResourcesReorganized mod = ResourcesReorganized.getInstance();
		try {
			mod.logInfo("[ResourcesRefueled] Saving Heliogen fuel data...");

			// Snapshot: only keep suppliers with accumulated fuel
			persistenceContainer.getMap().clear();
			for(Map.Entry<Vector3i, StellarFuelSupplier> entry : container.getMap().entrySet()) {
				if(!entry.getValue().isEmpty()) {
					persistenceContainer.getMap().put(new Vector3i(entry.getKey()), entry.getValue());
				}
			}
			persistenceContainer.beforeSerialize();

			ArrayList<Object> saved = PersistentObjectUtil.getObjects(mod.getSkeleton(), StellarFuelSourcesContainer.class);
			if(saved.isEmpty()) {
				PersistentObjectUtil.addObject(mod.getSkeleton(), persistenceContainer);
			}
			// PersistentObjectUtil holds a reference — no need to re-add if already present.

			if(!persistenceContainer.getMap().isEmpty()) {
				PersistentObjectUtil.save(mod.getSkeleton());
				mod.logInfo("[ResourcesRefueled] Saved " + persistenceContainer.size() + " active Heliogen fuel well(s).");
			} else {
				mod.logInfo("[ResourcesRefueled] No active Heliogen fuel wells to save.");
			}
		} catch(Exception e) {
			mod.logException("[ResourcesRefueled] Failed to save Heliogen fuel data.", e);
		}
	}

	// -------------------------------------------------------------------------
	// Convenience accessor
	// -------------------------------------------------------------------------

	/**
	 * Returns the StellarFuelSupplier for the system containing {@code sectorPos},
	 * creating it lazily if needed. Returns null for void systems.
	 */
	public static StellarFuelSupplier getSupplierForSector(Vector3i sectorPos) {
		Vector3i systemPos = StellarFuelSourcesContainer.systemPosFromSector(sectorPos);
		return container.getOrCreate(systemPos);
	}
}


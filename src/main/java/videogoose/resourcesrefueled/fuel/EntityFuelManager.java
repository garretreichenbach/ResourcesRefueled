package videogoose.resourcesrefueled.fuel;

import api.mod.config.PersistentObjectUtil;
import org.schema.game.common.data.player.inventory.Inventory;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;
import videogoose.resourcesrefueled.utils.InventoryUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * Manages per-entity Heliogen fuel caches, providing a persistent virtual view of each
 * entity's tank and canister state that remains valid even when the entity is not loaded.
 * <p>
 * <b>Lifecycle:</b>
 * <ol>
 *   <li>{@link #loadCacheData()} — called on {@code ServerInitializeEvent}; restores state
 *       from the previous session via {@code PersistentObjectUtil}.</li>
 *   <li>{@link #syncFromLive} — called whenever the entity IS loaded (e.g. from
 *       {@code onPreManufacture} or {@code ManagerContainerRegisterEvent}); snapshots the
 *       live tank level + canister count into the cache and clears the dirty flag.</li>
 *   <li>{@link #drainFuelUnits} — debits the cache (tank first, then canisters); sets dirty.</li>
 *   <li>{@link #writeBackToLive} — pushes the dirty cache back to the live entity (tank
 *       and inventory), then clears dirty. Called on {@code WorldSaveEvent}, {@code onDisable},
 *       and opportunistically at the start of each live {@code onPreManufacture} tick if dirty.</li>
 *   <li>{@link #saveCacheData()} — called on {@code WorldSaveEvent} / {@code onDisable};
 *       snapshots non-empty entries to persistence and calls {@code PersistentObjectUtil.save}.</li>
 * </ol>
 *
 * <b>Thread safety:</b> individual cache entries are accessed from the server tick thread
 * only; the container map is written during load/save on the same thread, so no additional
 * synchronisation is needed beyond what {@code ConcurrentHashMap} already provides in
 * {@code FuelTickState}.
 */
public final class EntityFuelManager {

	/** The live, in-use container. All gameplay code reads/writes here. */
	public static EntityFuelCacheContainer container = new EntityFuelCacheContainer();
	/** Persistence-only shadow — only active entries are copied here before saving. */
	private static EntityFuelCacheContainer persistenceContainer = new EntityFuelCacheContainer();

	private EntityFuelManager() {
	}

	// -------------------------------------------------------------------------
	// Persistence — Load
	// -------------------------------------------------------------------------

	/**
	 * Restores per-entity fuel caches from the previous session.
	 * If no data is found, starts with an empty container (caches are lazily created
	 * on first {@code syncFromLive} call).
	 */
	public static void loadCacheData() {
		ResourcesRefueled mod = ResourcesRefueled.getInstance();
		ArrayList<Object> saved = PersistentObjectUtil.getObjects(mod.getSkeleton(), EntityFuelCacheContainer.class);

		if(saved.isEmpty()) {
			PersistentObjectUtil.addObject(mod.getSkeleton(), new EntityFuelCacheContainer());
			mod.logInfo("[ResourcesRefueled] No existing entity fuel cache data found — starting fresh.");
			return;
		}

		mod.logInfo("[ResourcesRefueled] Restoring entity fuel cache data from previous session...");
		container = new EntityFuelCacheContainer();
		try {
			persistenceContainer = (EntityFuelCacheContainer) saved.get(0);
			if(persistenceContainer == null) {
				persistenceContainer = new EntityFuelCacheContainer();
			} else {
				container.getMap().putAll(persistenceContainer.getMap());
				container.afterDeserialize();
				mod.logInfo("[ResourcesRefueled] Loaded " + container.size() + " entity fuel cache(s).");
			}
		} catch(Exception e) {
			mod.logException("[ResourcesRefueled] Failed to load entity fuel cache data — starting fresh.", e);
			container = new EntityFuelCacheContainer();
			persistenceContainer = new EntityFuelCacheContainer();
		}
	}

	// -------------------------------------------------------------------------
	// Persistence — Save
	// -------------------------------------------------------------------------

	/**
	 * Persists non-empty entity fuel caches to mod storage.
	 * Empty entries are omitted to prevent unbounded save-file growth.
	 */
	public static void saveCacheData() {
		ResourcesRefueled mod = ResourcesRefueled.getInstance();
		try {
			mod.logInfo("[ResourcesRefueled] Saving entity fuel cache data...");

			persistenceContainer.clear();
			for(Map.Entry<String, EntityFuelCache> entry : container.getMap().entrySet()) {
				if(!entry.getValue().isEmpty()) {
					persistenceContainer.put(entry.getKey(), entry.getValue());
				}
			}
			persistenceContainer.beforeSerialize();

			ArrayList<Object> saved = PersistentObjectUtil.getObjects(mod.getSkeleton(), EntityFuelCacheContainer.class);
			if(saved.isEmpty()) {
				PersistentObjectUtil.addObject(mod.getSkeleton(), persistenceContainer);
			}

			if(!persistenceContainer.getMap().isEmpty()) {
				PersistentObjectUtil.save(mod.getSkeleton());
				mod.logInfo("[ResourcesRefueled] Saved " + persistenceContainer.size() + " entity fuel cache(s).");
			} else {
				mod.logInfo("[ResourcesRefueled] No active entity fuel caches to save.");
			}
		} catch(Exception e) {
			mod.logException("[ResourcesRefueled] Failed to save entity fuel cache data.", e);
		}
	}

	// -------------------------------------------------------------------------
	// Cache access
	// -------------------------------------------------------------------------

	/**
	 * Returns the cache for {@code uid}, creating a zeroed entry if none exists yet.
	 */
	public static EntityFuelCache getOrCreate(String uid) {
		EntityFuelCache cache = container.get(uid);
		if(cache == null) {
			cache = new EntityFuelCache();
			container.put(uid, cache);
		}
		return cache;
	}

	// -------------------------------------------------------------------------
	// Sync from live entity → cache
	// -------------------------------------------------------------------------

	/**
	 * Snapshots the live entity's fuel state into the cache.
	 * <p>
	 * Call this whenever the entity IS loaded: from {@code onPreManufacture} (extractor tick)
	 * or from {@code ManagerContainerRegisterEvent}. Clears the dirty flag so that a
	 * writeback is only triggered if fuel is actually consumed after this point.
	 *
	 * @param uid            Entity unique identifier.
	 * @param tankModule     The entity's {@link FluidTankSystemModule}, or {@code null}.
	 * @param canisterCount  Number of filled Heliogen Canisters in the factory inventory.
	 */
	public static void syncFromLive(String uid, FluidTankSystemModule tankModule, int canisterCount) {
		EntityFuelCache cache = getOrCreate(uid);

		if(tankModule != null && tankModule.getFluidId() == ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()) {
			cache.tankFluidLevel = tankModule.getCurrentFluidLevel();
			cache.fluidId = tankModule.getFluidId();
		} else {
			cache.tankFluidLevel = 0.0;
			cache.fluidId = 0;
		}

		cache.canisterCount = canisterCount;
		cache.snapshotCanisterCount = canisterCount;
		cache.dirty = false;
	}

	// -------------------------------------------------------------------------
	// Drain
	// -------------------------------------------------------------------------

	/**
	 * Drains {@code units} fluid units from the entity's cache (tank first, then
	 * canisters). Marks the cache dirty if any fuel was consumed.
	 *
	 * @return Actual units drained (may be less if cache is nearly empty).
	 */
	public static double drainFuelUnits(String uid, double units) {
		EntityFuelCache cache = getOrCreate(uid);
		return cache.drain(units, ConfigManager.getFuelPerCanister());
	}

	// -------------------------------------------------------------------------
	// Write-back to live entity
	// -------------------------------------------------------------------------

	/**
	 * Pushes dirty cache state back to the live entity and clears the dirty flag.
	 * <p>
	 * Writes tank level directly. For canisters, computes the delta between the
	 * snapshot count and the current count and removes exactly that many filled
	 * canisters from the inventory, returning empties for each one consumed.
	 * <p>
	 * Safe to call when the entity is live (during a tick, on WorldSave, on jump, etc.).
	 * Inventory operations are skipped if {@code inventories} is null or empty.
	 * This method is a no-op if the cache is not dirty.
	 *
	 * @param uid         Entity UID.
	 * @param tankModule  Live tank module, or {@code null}.
	 * @param inventories All inventories belonging to the entity (may be empty array or null).
	 */
	public static void writeBackToLive(String uid, FluidTankSystemModule tankModule, Inventory... inventories) {
		EntityFuelCache cache = container.get(uid);
		if(cache == null || !cache.dirty) return;

		// Drain the exact delta consumed from the tank since the last sync.
		// Using tankModule.drain() preserves per-network proportionality rather than
		// redistributing a flat total with setCurrentFluidLevel().
		if(tankModule != null && tankModule.getFluidId() == ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()) {
			double tankDelta = cache.snapshotTankFluidLevel - cache.tankFluidLevel;
			if(tankDelta > 0) {
				double actuallyDrained = tankModule.drain(tankDelta);
				// Re-align the snapshot so subsequent write-backs don't double-drain.
				cache.snapshotTankFluidLevel -= actuallyDrained;
			}
		}

		// Reconcile canister inventory using the snapshot delta.
		int canistersConsumed = cache.snapshotCanisterCount - cache.canisterCount;
		if(canistersConsumed > 0 && inventories != null) {
			short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
			short emptyId = ElementRegistry.HELIOGEN_CANISTER_EMPTY.getId();
			int remaining = canistersConsumed;
			for(Inventory inventory : inventories) {
				if(inventory == null || remaining <= 0) continue;
				int toRemove = Math.min(inventory.getOverallQuantity(filledId), remaining);
				if(toRemove > 0) {
					int removed = InventoryUtils.removeItems(inventory, filledId, toRemove);
					if(removed > 0) {
						inventory.incExistingOrNextFreeSlotWithoutException(emptyId, removed);
					}
					remaining -= removed;
				}
			}
		}

		// Update snapshot so future write-backs don't re-apply the same delta.
		cache.snapshotCanisterCount = cache.canisterCount;
		cache.dirty = false;
	}

	// -------------------------------------------------------------------------
	// Convenience readers (read-only, no side effects)
	// -------------------------------------------------------------------------

	/**
	 * Returns the total available fuel units for the entity from the cache,
	 * combining tank and canisters. Returns 0 if no cache exists yet.
	 */
	public static double getAvailableFuelUnits(String uid) {
		EntityFuelCache cache = container.get(uid);
		if(cache == null) return 0.0;
		return cache.totalFuelUnits(ConfigManager.getFuelPerCanister());
	}
}


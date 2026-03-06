package videogoose.resourcesreorganized.fuel;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializable container for per-entity Heliogen fuel caches.
 * <p>
 * Keyed by entity UID (String). Parallels {@link StellarFuelSourcesContainer} in shape
 * and is persisted via {@code PersistentObjectUtil} through {@code EntityFuelManager}.
 * <p>
 * Only entries that are non-empty or dirty need to be persisted; the manager handles
 * filtering before serialisation.
 */
public class EntityFuelCacheContainer implements Serializable {

	private static final long serialVersionUID = 1L;

	private final HashMap<String, EntityFuelCache> cacheMap = new HashMap<>();

	// -------------------------------------------------------------------------
	// Access
	// -------------------------------------------------------------------------

	/** Returns the cache for the given entity UID, or {@code null} if absent. */
	public EntityFuelCache get(String uid) {
		return cacheMap.get(uid);
	}

	/** Stores or replaces the cache for the given entity UID. */
	public void put(String uid, EntityFuelCache cache) {
		cacheMap.put(uid, cache);
	}

	/** Returns {@code true} if a cache entry exists for this UID. */
	public boolean contains(String uid) {
		return cacheMap.containsKey(uid);
	}

	/** Removes the cache entry for the given UID, if present. */
	public void remove(String uid) {
		cacheMap.remove(uid);
	}

	public Map<String, EntityFuelCache> getMap() {
		return cacheMap;
	}

	public int size() {
		return cacheMap.size();
	}

	public void clear() {
		cacheMap.clear();
	}

	// -------------------------------------------------------------------------
	// Serialisation lifecycle
	// -------------------------------------------------------------------------

	/** Invoked before this container is serialised to mod persistence. */
	public void beforeSerialize() {
		for(EntityFuelCache cache : cacheMap.values()) {
			cache.beforeSerialize();
		}
	}

	/** Invoked after this container is deserialised from mod persistence. */
	public void afterDeserialize() {
		for(EntityFuelCache cache : cacheMap.values()) {
			cache.afterDeserialize();
		}
	}
}


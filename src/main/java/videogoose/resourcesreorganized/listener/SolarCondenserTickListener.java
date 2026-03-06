package videogoose.resourcesreorganized.listener;

import api.listener.fastevents.FactoryManufactureListener;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ithirahad.resourcesresourced.universe.starsystem.SystemSheet;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.factory.FactoryCollectionManager;
import org.schema.game.common.controller.elements.factory.FactoryProducerInterface;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.FactoryResource;
import org.schema.game.common.data.element.meta.RecipeInterface;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.server.data.GameServerState;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.fuel.StellarFuelManager;
import videogoose.resourcesreorganized.fuel.StellarFuelSupplier;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.ResourcesReorganized;

/**
 * Applies the star-proximity yield multiplier to the Heliogen Condenser.
 * <p>
 * The base recipe produces 1 Heliogen Plasma per cycle (registered in
 * HeliogenCondenser.postInitData). This listener fires after that item is
 * produced and adds bonus plasma proportional to how close the station is
 * to its local star, using SystemSheet.getTemperature(sector) as the
 * proximity value [0.0 = system edge, 1.0 = star centre].
 * <p>
 * Yield formula:
 *   bonus = floor(BASE_OUTPUT * proximity * (starRegenRate / REGEN_RATE_BASELINE))
 * <p>
 * This means:
 *   - A condenser at proximity 0.0 (system edge) gets no bonus — just the base 1.
 *   - A condenser at proximity 1.0 next to a normal star gets BASE_OUTPUT bonus units.
 *   - Hotter/more exotic stars (higher regenRate) scale the bonus further.
 *   - Void systems (null supplier) produce only the base recipe output with no bonus.
 * <p>
 * Two injection points:
 *   onPreManufacture — blocks the cycle entirely in void systems (no star = no reaction).
 *   onProduceItem    — adds the proximity bonus when a cycle completes successfully.
 */
public class SolarCondenserTickListener implements FactoryManufactureListener {

	/**
	 * The regen rate of a "normal" star, used to normalise bonus across star types.
	 * Matches StellarFuelSupplier.getBaseRegenForClass(SystemClass.NORMAL) = 1.0f.
	 */
	private static final float REGEN_RATE_BASELINE = 1.0f;

	// -------------------------------------------------------------------------
	// onPreManufacture — block void-system condensers
	// -------------------------------------------------------------------------

	@Override
	public boolean onPreManufacture(FactoryCollectionManager factoryCollectionManager, Inventory inventory, LongOpenHashSet[] longOpenHashSets) {
		if(GameServerState.instance == null) return true;

		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || block.getType() != ElementRegistry.HELIOGEN_CONDENSER.getId()) return true;

		ManagedUsableSegmentController<?> entity = (ManagedUsableSegmentController<?>) factoryCollectionManager.getSegmentController();
		Vector3i sector = entity.getSector(new Vector3i());

		StellarFuelSupplier supplier = StellarFuelManager.getSupplierForSector(sector);
		if(supplier == null) {
			// Void system — no star, no condensation reaction possible. Cancel the cycle.
			// Unless debug mode is enabled, in which case allow testing anywhere.
			if(!ConfigManager.isDebugMode()) return false;
			ResourcesReorganized.getInstance().logInfo("[ResourcesReorganized] Debug mode enabled: allowing Helio­gen Condenser cycle in void system " + sector);
		}

		return true;
	}

	// -------------------------------------------------------------------------
	// onProduceItem — apply proximity bonus
	// -------------------------------------------------------------------------

	@Override
	public void onProduceItem(RecipeInterface recipeInterface, Inventory inventory, FactoryProducerInterface producer, FactoryResource product, int quantity, IntCollection changeSet) {
		if(GameServerState.instance == null) return;

		// Only act on Heliogen Plasma output from a Heliogen Condenser.
		SegmentPiece block = inventory.getBlockIfPossible();
		if(block == null || block.getType() != ElementRegistry.HELIOGEN_CONDENSER.getId()) return;
		if(product.type != ElementRegistry.HELIOGEN_PLASMA.getId()) return;

		ManagedUsableSegmentController<?> entity = (ManagedUsableSegmentController<?>) ((FactoryCollectionManager) producer).getSegmentController();
		Vector3i sector = entity.getSector(new Vector3i());

		float proximity = SystemSheet.getTemperature(sector);
		if(proximity <= 0.0f && !ConfigManager.isDebugMode()) return; // at the very edge, no bonus

		StellarFuelSupplier supplier = StellarFuelManager.getSupplierForSector(sector);
		if(supplier == null) {
			if(!ConfigManager.isDebugMode()) return; // void system — blocked in onPreManufacture but guard here too
			// Debug mode: fake a normal star so testing is easy away from stars.
			ResourcesReorganized.getInstance().logInfo("[ResourcesReorganized] Debug mode: applying condenser bonus as if near a normal star at " + sector);
			// treat as normal star
			float effectiveProximity = 1.0f;
			int bonus = (int) Math.floor(ConfigManager.getCondenserBaseOutput() * effectiveProximity * REGEN_RATE_BASELINE * quantity);
			if(bonus <= 0) return;

			int inventoryChange = inventory.incExistingOrNextFreeSlotWithoutException(ElementRegistry.HELIOGEN_PLASMA.getId(), bonus);
			changeSet.add(inventoryChange);
			return;
		}

		// Scale bonus by proximity and star class relative to a normal star.
		float starMultiplier = supplier.getBaseRegenRate() / REGEN_RATE_BASELINE;
		float effectiveProximity = ConfigManager.isCondenserProximityScaled() ? proximity : 1.0f;
		int bonus = (int) Math.floor(ConfigManager.getCondenserBaseOutput() * effectiveProximity * starMultiplier * quantity);
		if(bonus <= 0) return;

		int inventoryChange = inventory.incExistingOrNextFreeSlotWithoutException(ElementRegistry.HELIOGEN_PLASMA.getId(), bonus);
		changeSet.add(inventoryChange);
	}
}


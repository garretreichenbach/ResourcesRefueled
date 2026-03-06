package videogoose.resourcesreorganized.listener;

import api.listener.Listener;
import api.listener.events.entity.ShipJumpEngageEvent;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.server.controller.SectorSwitch;
import org.schema.game.server.data.GameServerState;
import org.schema.schine.network.server.ServerMessage;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.fuel.EntityFuelManager;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.systems.FluidSystemModule;

/**
 * Handles Heliogen fuel consumption when a ship engages its FTL drive.
 * <p>
 * Fuel is read from and consumed via {@link EntityFuelManager}, which maintains a
 * virtualised cache of the entity's tank + canister state. The cache is synced from
 * the live entity immediately before the jump and written back to it after draining,
 * so the entity's actual tank and inventory always reflect the consumed fuel.
 */
public class ShipJumpFuelListener extends Listener<ShipJumpEngageEvent> {

	@Override
	public void onEvent(ShipJumpEngageEvent event) {
		if(!(event.getController() instanceof Ship)) return;
		if(!event.getController().isOnServer()) return;

		Vector3i currentSector = event.getOriginalSectorPos();
		Vector3i targetSector = event.getNewSector();
		double jumpDistance = Vector3i.getDistance(currentSector, targetSector);

		int canistersNeeded = (int) Math.ceil(jumpDistance * ConfigManager.getFtlFuelPerSector());
		if(canistersNeeded <= 0) return;

		Ship ship = (Ship) event.getController();
		ShipManagerContainer shipManagerContainer = ship.getManagerContainer();
		String uid = ship.getUniqueIdentifier();
		FluidSystemModule tankModule = (FluidSystemModule) shipManagerContainer.getModMCModule(ElementRegistry.FLUID_TANK.getId());

		// Collect all ship inventories for canister reconciliation on write-back.
		Inventory[] inventories = shipManagerContainer.getInventories().values().toArray(new Inventory[0]);

		// Sync cache from the live entity before doing any fuel arithmetic.
		int totalCanisters = countInventoryCanisters(shipManagerContainer);
		EntityFuelManager.syncFromLive(uid, tankModule, totalCanisters);

		double fuelPerCanister = ConfigManager.getFuelPerCanister();
		double fuelRequired = canistersNeeded * fuelPerCanister;
		double fuelAvailable = EntityFuelManager.getAvailableFuelUnits(uid);

		// Calculate the effective jump target given available fuel.
		// canistersAvailable is expressed in whole-canister equivalents for the redirect formula.
		int canistersAvailable = (int) Math.floor(fuelAvailable / fuelPerCanister);
		Vector3i redirect = calcShortJumpTarget(currentSector, targetSector, canistersAvailable, canistersNeeded);

		// Drain from cache (tank first, then canisters).
		double toDrain = Math.min(fuelRequired, fuelAvailable);
		double drained = EntityFuelManager.drainFuelUnits(uid, toDrain);

		// Write the drained state back to the live entity now that we have the amounts.
		EntityFuelManager.writeBackToLive(uid, tankModule, inventories);

		boolean hadEnough = (drained >= fuelRequired - 0.001);

		if(hadEnough) {
			ship.sendServerMessage("[Heliogen System] Consumed " + String.format("%.1f", drained) + " fuel units for jump.", ServerMessage.MESSAGE_TYPE_INFO);
		} else if(drained > 0) {
			ship.sendServerMessage("[Heliogen System] Insufficient fuel — partial consumption of " + String.format("%.1f", drained) + " fuel units. Redirecting jump.", ServerMessage.MESSAGE_TYPE_WARNING);
		} else {
			ship.sendServerMessage("[Heliogen System] No Heliogen fuel available.", ServerMessage.MESSAGE_TYPE_WARNING);
		}

		handleJump(event, hadEnough, redirect);
	}

	/**
	 * Calculates the furthest sector we can jump to toward {@code target} given
	 * {@code availableCanisters} out of {@code neededCanisters} for the full distance.
	 * Always returns at least one sector of progress so the ship isn't left stranded,
	 * and never overshoots the target.
	 */
	private static Vector3i calcShortJumpTarget(Vector3i origin, Vector3i target, int availableCanisters, int neededCanisters) {
		if(availableCanisters >= neededCanisters) {
			return new Vector3i(target);
		}

		double dx = target.x - origin.x;
		double dy = target.y - origin.y;
		double dz = target.z - origin.z;
		double fullDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if(fullDist == 0) return new Vector3i(origin);

		double fraction = (neededCanisters > 0) ? (double) availableCanisters / neededCanisters : 0;
		double reachableDist = Math.max(1.0, fraction * fullDist);
		reachableDist = Math.min(reachableDist, fullDist);

		int rx = origin.x + (int) Math.round((dx / fullDist) * reachableDist);
		int ry = origin.y + (int) Math.round((dy / fullDist) * reachableDist);
		int rz = origin.z + (int) Math.round((dz / fullDist) * reachableDist);

		return new Vector3i(rx, ry, rz);
	}

	/** Counts total filled Heliogen Canisters across all ship inventories. */
	private static int countInventoryCanisters(ShipManagerContainer shipManagerContainer) {
		short filledId = ElementRegistry.HELIOGEN_CANISTER_FILLED.getId();
		int total = 0;
		for(Inventory inventory : shipManagerContainer.getInventories().values()) {
			total += inventory.getOverallQuantity(filledId);
		}
		return total;
	}

	/**
	 * Executes the jump to {@code redirect} with appropriate messaging.
	 */
	private void handleJump(ShipJumpEngageEvent event, boolean hadEnough, Vector3i redirect) {
		event.setCanceled(true);
		event.getController().getNetworkObject().graphicsEffectModifier.add((byte) 1);
		Ship ship = (Ship) event.getController();
		SectorSwitch queueSectorSwitch = ((GameServerState) ship.getState()).getController().queueSectorSwitch(ship, redirect, SectorSwitch.TRANS_JUMP, false, true, true);
		if(queueSectorSwitch != null) {
			queueSectorSwitch.delay = System.currentTimeMillis() + 4000;
			queueSectorSwitch.jumpSpawnPos = ship.getWorldTransform().origin;
			queueSectorSwitch.executionGraphicsEffect = 2;
			queueSectorSwitch.keepJumpBasisWithJumpPos = true;
		}
		String jumpTypeMsg = hadEnough ? "Jumping to target sector -> %s" : "Jumping on reserves to target sector -> %s";
		ship.sendServerMessage(String.format(jumpTypeMsg, redirect.toStringPure()), ServerMessage.MESSAGE_TYPE_INFO);
	}
}

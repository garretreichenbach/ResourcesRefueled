package videogoose.resourcesreorganized.mixin.inventory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.StashInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import videogoose.resourcesreorganized.logistics.item.runtime.InventoryReferenceRegistry;
import videogoose.resourcesreorganized.manager.ConfigManager;

/**
 * Cancels StarMade's automatic storage-to-storage item pulls so items only move through this mod's
 * conveyor / item-pipe networks.
 * <p>
 * Both the storage pull-filter loop ({@code handleFilterInventories}) and rail load/unload transfers
 * ({@code handleDockedInventories}) call {@code ManagerContainer.doInventoryTransferFrom}. The two call
 * sites are redirected independently:
 * <ul>
 *     <li><b>Pull filters</b> are always cancelled &mdash; they are the vanilla teleportation being replaced.</li>
 *     <li><b>Rail load/unload</b> is allowed only when one of the involved storages is a registered network
 *     port, i.e. the rail block is wired into a conveyor / pipe network; otherwise it is cancelled.</li>
 * </ul>
 * Manual player GUI transfers go through {@code Inventory.doSwitchSlotsOrCombine} and are left untouched.
 */
@Mixin(value = ManagerContainer.class, remap = false)
public abstract class StorageAutoPullMixin {

	@Shadow
	private boolean doInventoryTransferFrom(StashInventory pullTo, Inventory pullFrom, Object2ObjectOpenHashMap<Inventory, IntOpenHashSet> changedMap) {
		throw new AssertionError("shadow");
	}

	@Redirect(method = "handleFilterInventories", at = @At(value = "INVOKE", target = "Lorg/schema/game/common/controller/elements/ManagerContainer;doInventoryTransferFrom(Lorg/schema/game/common/data/player/inventory/StashInventory;Lorg/schema/game/common/data/player/inventory/Inventory;Lit/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;)Z"))
	private boolean rr$redirectFilterPull(ManagerContainer self, StashInventory pullTo, Inventory pullFrom, Object2ObjectOpenHashMap<Inventory, IntOpenHashSet> changedMap) {
		if(ConfigManager.isLogisticsBlockAutoPull()) {
			return false;
		}
		return doInventoryTransferFrom(pullTo, pullFrom, changedMap);
	}

	@Redirect(method = "handleDockedInventories", at = @At(value = "INVOKE", target = "Lorg/schema/game/common/controller/elements/ManagerContainer;doInventoryTransferFrom(Lorg/schema/game/common/data/player/inventory/StashInventory;Lorg/schema/game/common/data/player/inventory/Inventory;Lit/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;)Z"))
	private boolean rr$redirectRailPull(ManagerContainer self, StashInventory pullTo, Inventory pullFrom, Object2ObjectOpenHashMap<Inventory, IntOpenHashSet> changedMap) {
		if(ConfigManager.isLogisticsBlockAutoPull() && !rr$isNetworkPort(pullTo) && !rr$isNetworkPort(pullFrom)) {
			return false;
		}
		return doInventoryTransferFrom(pullTo, pullFrom, changedMap);
	}

	private static boolean rr$isNetworkPort(Inventory inventory) {
		if(inventory == null) {
			return false;
		}
		return InventoryReferenceRegistry.isRegistered("inv:" + Integer.toHexString(System.identityHashCode(inventory)));
	}
}

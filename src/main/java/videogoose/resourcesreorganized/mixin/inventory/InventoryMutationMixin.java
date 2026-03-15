package videogoose.resourcesreorganized.mixin.inventory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.player.inventory.InventoryMultMod;
import org.schema.game.common.data.player.inventory.NetworkInventoryInterface;
import org.schema.game.common.data.player.inventory.InventorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.resourcesreorganized.logistics.ItemMutationIngressAdapter;
import videogoose.resourcesreorganized.logistics.InventoryMutationProbe;
import videogoose.resourcesreorganized.manager.ConfigManager;

import java.io.DataInput;

@Mixin(value = Inventory.class, remap = false)
public abstract class InventoryMutationMixin {

	@Inject(method = "inc(ISI)V", at = @At("HEAD"), cancellable = true, remap = false)
	private void rr$probeInc(int slot, short type, int count, CallbackInfo ci) {
		InventoryMutationProbe.logInc((Inventory) (Object) this, slot, type, count);
		if(ConfigManager.isLogisticsInterceptEnabled()) {
			boolean handled = ItemMutationIngressAdapter.tryHandleInventoryMutation("inc", (Inventory) (Object) this, type, count, -1);
			if(handled) {
				ci.cancel();
			}
		}
	}

	@Inject(method = "put(ISII)Lorg/schema/game/common/data/player/inventory/InventorySlot;", at = @At("HEAD"), cancellable = true, remap = false)
	private void rr$probePut(int slot, short type, int count, int meta, CallbackInfoReturnable<InventorySlot> cir) {
		InventoryMutationProbe.logPut((Inventory) (Object) this, slot, type, count, meta);
		if(ConfigManager.isLogisticsInterceptEnabled()) {
			Inventory self = (Inventory) (Object) this;
			boolean handled = ItemMutationIngressAdapter.tryHandleInventoryMutation("put", self, type, count, meta);
			if(handled) {
				cir.setReturnValue(self.getSlot(slot));
			}
		}
	}

	@Inject(method = "handleReceived(Lorg/schema/game/common/data/player/inventory/InventoryMultMod;Lorg/schema/game/common/data/player/inventory/NetworkInventoryInterface;)V", at = @At("HEAD"), remap = false)
	private void rr$probeHandleReceived(InventoryMultMod mod, NetworkInventoryInterface inventoryInterface, CallbackInfo ci) {
		InventoryMutationProbe.logHandleReceived((Inventory) (Object) this, mod, inventoryInterface);
	}

	@Inject(method = "deserialize(Ljava/io/DataInput;)V", at = @At("HEAD"), remap = false)
	private void rr$probeDeserialize(DataInput buffer, CallbackInfo ci) {
		InventoryMutationProbe.logDeserialize((Inventory) (Object) this);
	}

	@Inject(method = "deserializeSlot(Ljava/io/DataInput;)Lorg/schema/game/common/data/player/inventory/InventorySlot;", at = @At("HEAD"), remap = false)
	private void rr$probeDeserializeSlot(DataInput buffer, CallbackInfoReturnable<InventorySlot> cir) {
		InventoryMutationProbe.logDeserializeSlot((Inventory) (Object) this);
	}

	@Inject(method = "doSwitchSlotsOrCombine", at = @At("HEAD"), remap = false)
	private void rr$probeDoSwitchSlotsOrCombine(int slot, int otherSlot, int subSlotFromOther, Inventory otherInventory, int count, Object2ObjectOpenHashMap<Inventory, IntOpenHashSet> moddedSlots, CallbackInfo ci) {
		InventoryMutationProbe.logSwitchOrCombine((Inventory) (Object) this, slot, otherSlot, subSlotFromOther, otherInventory, count);
	}
}


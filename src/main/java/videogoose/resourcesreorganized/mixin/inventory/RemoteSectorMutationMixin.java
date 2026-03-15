package videogoose.resourcesreorganized.mixin.inventory;

import org.schema.game.common.data.world.RemoteSector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.resourcesreorganized.logistics.InventoryMutationProbe;

import javax.vecmath.Vector3f;

@Mixin(value = RemoteSector.class, remap = false)
public abstract class RemoteSectorMutationMixin {

	@Inject(method = "addItem(Ljavax/vecmath/Vector3f;SII)V", at = @At("HEAD"), remap = false)
	private void rr$probeAddItem(Vector3f pos, short type, int metaId, int count, CallbackInfo ci) {
		InventoryMutationProbe.logRemoteAddItem(this, pos, type, metaId, count);
	}
}


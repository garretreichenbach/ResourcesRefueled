package videogoose.resourcesrefueled.listener;

import api.listener.fastevents.segmentpiece.SegmentPieceKilledListener;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SendableSegmentController;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.ModuleExplosion;
import org.schema.game.common.data.SegmentPiece;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;

import java.util.Collections;
import java.util.List;

public class SegmentPieceKillEvent implements SegmentPieceKilledListener {

	@Override
	public void onBlockKilled(SegmentPiece segmentPiece, SendableSegmentController sendableSegmentController, @Nullable Damager damager, boolean b) {
		if(ElementRegistry.isFluidTank(segmentPiece.getType())) {
			if(sendableSegmentController instanceof ManagedUsableSegmentController<?>) {
				ManagedUsableSegmentController<?> managed = (ManagedUsableSegmentController<?>) sendableSegmentController;
				ManagerContainer<?> managerContainer = managed.getManagerContainer();
				if(managerContainer.getModMCModule(segmentPiece.getType()) instanceof FluidTankSystemModule) {
					FluidTankSystemModule tankModule = (FluidTankSystemModule) managerContainer.getModMCModule(segmentPiece.getType());
					List<ModuleExplosion> explosionList = createExplosionList(segmentPiece, tankModule);
					for(ModuleExplosion explosion : explosionList) {
						managerContainer.addModuleExplosions(explosion);
					}
				}
			}
		}
	}

	/**
	 * Calculates a bunch of random positions on the tank to spawn explosions at, the amount of positions is based on how much fluid was in the tank.
	 */
	private List<ModuleExplosion> createExplosionList(SegmentPiece segmentPiece, FluidTankSystemModule tankModule) {
		int fluidLevelPerExplosion = ConfigManager.getFluidLevelPerExplosion();
		LongList positions = new LongArrayList();
		LongList blocks = tankModule.getBlockIndices();
		int amountOfExplosions = (int) Math.ceil(tankModule.getCurrentFluidLevel() / fluidLevelPerExplosion);
		for(int i = 0; i < amountOfExplosions; i++) {
			long randomBlock = blocks.getLong((int) (Math.random() * blocks.size()));
			positions.add(randomBlock);
		}
		float radius = (float) (tankModule.getCurrentFluidLevel() / tankModule.getTankCapacity() * ConfigManager.getMaxFluidExplosionRadius());
		float damage = ConfigManager.getFluidExplosionDamage();
		return Collections.singletonList(new ModuleExplosion(positions, 0L, (int) radius, (int) damage, segmentPiece.getAbsoluteIndex(), ModuleExplosion.ExplosionCause.POWER_AUX, tankModule.getBoundingBox()));
	}
}

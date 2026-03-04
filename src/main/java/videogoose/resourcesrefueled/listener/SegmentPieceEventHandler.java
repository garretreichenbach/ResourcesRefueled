package videogoose.resourcesrefueled.listener;

import api.listener.fastevents.segmentpiece.SegmentPieceAddListener;
import api.listener.fastevents.segmentpiece.SegmentPieceKilledListener;
import api.listener.fastevents.segmentpiece.SegmentPieceRemoveListener;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.SendableSegmentController;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.ModuleExplosion;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.world.Segment;
import videogoose.resourcesrefueled.element.ElementRegistry;
import videogoose.resourcesrefueled.manager.ConfigManager;
import videogoose.resourcesrefueled.systems.FluidTankSystemModule;

import java.util.Collections;
import java.util.List;

public class SegmentPieceEventHandler implements SegmentPieceAddListener, SegmentPieceRemoveListener, SegmentPieceKilledListener {
	@Override
	public void onAdd(SegmentController segmentController, short type, byte orientation, byte x, byte y, byte z, Segment segment, boolean updateSegmentBuffer, long index, boolean server) {
		if(server && segmentController instanceof ManagedUsableSegmentController<?>) {
			ManagerContainer<?> container = ((ManagedUsableSegmentController<?>) segmentController).getManagerContainer();
			if(container.getModMCModule(ElementRegistry.FLUID_TANK.getId()) instanceof FluidTankSystemModule) {
				FluidTankSystemModule module = (FluidTankSystemModule) container.getModMCModule(ElementRegistry.FLUID_TANK.getId());
				module.onPlace(index, type);
			}
		}
	}

	@Override
	public void onBlockRemove(short type, int segmentSize, byte x, byte y, byte z, byte b3, Segment segment, boolean preserveControl, boolean server) {
		if(server && segment.getSegmentController() instanceof ManagedUsableSegmentController<?>) {
			ManagerContainer<?> container = ((ManagedUsableSegmentController<?>) segment.getSegmentController()).getManagerContainer();
			if(container.getModMCModule(ElementRegistry.FLUID_TANK.getId()) instanceof FluidTankSystemModule) {
				FluidTankSystemModule module = (FluidTankSystemModule) container.getModMCModule(ElementRegistry.FLUID_TANK.getId());
				module.onRemove(ElementCollection.getIndex4(x, y, z, type), type);
			}
		}
	}

	@Override
	public void onBlockKilled(SegmentPiece segmentPiece, SendableSegmentController sendableSegmentController, @Nullable Damager damager, boolean b) {
		short type = segmentPiece.getType();
		if(type != ElementRegistry.FLUID_TANK.getId() && !ElementRegistry.isPipe(type)) return;

		if(sendableSegmentController instanceof ManagedUsableSegmentController<?>) {
			ManagedUsableSegmentController<?> managed = (ManagedUsableSegmentController<?>) sendableSegmentController;
			ManagerContainer<?> managerContainer = managed.getManagerContainer();
			if(managerContainer.getModMCModule(ElementRegistry.FLUID_TANK.getId()) instanceof FluidTankSystemModule) {
				FluidTankSystemModule tankModule = (FluidTankSystemModule) managerContainer.getModMCModule(ElementRegistry.FLUID_TANK.getId());
				long blockIndex = segmentPiece.getAbsoluteIndex();
				// Use the fluid level of the specific network this block belonged to,
				// so only that network's stored fluid contributes to the explosion.
				double networkFluid = tankModule.getFluidLevelForBlock(blockIndex);
				if(networkFluid > 0 && !tankModule.getBlockIndicesForExplosion(blockIndex).isEmpty()) {
					List<ModuleExplosion> explosionList = createExplosionList(segmentPiece, tankModule, blockIndex);
					for(ModuleExplosion explosion : explosionList) {
						managerContainer.addModuleExplosions(explosion);
					}
				}
			}
		}
	}

	private List<ModuleExplosion> createExplosionList(SegmentPiece segmentPiece, FluidTankSystemModule tankModule, long blockIndex) {
		int fluidLevelPerExplosion = ConfigManager.getFluidLevelPerExplosion();
		LongList blocks = tankModule.getBlockIndicesForExplosion(blockIndex);
		double networkFluid = tankModule.getFluidLevelForBlock(blockIndex);
		double networkCapacity = tankModule.getCapacityForBlock(blockIndex);

		LongList positions = new LongArrayList();
		int amountOfExplosions = (int) Math.ceil(networkFluid / fluidLevelPerExplosion);
		for(int i = 0; i < amountOfExplosions; i++) {
			long randomBlock = blocks.getLong((int) (Math.random() * blocks.size()));
			positions.add(randomBlock);
		}
		float radius = networkCapacity > 0 ? (float) (networkFluid / networkCapacity * ConfigManager.getMaxFluidExplosionRadius()) : 0.0f;
		float damage = ConfigManager.getFluidExplosionDamage();
		return Collections.singletonList(new ModuleExplosion(positions, 0L, (int) radius, (int) damage, segmentPiece.getAbsoluteIndex(), ModuleExplosion.ExplosionCause.POWER_AUX, tankModule.getBoundingBoxForBlock(blockIndex)));
	}
}

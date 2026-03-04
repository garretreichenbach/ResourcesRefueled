package videogoose.resourcesrefueled.systems;

import api.utils.game.module.util.SystemModule;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.network.SerialializationInterface;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FluidTankSystemModule extends SystemModule<FluidTankSystemModule.TankSystemData> {

	public FluidTankSystemModule(SegmentController entity, ManagerContainer<?> managerContainer) {
		super(entity, managerContainer, ResourcesRefueled.getInstance(), ElementRegistry.HELIOGEN_TANK.getId(), new TankSystemData());
	}

	/**
	 * Handle is the update function for the module, called every tick. Put your main logic here.
	 * @param timer The timer object contains timing information about the current tick, such as delta time and total time. Use it for any time-based calculations or actions.
	 */
	@Override
	public void handle(Timer timer) {

	}

	@Override
	public double getPowerConsumedPerSecondResting() {
		return 0;
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
		return 0;
	}

	@Override
	public String getName() {
		return "Fluid Tank System Module";
	}

	public short getFluidTypeId() {
		return getData().fluidTypeId;
	}

	public void setFluidTypeId(short fluidTypeId) {
		getData().fluidTypeId = fluidTypeId;
		flagUpdatedData();
	}

	public double getTankCapacity() {
		return getData().tankCapacity;
	}

	public void setTankCapacity(double tankCapacity) {
		getData().tankCapacity = tankCapacity;
		flagUpdatedData();
	}

	public double getCurrentFluidLevel() {
		return getData().currentFluidLevel;
	}

	public void setCurrentFluidLevel(double currentFluidLevel) {
		getData().currentFluidLevel = currentFluidLevel;
		flagUpdatedData();
	}

	protected static class TankSystemData implements SerialializationInterface {

		private short fluidTypeId;
		private double tankCapacity;
		private double currentFluidLevel;

		@Override
		public void serialize(DataOutput dataOutput, boolean b) throws IOException {
			dataOutput.writeShort(fluidTypeId);
			dataOutput.writeDouble(tankCapacity);
			dataOutput.writeDouble(currentFluidLevel);
		}

		@Override
		public void deserialize(DataInput dataInput, int i, boolean b) throws IOException {
			fluidTypeId = dataInput.readShort();
			tankCapacity = dataInput.readDouble();
			currentFluidLevel = dataInput.readDouble();
		}
	}
}

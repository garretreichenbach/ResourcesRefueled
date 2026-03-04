package videogoose.resourcesrefueled.systems;

import api.utils.game.module.util.SystemModule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import org.schema.schine.network.SerialializationInterface;
import videogoose.resourcesrefueled.ResourcesRefueled;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FluidTankSystemModule extends SystemModule<FluidTankSystemModule.TankSystemData> {

	public FluidTankSystemModule(SegmentController entity, ManagerContainer<?> managerContainer, short tankId, short fluidId) {
		super(entity, managerContainer, ResourcesRefueled.getInstance(), tankId, new TankSystemData(fluidId, 1000, 0));
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
		return "Fluid Tank System Module - " + ElementKeyMap.getInfo(getFluidTypeId()).getName();
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

	public LongArrayList getBlockIndices() {
		LongArrayList indices = new LongArrayList();
		for(long index : blocks.keySet()) {
			indices.add(index);
		}
		return indices;
	}

	public BoundingBox getBoundingBox() {
		BoundingBox boundingBox = new BoundingBox();
		Vector3i min = new Vector3i(0, 0, 0);
		Vector3i max = new Vector3i(0, 0, 0);
		Vector3i pos = new Vector3i();
		for(long index : blocks.keySet()) {
			ElementCollection.getPosFromIndex(index, pos);
			min.set(Math.min(min.x, pos.x), Math.min(min.y, pos.y), Math.min(min.z, pos.z));
			max.set(Math.max(max.x, pos.x), Math.max(max.y, pos.y), Math.max(max.z, pos.z));
		}
		return boundingBox;
	}

	protected static class TankSystemData implements SerialializationInterface {

		private short fluidTypeId;
		private double tankCapacity;
		private double currentFluidLevel;

		public TankSystemData() {
			fluidTypeId = 0;
			tankCapacity = 0;
			currentFluidLevel = 0;
		}

		public TankSystemData(short fluidTypeId, double tankCapacity, double currentFluidLevel) {
			this.fluidTypeId = fluidTypeId;
			this.tankCapacity = tankCapacity;
			this.currentFluidLevel = currentFluidLevel;
		}

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

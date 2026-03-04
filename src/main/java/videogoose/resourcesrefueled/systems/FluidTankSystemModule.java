package videogoose.resourcesrefueled.systems;

import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.module.util.SystemModule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import videogoose.resourcesrefueled.ResourcesRefueled;

import java.io.IOException;

public class FluidTankSystemModule extends SystemModule {

	private short fluidId;
	private double tankCapacity;
	private double currentFluidLevel;

	public FluidTankSystemModule(SegmentController entity, ManagerContainer<?> managerContainer, short tankId, short fluidId) {
		super(entity, managerContainer, ResourcesRefueled.getInstance(), tankId);
		this.fluidId = fluidId;
	}

	/**
	 * Handle is the update function for the module, called every tick. Put your main logic here.
	 * @param timer The timer object contains timing information about the current tick, such as delta time and total time. Use it for any time-based calculations or actions.
	 */
	@Override
	public void handle(Timer timer) {

	}

	@Override
	public void onTagSerialize(PacketWriteBuffer buffer) throws IOException {
		buffer.writeShort(fluidId);
		buffer.writeDouble(tankCapacity);
		buffer.writeDouble(currentFluidLevel);
	}

	@Override
	public void onTagDeserialize(PacketReadBuffer buffer) throws IOException {
		fluidId = buffer.readShort();
		tankCapacity = buffer.readDouble();
		currentFluidLevel = buffer.readDouble();
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
		return "Fluid Tank System Module - " + ElementKeyMap.getInfo(fluidId).getName();
	}

	public short getFluidId() {
		return fluidId;
	}

	public void setFluidId(short fluidId) {
		this.fluidId = fluidId;
		flagUpdatedData();
	}

	public double getTankCapacity() {
		return tankCapacity;
	}

	public void setTankCapacity(double tankCapacity) {
		this.tankCapacity = tankCapacity;
		flagUpdatedData();
	}

	public double getCurrentFluidLevel() {
		return currentFluidLevel;
	}

	public void setCurrentFluidLevel(double currentFluidLevel) {
		this.currentFluidLevel = currentFluidLevel;
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
}

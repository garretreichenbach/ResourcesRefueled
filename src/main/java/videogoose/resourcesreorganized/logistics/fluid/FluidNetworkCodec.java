package videogoose.resourcesreorganized.logistics.fluid;

import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.systems.FluidSystemModule;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class FluidNetworkCodec {

	private FluidNetworkCodec() {
	}

	public static void serialize(PacketWriteBuffer buffer, List<FluidSystemModule.FluidNetwork> networks, Map<Long, FluidSystemModule.FluidTankSegment> tankSegments, Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments) throws IOException {
		buffer.writeInt(networks.size());
		for(FluidSystemModule.FluidNetwork net : networks) {
			buffer.writeShort(net.fluidId);
			buffer.writeDouble(net.fluidLevel);
			buffer.writeInt(net.memberIndices.size());
			for(long idx : net.memberIndices) {
				buffer.writeLong(idx);
				short type;
				if(tankSegments.containsKey(idx)) {
					type = tankSegments.get(idx).blockType;
				} else if(pipeSegments.containsKey(idx)) {
					type = pipeSegments.get(idx).blockType;
				} else {
					type = 0;
				}
				buffer.writeShort(type);
			}
		}
	}

	public static void deserialize(PacketReadBuffer buffer, List<FluidSystemModule.FluidNetwork> networks, Map<Long, FluidSystemModule.FluidTankSegment> tankSegments, Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments) throws IOException {
		tankSegments.clear();
		pipeSegments.clear();
		networks.clear();

		int networkCount = buffer.readInt();
		for(int n = 0; n < networkCount; n++) {
			FluidSystemModule.FluidNetwork net = new FluidSystemModule.FluidNetwork();
			net.fluidId = buffer.readShort();
			net.fluidLevel = buffer.readDouble();
			int memberCount = buffer.readInt();
			for(int m = 0; m < memberCount; m++) {
				long idx = buffer.readLong();
				short type = buffer.readShort();
				net.memberIndices.add(idx);
				if(type == ElementRegistry.FLUID_TANK.getId()) {
					tankSegments.put(idx, new FluidSystemModule.FluidTankSegment(idx, type));
				} else if(ElementRegistry.isPipe(type)) {
					pipeSegments.put(idx, new FluidSystemModule.FluidPipeSegment(idx, type));
				}
			}
			FluidTopologyMutationService.recalculateNetworkCapacity(net, tankSegments);
			net.fluidLevel = Math.min(net.fluidLevel, net.tankCapacity);
			networks.add(net);
		}
	}
}


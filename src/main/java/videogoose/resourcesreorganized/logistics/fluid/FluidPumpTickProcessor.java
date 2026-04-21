package videogoose.resourcesreorganized.logistics.fluid;

import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.systems.FluidSystemModule;

import java.util.*;
import java.util.function.Consumer;

public final class FluidPumpTickProcessor {

	private FluidPumpTickProcessor() {
	}

	public static boolean process(Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments, Map<Long, FluidSystemModule.FluidPortSegment> portSegments, List<FluidSystemModule.FluidNetwork> networks, double pumpRate, double portBufferCapacity, Consumer<String> debugLogger) {
		boolean anyChange = false;
		float ticksPerSecond = 20.0f;

		for(FluidSystemModule.FluidPipeSegment seg : pipeSegments.values()) {
			if(seg.blockType != ElementRegistry.PIPE_PUMP.getId()) {
				continue;
			}
			long pumpIndex = seg.blockIndex;

			Set<FluidSystemModule.FluidNetwork> adjacentNets = new LinkedHashSet<>();
			List<FluidSystemModule.FluidPortSegment> adjacentPorts = new ArrayList<>();
			for(long nb : FluidTopologyUtils.faceAdjacentIndices(pumpIndex)) {
				FluidSystemModule.FluidPortSegment portSeg = portSegments.get(nb);
				if(portSeg != null) {
					adjacentPorts.add(portSeg);
				}
				for(FluidSystemModule.FluidNetwork net : networks) {
					if(net.memberIndices.contains(nb)) {
						adjacentNets.add(net);
						break;
					}
				}
			}

			if(adjacentNets.size() + adjacentPorts.size() < 2) {
				seg.flowRate = 0;
				continue;
			}

			FluidSystemModule.FluidNetwork sourceNet = null;
			FluidSystemModule.FluidPortSegment sourcePort = null;
			for(FluidSystemModule.FluidNetwork net : adjacentNets) {
				if(net.fluidLevel > 0) {
					sourceNet = net;
					break;
				}
			}
			if(sourceNet == null) {
				for(FluidSystemModule.FluidPortSegment port : adjacentPorts) {
					if(port.bufferLevel > 0) {
						sourcePort = port;
						break;
					}
				}
			}

			short sourceFluidId = sourceNet != null ? sourceNet.fluidId : (sourcePort != null ? sourcePort.bufferFluidId : 0);
			FluidSystemModule.FluidNetwork targetNet = null;
			FluidSystemModule.FluidPortSegment targetPort = null;
			for(FluidSystemModule.FluidNetwork net : adjacentNets) {
				if(net == sourceNet) {
					continue;
				}
				if(net.fluidId != 0 && net.fluidId != sourceFluidId) {
					continue;
				}
				if(net.tankCapacity > net.fluidLevel) {
					targetNet = net;
					break;
				}
			}
			if(targetNet == null) {
				for(FluidSystemModule.FluidPortSegment port : adjacentPorts) {
					if(port == sourcePort) {
						continue;
					}
					if(port.bufferFluidId != 0 && port.bufferFluidId != sourceFluidId) {
						continue;
					}
					if(port.bufferLevel < portBufferCapacity) {
						targetPort = port;
						break;
					}
				}
			}

			boolean hasSource = sourceNet != null || sourcePort != null;
			boolean hasTarget = targetNet != null || targetPort != null;
			if(!hasSource || !hasTarget) {
				seg.flowRate = 0;
				continue;
			}

			double sourceAvail = sourceNet != null ? sourceNet.fluidLevel : sourcePort.bufferLevel;
			double targetFree = targetNet != null ? (targetNet.tankCapacity - targetNet.fluidLevel) : (portBufferCapacity - targetPort.bufferLevel);
			double transferable = Math.min(pumpRate, Math.min(sourceAvail, targetFree));
			if(transferable <= 0) {
				seg.flowRate = 0;
				continue;
			}

			if(sourceNet != null) {
				sourceNet.fluidLevel = Math.max(0.0, sourceNet.fluidLevel - transferable);
				if(sourceNet.fluidLevel <= 0) {
					sourceNet.fluidId = 0;
					sourceNet.fluidLevel = 0;
				}
			} else {
				sourcePort.bufferLevel = Math.max(0.0, sourcePort.bufferLevel - transferable);
				if(sourcePort.bufferLevel <= 0) {
					sourcePort.bufferFluidId = 0;
					sourcePort.bufferLevel = 0;
				}
			}

			if(targetNet != null) {
				if(targetNet.fluidId == 0) {
					targetNet.fluidId = sourceFluidId;
				}
				targetNet.fluidLevel = Math.min(targetNet.tankCapacity, targetNet.fluidLevel + transferable);
			} else {
				if(targetPort.bufferFluidId == 0) {
					targetPort.bufferFluidId = sourceFluidId;
				}
				targetPort.bufferLevel = Math.min(portBufferCapacity, targetPort.bufferLevel + transferable);
			}

			seg.flowRate = (float) (transferable * ticksPerSecond);
			anyChange = true;
			if(debugLogger != null) {
				String srcDesc = sourceNet != null ? "net{" + sourceNet.memberIndices.size() + "}" : "port@" + sourcePort.blockIndex;
				String dstDesc = targetNet != null ? "net{" + targetNet.memberIndices.size() + "}" : "port@" + targetPort.blockIndex;
				debugLogger.accept("[FluidNetwork] Pump @ " + pumpIndex + " moved " + transferable + " units from " + srcDesc + " to " + dstDesc + " (flow: " + seg.flowRate + " L/s)");
			}
		}

		return anyChange;
	}
}


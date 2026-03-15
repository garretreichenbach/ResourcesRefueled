package videogoose.resourcesreorganized.logistics.fluid;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import videogoose.resourcesreorganized.element.ElementRegistry;
import videogoose.resourcesreorganized.manager.ConfigManager;
import videogoose.resourcesreorganized.systems.FluidSystemModule;

import java.util.*;
import java.util.function.Consumer;

public final class FluidTopologyMutationService {

	private FluidTopologyMutationService() {
	}

	public static boolean onPlace(long index, short blockType, SegmentController segmentController, Map<Long, FluidSystemModule.FluidTankSegment> tankSegments, Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments, Map<Long, FluidSystemModule.FluidPortSegment> portSegments, List<FluidSystemModule.FluidNetwork> networks, Consumer<String> debugLogger) {
		if(blockType == ElementRegistry.FLUID_TANK.getId()) {
			tankSegments.put(index, new FluidSystemModule.FluidTankSegment(index, blockType));
		} else if(blockType == ElementRegistry.FLUID_PORT.getId()) {
			portSegments.put(index, new FluidSystemModule.FluidPortSegment(index));
			return true;
		} else if(ElementRegistry.isPipe(blockType)) {
			if(!shouldTreatAsFunctionalPipe(index, blockType, segmentController, tankSegments, pipeSegments, networks)) {
				return false;
			}
			pipeSegments.put(index, new FluidSystemModule.FluidPipeSegment(index, blockType));
		} else {
			return false;
		}

		if(isNetworkDevice(blockType)) {
			if(debugLogger != null) {
				debugLogger.accept("[FluidNetwork] Placed device " + blockType + " @ " + index + " (boundary, not merged)");
			}
			return true;
		}

		Set<Long> neighbours = FluidTopologyUtils.faceAdjacentIndices(index);
		List<FluidSystemModule.FluidNetwork> adjacentNetworks = new ArrayList<>();
		for(FluidSystemModule.FluidNetwork net : networks) {
			for(long nb : neighbours) {
				if(pipeSegments.containsKey(nb) && isNetworkDevice(pipeSegments.get(nb).blockType)) {
					continue;
				}
				if(net.memberIndices.contains(nb)) {
					adjacentNetworks.add(net);
					break;
				}
			}
		}

		FluidSystemModule.FluidNetwork merged = new FluidSystemModule.FluidNetwork();
		merged.memberIndices.add(index);
		short mergedFluidId = 0;
		double maxFluidLevel = 0;

		for(FluidSystemModule.FluidNetwork net : adjacentNetworks) {
			merged.memberIndices.addAll(net.memberIndices);
			if(net.fluidLevel > maxFluidLevel) {
				maxFluidLevel = net.fluidLevel;
				mergedFluidId = net.fluidId;
			}

			if(mergedFluidId == 0 || net.fluidId == mergedFluidId || net.fluidId == 0) {
				merged.fluidLevel += net.fluidLevel;
			} else if(debugLogger != null) {
				debugLogger.accept("[FluidNetwork] Discarding " + net.fluidLevel + "L of fluid type " + net.fluidId + " due to incompatibility with dominant type " + mergedFluidId);
			}

			networks.remove(net);
		}

		merged.fluidId = mergedFluidId;
		recalculateNetworkCapacity(merged, tankSegments);
		merged.fluidLevel = Math.min(merged.fluidLevel, merged.tankCapacity);
		if(merged.fluidLevel <= 0) {
			merged.fluidId = 0;
			merged.fluidLevel = 0;
		}
		networks.add(merged);

		if(debugLogger != null) {
			debugLogger.accept("[FluidNetwork] Placed " + blockType + " @ " + index + " — networks: " + networks.size() + ", merged capacity: " + merged.tankCapacity + ", fluid: " + merged.fluidLevel + "L of type " + merged.fluidId);
		}
		return true;
	}

	public static boolean onRemove(long index, short blockType, Map<Long, FluidSystemModule.FluidTankSegment> tankSegments, Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments, Map<Long, FluidSystemModule.FluidPortSegment> portSegments, List<FluidSystemModule.FluidNetwork> networks, Consumer<String> debugLogger) {
		if(portSegments.remove(index) != null) {
			return true;
		}

		boolean wasTank = tankSegments.remove(index) != null;
		boolean wasPipe = !wasTank && pipeSegments.remove(index) != null;
		if(!wasTank && !wasPipe) {
			return false;
		}

		FluidSystemModule.FluidNetwork ownerNetwork = null;
		for(FluidSystemModule.FluidNetwork net : networks) {
			if(net.memberIndices.contains(index)) {
				ownerNetwork = net;
				break;
			}
		}
		if(ownerNetwork == null) {
			return true;
		}

		double savedFluid = ownerNetwork.fluidLevel;
		short savedFluidId = ownerNetwork.fluidId;
		networks.remove(ownerNetwork);
		ownerNetwork.memberIndices.remove(index);

		if(ownerNetwork.memberIndices.isEmpty()) {
			return true;
		}

		List<FluidSystemModule.FluidNetwork> newNetworks = floodPartition(ownerNetwork.memberIndices, pipeSegments);
		double totalNewCapacity = 0;
		for(FluidSystemModule.FluidNetwork net : newNetworks) {
			recalculateNetworkCapacity(net, tankSegments);
			totalNewCapacity += net.tankCapacity;
		}
		for(FluidSystemModule.FluidNetwork net : newNetworks) {
			double fraction = (totalNewCapacity > 0) ? net.tankCapacity / totalNewCapacity : 1.0 / newNetworks.size();
			net.fluidLevel = Math.min(savedFluid * fraction, net.tankCapacity);
			net.fluidId = savedFluidId;
			if(net.fluidLevel <= 0) {
				net.fluidId = 0;
				net.fluidLevel = 0;
			}
		}

		networks.addAll(newNetworks);
		if(debugLogger != null) {
			debugLogger.accept("[FluidNetwork] Removed " + blockType + " @ " + index + " — split into " + newNetworks.size() + " network(s).");
		}
		return true;
	}

	private static boolean shouldTreatAsFunctionalPipe(long index, short blockType, SegmentController segmentController, Map<Long, FluidSystemModule.FluidTankSegment> tankSegments, Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments, List<FluidSystemModule.FluidNetwork> networks) {
		short valveId = ElementRegistry.PIPE_VALVE.getId();
		short pumpId = ElementRegistry.PIPE_PUMP.getId();
		short filterId = ElementRegistry.PIPE_FILTER.getId();
		if(blockType == valveId || blockType == pumpId || blockType == filterId) {
			return true;
		}

		Set<Long> neighbours = FluidTopologyUtils.faceAdjacentIndices(index);
		for(long nb : neighbours) {
			if(tankSegments.containsKey(nb)) {
				return true;
			}

			FluidSystemModule.FluidPipeSegment seg = pipeSegments.get(nb);
			if(seg != null && (seg.blockType == valveId || seg.blockType == pumpId || seg.blockType == filterId)) {
				return true;
			}

			for(FluidSystemModule.FluidNetwork net : networks) {
				if(net.memberIndices.contains(nb)) {
					if(net.tankCapacity > 0) {
						return true;
					}
					for(long member : net.memberIndices) {
						FluidSystemModule.FluidPipeSegment ps = pipeSegments.get(member);
						if(ps != null && (ps.blockType == valveId || ps.blockType == pumpId || ps.blockType == filterId)) {
							return true;
						}
					}
				}
			}

			SegmentPiece piece = segmentController.getSegmentBuffer().getPointUnsave(nb);
			if(piece != null && piece.getType() != Element.TYPE_NONE && ElementRegistry.canInteractWithFluid(piece.getType())) {
				return true;
			}
		}
		return false;
	}

	private static List<FluidSystemModule.FluidNetwork> floodPartition(Set<Long> members, Map<Long, FluidSystemModule.FluidPipeSegment> pipeSegments) {
		Set<Long> unvisited = new HashSet<Long>(members);
		List<FluidSystemModule.FluidNetwork> result = new ArrayList<FluidSystemModule.FluidNetwork>();

		while(!unvisited.isEmpty()) {
			FluidSystemModule.FluidNetwork component = new FluidSystemModule.FluidNetwork();
			Queue<Long> queue = new ArrayDeque<Long>();
			long seed = unvisited.iterator().next();
			FluidSystemModule.FluidPipeSegment seedSeg = pipeSegments.get(seed);
			if(seedSeg != null && isNetworkDevice(seedSeg.blockType)) {
				unvisited.remove(seed);
				continue;
			}
			queue.add(seed);
			unvisited.remove(seed);

			while(!queue.isEmpty()) {
				long current = queue.poll();
				component.memberIndices.add(current);
				for(long nb : FluidTopologyUtils.faceAdjacentIndices(current)) {
					if(unvisited.contains(nb)) {
						FluidSystemModule.FluidPipeSegment nbSeg = pipeSegments.get(nb);
						if(nbSeg != null && isNetworkDevice(nbSeg.blockType)) {
							continue;
						}
						unvisited.remove(nb);
						queue.add(nb);
					}
				}
			}
			if(!component.memberIndices.isEmpty()) {
				result.add(component);
			}
		}

		return result;
	}

	public static void recalculateNetworkCapacity(FluidSystemModule.FluidNetwork net, Map<Long, FluidSystemModule.FluidTankSegment> tankSegments) {
		int tankCount = 0;
		for(long idx : net.memberIndices) {
			if(tankSegments.containsKey(idx)) {
				tankCount++;
			}
		}
		net.tankCapacity = tankCount * ConfigManager.getCapacityPerTank();
	}

	private static boolean isNetworkDevice(short blockType) {
		return blockType == ElementRegistry.PIPE_PUMP.getId() || blockType == ElementRegistry.PIPE_VALVE.getId() || blockType == ElementRegistry.PIPE_FILTER.getId();
	}
}


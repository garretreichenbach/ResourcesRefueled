package videogoose.resourcesreorganized.logistics.item.planner;

import videogoose.resourcesreorganized.logistics.item.graph.*;
import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;

import java.util.*;

public final class ItemRoutePlanner {

	private final ItemLogisticsGraph graph;

	public ItemRoutePlanner(ItemLogisticsGraph graph) {
		this.graph = graph;
	}

	public Optional<ItemRoute> planRoute(ItemTransferRequest request) {
		Optional<List<String>> pathOpt = constrainedPath(request);
		if(!pathOpt.isPresent()) {
			return Optional.empty();
		}
		List<String> path = pathOpt.get();
		int throughput = Integer.MAX_VALUE;
		boolean usesPump = false;
		for(int i = 0; i < path.size(); i++) {
			String nodeId = path.get(i);
			ItemNode node = graph.getNode(nodeId).orElse(null);
			throughput = Math.min(throughput, node != null ? node.getThroughputPerTick() : 1);
			if(node != null && node.getType() == ItemNodeType.PUMP) {
				usesPump = true;
			}
			if(i < path.size() - 1) {
				String next = path.get(i + 1);
				throughput = Math.min(throughput, graph.getEdge(nodeId, next).map(ItemEdge::getCapacityPerTick).orElse(1));
			}
		}
		if(throughput == Integer.MAX_VALUE) {
			throughput = 1;
		}
		return Optional.of(new ItemRoute(path, throughput, request.getTransportFamily(), request.getChannel(), usesPump));
	}

	public Optional<ItemRoute> planRoute(String sourceNodeId, String destinationNodeId) {
		ItemTransferRequest fallback = new ItemTransferRequest(sourceNodeId, destinationNodeId, (short) 0, 0, 1, 0, true);
		return planRoute(fallback);
	}

	private Optional<List<String>> constrainedPath(ItemTransferRequest request) {
		String sourceNodeId = request.getSourceNodeId();
		String destinationNodeId = request.getDestinationNodeId();
		if(!graph.getNode(sourceNodeId).isPresent() || !graph.getNode(destinationNodeId).isPresent()) {
			return Optional.empty();
		}
		ItemNode sourceNode = graph.getNode(sourceNodeId).get();
		ItemNode destinationNode = graph.getNode(destinationNodeId).get();
		if(!isEndpointAllowed(sourceNode, true, request) || !isEndpointAllowed(destinationNode, false, request)) {
			return Optional.empty();
		}

		ArrayDeque<String> queue = new ArrayDeque<>();
		Map<String, String> previous = new HashMap<>();
		Set<String> visited = new HashSet<>();

		queue.add(sourceNodeId);
		visited.add(sourceNodeId);

		while(!queue.isEmpty()) {
			String current = queue.removeFirst();
			if(current.equals(destinationNodeId)) {
				List<String> path = reconstructPath(previous, destinationNodeId);
				if(isPathValidForRequest(path, request)) {
					return Optional.of(path);
				}
				continue;
			}

			for(ItemEdge edge : graph.getOutgoingEdges(current)) {
				if(!isEdgeAllowed(edge, request)) {
					continue;
				}
				String next = edge.getToNodeId();
				if(!graph.getNode(next).isPresent()) {
					continue;
				}
				if(!isNodeAllowed(graph.getNode(next).get(), request)) {
					continue;
				}
				if(visited.add(next)) {
					previous.put(next, current);
					queue.addLast(next);
				}
			}
		}

		return Optional.empty();
	}

	private static List<String> reconstructPath(Map<String, String> previous, String destinationNodeId) {
		LinkedList<String> path = new LinkedList<>();
		String cursor = destinationNodeId;
		path.addFirst(cursor);
		while(previous.containsKey(cursor)) {
			cursor = previous.get(cursor);
			path.addFirst(cursor);
		}
		return path;
	}

	private boolean isPathValidForRequest(List<String> path, ItemTransferRequest request) {
		TransportFamily family = request.getTransportFamily();
		if(family == TransportFamily.TUBE && request.isRequirePump()) {
			for(String nodeId : path) {
				ItemNode node = graph.getNode(nodeId).orElse(null);
				if(node != null && node.getType() == ItemNodeType.PUMP) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	private static boolean isEndpointAllowed(ItemNode node, boolean source, ItemTransferRequest request) {
		boolean endpointRequiresPort = source ? request.isSourceRequiresInventoryPort() : request.isDestinationRequiresInventoryPort();
		if(endpointRequiresPort) {
			return node.getType() == ItemNodeType.INVENTORY_PORT;
		}
		if(node.getType() == ItemNodeType.INVENTORY_PORT) {
			return true;
		}
		if(request.getTransportFamily() == TransportFamily.CONVEYOR && request.isAllowDirectInventoryAdjacency()) {
			if(source) {
				return node.isExtractionCapable();
			}
			return true;
		}
		return false;
	}

	private static boolean isEdgeAllowed(ItemEdge edge, ItemTransferRequest request) {
		if(!edge.supportsChannel(request.getChannel())) {
			return false;
		}
		TransportFamily family = request.getTransportFamily();
		if(family == TransportFamily.CONVEYOR) {
			if(edge.getTransportFamily() == TransportFamily.TUBE) {
				return false;
			}
			if(edge.isVertical()) {
				return false;
			}
		}
		if(family == TransportFamily.TUBE) {
			if(edge.getTransportFamily() == TransportFamily.CONVEYOR) {
				return false;
			}
			return !edge.isVertical() || request.isAllowVertical();
		}
		return true;
	}

	private boolean isNodeAllowed(ItemNode node, ItemTransferRequest request) {
		if(!node.supportsChannel(request.getChannel())) {
			return false;
		}
		TransportFamily family = request.getTransportFamily();
		if(family == TransportFamily.CONVEYOR && node.getTransportFamily() == TransportFamily.TUBE) {
			return false;
		}
		return family != TransportFamily.TUBE || node.getTransportFamily() != TransportFamily.CONVEYOR;
	}
}


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
		for(int i = 0; i < path.size(); i++) {
			String nodeId = path.get(i);
			ItemNode node = graph.getNode(nodeId).orElse(null);
			throughput = Math.min(throughput, node != null ? node.getThroughputPerTick() : 1);
			if(i < path.size() - 1) {
				String next = path.get(i + 1);
				throughput = Math.min(throughput, graph.getEdge(nodeId, next).map(ItemEdge::getCapacityPerTick).orElse(1));
			}
		}
		if(throughput == Integer.MAX_VALUE) {
			throughput = 1;
		}
		return Optional.of(new ItemRoute(path, throughput, request.transportFamily(), request.channel()));
	}

	public Optional<ItemRoute> planRoute(String sourceNodeId, String destinationNodeId) {
		ItemTransferRequest fallback = new ItemTransferRequest(sourceNodeId, destinationNodeId, (short) 0, 0, 1, 0, true);
		return planRoute(fallback);
	}

	private Optional<List<String>> constrainedPath(ItemTransferRequest request) {
		String sourceNodeId = request.sourceNodeId();
		String destinationNodeId = request.destinationNodeId();
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
				return Optional.of(reconstructPath(previous, destinationNodeId));
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

	private static boolean isEndpointAllowed(ItemNode node, boolean source, ItemTransferRequest request) {
		boolean endpointRequiresPort = source ? request.sourceRequiresInventoryPort() : request.destinationRequiresInventoryPort();
		if(endpointRequiresPort) {
			return node.getType() == ItemNodeType.INVENTORY_PORT;
		}
		if(node.getType() == ItemNodeType.INVENTORY_PORT) {
			return true;
		}
		if(request.transportFamily() == TransportFamily.CONVEYOR && request.allowDirectInventoryAdjacency()) {
			if(source) {
				return node.isExtractionCapable();
			}
			return true;
		}
		return false;
	}

	// Conveyors are the only transport family, and they carry items vertically themselves (BeltShape
	// .TURN_UP), so neither family mixing nor a vertical restriction applies any more — a channel
	// mismatch is all that can rule an edge or node out.
	private static boolean isEdgeAllowed(ItemEdge edge, ItemTransferRequest request) {
		return edge.supportsChannel(request.channel());
	}

	private static boolean isNodeAllowed(ItemNode node, ItemTransferRequest request) {
		return node.supportsChannel(request.channel());
	}
}


package videogoose.resourcesreorganized.logistics.item.graph;

import java.util.*;

public final class ItemLogisticsGraph {

	private final Map<String, ItemNode> nodes = new HashMap<>();
	private final Map<String, List<ItemEdge>> outgoingEdges = new HashMap<>();

	public void registerNode(ItemNode node) {
		nodes.put(node.getId(), node);
		outgoingEdges.putIfAbsent(node.getId(), new ArrayList<>());
	}

	public void removeNode(String nodeId) {
		nodes.remove(nodeId);
		outgoingEdges.remove(nodeId);
		for(List<ItemEdge> edges : outgoingEdges.values()) {
			edges.removeIf(edge -> edge.getToNodeId().equals(nodeId));
		}
	}

	public void connect(ItemEdge edge) {
		if(!nodes.containsKey(edge.getFromNodeId()) || !nodes.containsKey(edge.getToNodeId())) {
			return;
		}
		List<ItemEdge> edges = outgoingEdges.computeIfAbsent(edge.getFromNodeId(), key -> new ArrayList<>());
		edges.removeIf(existing -> existing.getToNodeId().equals(edge.getToNodeId()));
		edges.add(edge);
	}

	public void disconnect(String fromNodeId, String toNodeId) {
		List<ItemEdge> edges = outgoingEdges.get(fromNodeId);
		if(edges != null) {
			edges.removeIf(existing -> existing.getToNodeId().equals(toNodeId));
		}
	}

	public Optional<List<String>> shortestPath(String sourceNodeId, String destinationNodeId) {
		if(!nodes.containsKey(sourceNodeId) || !nodes.containsKey(destinationNodeId)) {
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
			for(ItemEdge edge : outgoingEdges.getOrDefault(current, Collections.emptyList())) {
				String next = edge.getToNodeId();
				if(visited.add(next)) {
					previous.put(next, current);
					queue.addLast(next);
				}
			}
		}
		return Optional.empty();
	}

	public Optional<ItemNode> getNode(String nodeId) {
		return Optional.ofNullable(nodes.get(nodeId));
	}

	public Optional<ItemEdge> getEdge(String fromNodeId, String toNodeId) {
		for(ItemEdge edge : outgoingEdges.getOrDefault(fromNodeId, Collections.emptyList())) {
			if(edge.getToNodeId().equals(toNodeId)) {
				return Optional.of(edge);
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
}


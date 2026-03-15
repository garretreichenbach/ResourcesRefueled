package videogoose.resourcesreorganized.logistics.item.planner;

import videogoose.resourcesreorganized.logistics.item.graph.ItemEdge;
import videogoose.resourcesreorganized.logistics.item.graph.ItemLogisticsGraph;
import videogoose.resourcesreorganized.logistics.item.graph.ItemNode;

import java.util.List;
import java.util.Optional;

public final class ItemRoutePlanner {

	private final ItemLogisticsGraph graph;

	public ItemRoutePlanner(ItemLogisticsGraph graph) {
		this.graph = graph;
	}

	public Optional<ItemRoute> planRoute(String sourceNodeId, String destinationNodeId) {
		Optional<List<String>> pathOpt = graph.shortestPath(sourceNodeId, destinationNodeId);
		if(!pathOpt.isPresent()) {
			return Optional.empty();
		}
		List<String> path = pathOpt.get();
		int throughput = Integer.MAX_VALUE;
		for(int i = 0; i < path.size(); i++) {
			String nodeId = path.get(i);
			throughput = Math.min(throughput, graph.getNode(nodeId).map(ItemNode::getThroughputPerTick).orElse(1));
			if(i < path.size() - 1) {
				String next = path.get(i + 1);
				throughput = Math.min(throughput, graph.getEdge(nodeId, next).map(ItemEdge::getCapacityPerTick).orElse(1));
			}
		}
		if(throughput == Integer.MAX_VALUE) {
			throughput = 1;
		}
		return Optional.of(new ItemRoute(path, throughput));
	}
}


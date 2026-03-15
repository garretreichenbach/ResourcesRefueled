package videogoose.resourcesreorganized.logistics.item.runtime;

import videogoose.resourcesreorganized.logistics.item.policy.ItemEndpointPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemEndpointPolicyRegistry {

	private static final Map<String, ItemEndpointPolicy> policiesByNodeId = new ConcurrentHashMap<String, ItemEndpointPolicy>();

	private ItemEndpointPolicyRegistry() {
	}

	public static void setPolicy(String nodeId, ItemEndpointPolicy policy) {
		if(nodeId == null || policy == null) {
			return;
		}
		policiesByNodeId.put(nodeId, policy);
	}

	public static void clearPolicy(String nodeId) {
		if(nodeId == null) {
			return;
		}
		policiesByNodeId.remove(nodeId);
	}

	public static ItemEndpointPolicy getPolicy(String nodeId) {
		if(nodeId == null) {
			return ItemEndpointPolicy.none();
		}
		return policiesByNodeId.getOrDefault(nodeId, ItemEndpointPolicy.none());
	}

	public static boolean requiresInventoryPort(String nodeId) {
		return getPolicy(nodeId).requiresInventoryPort();
	}
}


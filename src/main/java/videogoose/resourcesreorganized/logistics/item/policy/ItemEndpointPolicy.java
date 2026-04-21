package videogoose.resourcesreorganized.logistics.item.policy;

public record ItemEndpointPolicy(ItemEndpointPolicyType type, int channelMask) {

	private static final ItemEndpointPolicy NONE = new ItemEndpointPolicy(ItemEndpointPolicyType.NONE, -1);

	public boolean requiresInventoryPort() {
		return type != null && type != ItemEndpointPolicyType.NONE;
	}

	public static ItemEndpointPolicy none() {
		return NONE;
	}
}


package videogoose.resourcesreorganized.logistics.item.policy;

public final class ItemEndpointPolicy {

	private static final ItemEndpointPolicy NONE = new ItemEndpointPolicy(ItemEndpointPolicyType.NONE, -1);

	private final ItemEndpointPolicyType type;
	private final int channelMask;

	public ItemEndpointPolicy(ItemEndpointPolicyType type, int channelMask) {
		this.type = type;
		this.channelMask = channelMask;
	}

	public ItemEndpointPolicyType getType() {
		return type;
	}

	public int getChannelMask() {
		return channelMask;
	}

	public boolean requiresInventoryPort() {
		return type != null && type != ItemEndpointPolicyType.NONE;
	}

	public static ItemEndpointPolicy none() {
		return NONE;
	}
}


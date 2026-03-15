package videogoose.resourcesreorganized.logistics.item.api;

import videogoose.resourcesreorganized.logistics.item.model.ItemTransferRequest;

/**
 * Entry point used by future inventory mixin hooks.
 */
@FunctionalInterface
public interface ItemMutationIngress {

	boolean tryRouteMutation(ItemTransferRequest request);
}


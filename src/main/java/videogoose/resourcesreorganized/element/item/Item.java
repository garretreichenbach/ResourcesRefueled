package videogoose.resourcesreorganized.element.item;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.ElementInterface;

public abstract class Item implements ElementInterface {

	protected String name;
	protected ElementInformation itemInfo;

	protected Item(String name) {
		this.name = name;
	}

	@Override
	public void initData() {
		itemInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[6]);
		itemInfo.placable = false; // Items are not placable in the world like blocks, so set this to false.
	}

	@Override
	public short getId() {
		return itemInfo.id;
	}

	@Override
	public ElementInformation getInfo() {
		return itemInfo;
	}
}
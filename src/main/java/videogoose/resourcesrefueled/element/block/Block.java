package videogoose.resourcesrefueled.element.block;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementInterface;

public abstract class Block implements ElementInterface {

	protected String name;
	protected ElementInformation blockInfo;

	protected Block(String name) {
		this.name = name;
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, new short[6]);
	}

	@Override
	public short getId() {
		return blockInfo.id;
	}

	@Override
	public ElementInformation getInfo() {
		return blockInfo;
	}
}
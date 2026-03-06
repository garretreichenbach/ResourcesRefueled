package videogoose.resourcesreorganized.element.block;

import org.schema.game.common.data.element.ElementInformation;
import videogoose.resourcesreorganized.element.ElementInterface;

public abstract class Block implements ElementInterface {

	protected String name;
	protected ElementInformation blockInfo;

	protected Block(String name) {
		this.name = name;
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
package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

/**
 * The Heliogen Tank is a pressurized storage block for Heliogen fuel.
 * Multiple tank blocks linked together form a multiblock storage unit.
 * <p>
 * IMPORTANT: Fuel level tracking is handled by the custom ManagerContainerModule
 * added separately. This class only defines the block's physical/game properties.
 * <p>
 * When a tank block is destroyed while containing fuel, the SegmentPieceKillEvent
 * listener triggers an explosion scaled to the stored fuel amount.
 */
public class HeliogenTank extends Block {

	/** Heliogen units stored per tank block. Exposed for use by the manager module and kill listener. */
	public static final int CAPACITY_PER_BLOCK = 50;

	public HeliogenTank() {
		super("Heliogen Tank");
	}

	@Override
	public void initData() {
		ElementInformation vanillaFactory = ElementKeyMap.getInfo(ElementKeyMap.FACTORY_BASIC_ID);
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), "Heliogen Tank", vanillaFactory.getTextureIds());
		blockInfo.description = "A pressurized block for storing bulk Heliogen fuel. Each block holds " + CAPACITY_PER_BLOCK + " units.\nLink multiple tank blocks together to form a larger storage system.\nWARNING: Tank blocks containing fuel are extremely volatile. Destroying a loaded tank will trigger an explosion proportional to stored fuel — handle with care during construction and combat.";
		blockInfo.price = (int) (vanillaFactory.price * 2);
		blockInfo.mass = vanillaFactory.mass * 1.5f;
		// Dense but not large — tanks are compact pressure vessels.
		blockInfo.volume = vanillaFactory.volume * 0.5f;
		// High HP: tanks are reinforced, but when they go, they go.
		blockInfo.maxHitPointsFull = vanillaFactory.maxHitPointsFull * 2;
		blockInfo.shoppable = true;
		blockInfo.canActivate = false; // Activated via the manager module, not directly.
		blockInfo.systemBlock = true;
		blockInfo.type = vanillaFactory.type;
	}

	@Override
	public void postInitData() {
		// No recipe or multiblock wiring needed here — the manager module handles linking.
	}

	@Override
	public void initResources() {
		// TODO: custom tank textures (sealed metal cylinder aesthetic)
	}
}

package videogoose.resourcesreorganized.element.block.inventory;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.Block;
import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;
import videogoose.resourcesreorganized.logistics.item.belt.ConveyorArrowFacing;

/**
 * Shared definition for the conveyor belt shapes.
 * <p>
 * Every {@link BeltShape} is its own block id carrying one fixed model, so the engine's stock draw
 * path renders the right mesh without inspecting neighbours. The shapes all declare the same
 * {@code inventoryGroup}, which the engine uses to merge them into a single inventory slot with one
 * sub-slot per shape &mdash; the player scrolls sub-slots to pick which bend to place.
 */
public abstract class ConveyorBeltBlock extends Block {

	/**
	 * Inventory multi-slot key shared by every belt shape. Blocks with a matching group merge into one
	 * slot ({@code InventorySlot.isMultiSlotCompatibleTo}); the sub-slot then selects the block placed.
	 * Deliberately narrower than the "ItemTransport" group used by tubes and pumps, so the belt stack
	 * only cycles through belt shapes.
	 */
	private static final String INVENTORY_GROUP = "ConveyorBelt";

	/** Keeps the "no facing arrow API" note to one line rather than one per belt shape. */
	private static boolean facingApiWarned;

	private final BeltShape shape;
	private final String description;

	protected ConveyorBeltBlock(String name, BeltShape shape, String description) {
		super(name);
		this.shape = shape;
		this.description = description;
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newElement(ResourcesReorganized.getInstance(), name, new short[] {0, 0, 0, 0, 0, 0});
		blockInfo.type = Blocks.PIPE.getInfo().type;
		blockInfo.mass = Blocks.PIPE.getInfo().mass * 1.3f;
		blockInfo.price = Blocks.PIPE.getInfo().price * 2;
		blockInfo.volume = Blocks.PIPE.getInfo().volume * 1.3f;
		blockInfo.description = description;
		blockInfo.canActivate = true;
		blockInfo.shoppable = true;
		blockInfo.inventoryGroup = INVENTORY_GROUP;
		// Collision height follows the shape's mesh: the flat shapes are thin floor blocks and collide as
		// a 1/4 slab, the vertical bend fills its cell and collides as a cube. The physics pipeline reads
		// ElementInformation.getSlab(orientation) independently of blockStyle, so the custom mesh still
		// renders either way. NOTE: slab != 0 also keeps a block out of the vanilla shape radial menu,
		// which only indexes slab-0 variants — the inventory multi-slot above is what groups these.
		blockInfo.slab = shape.collisionSlab();

		BlockConfig.assignLod(blockInfo, ResourcesReorganized.getInstance(), shape.modelName(), null);
		// assignLod leaves blockStyle = SPRITE; a directional belt needs the full 24-orientation set
		// (rail-like) so it can be rotated to face any flow direction and the model rotates with it.
		blockInfo.blockStyle = BlockStyle.NORMAL24;
	}

	@Override
	public void postInitData() {
		// Ids are handed out during initData, so the type -> shape lookup can only be filled now.
		BeltShape.register(getId(), shape);
		try {
			// Point the vanilla build-mode arrow at this shape's output face, which for an up turn is
			// its top rather than its forward. ConveyorArrowFacing holds the only reference to
			// BlockFacingArrowAPI and loads here, so on a jar without that API this is a skipped
			// override, not a failed block registration — the mod's own build preview arrows still show
			// the true flow either way.
			ConveyorArrowFacing.register(getId(), shape);
		} catch(Throwable missingFacingApi) {
			if(!facingApiWarned) {
				facingApiWarned = true;
				ResourcesReorganized.getInstance().logDebug(
						"Build-mode facing arrow API not present; conveyor output direction comes from the "
								+ "mod's own build preview instead.");
			}
		}
	}

	@Override
	public void initResources() {
	}
}

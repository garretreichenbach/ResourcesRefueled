package videogoose.resourcesrefueled.element.block.systems;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.Block;

import java.util.function.Supplier;

/**
 * A generic pressurised fluid storage block.
 *
 * FluidTank is not Heliogen-specific — any fluid type can be stored in one by
 * instantiating it with the appropriate {@code fluidTypeId} and {@code displayName}.
 * The Heliogen tank in the registry is simply {@code new FluidTank("Heliogen Tank", ...)}
 * with {@code ElementRegistry.HELIOGEN_CANISTER_FILLED} as the stored fluid.
 *
 * <h3>Fluid tracking</h3>
 * Actual fluid level is tracked by the custom ManagerContainerModule you are
 * implementing separately. This class only defines the block's physical properties
 * and exposes the metadata the module needs ({@link #capacityPerBlock},
 * {@link #volatileOnDestruction}).
 *
 * <h3>Explosion</h3>
 * When {@link #volatileOnDestruction} is true and a block is destroyed while
 * containing fluid, the {@code SegmentPieceKillEvent} listener reads
 * {@link #capacityPerBlock} and the stored level from the manager module to
 * calculate explosion yield.
 */
public class FluidTank extends Block {

	/** How many fluid units this block contributes to the multiblock's total capacity. */
	public final int capacityPerBlock;

	/**
	 * The element ID of the fluid stored in this tank type.
	 * Set in {@link #postInitData()} after all elements are registered.
	 * Use a lambda supplier so the ID can be resolved lazily.
	 */
	private final Supplier<Short> fluidIdSupplier;

	/** Cached after postInitData resolves the supplier. */
	private short resolvedFluidId = -1;

	/**
	 * If true, destroying a loaded block triggers an explosion via
	 * SegmentPieceKillEvent proportional to stored fluid × ConfigManager.getTankExplosionYieldPerUnit().
	 */
	public final boolean volatileOnDestruction;

	/**
	 * Full constructor.
	 *
	 * @param displayName          In-game block name (e.g. "Heliogen Tank").
	 * @param fluidIdSupplier      Lazy supplier returning the element ID of the stored fluid.
	 *                             Use a lambda, e.g. {@code () -> ElementRegistry.HELIOGEN_CANISTER_FILLED.getId()},
	 *                             so it resolves after element registration.
	 * @param capacityPerBlock     Fluid units stored per block.
	 * @param volatileOnDestruction Whether destroying a loaded block explodes.
	 */
	public FluidTank(String displayName, Supplier<Short> fluidIdSupplier, int capacityPerBlock, boolean volatileOnDestruction) {
		super(displayName);
		this.fluidIdSupplier = fluidIdSupplier;
		this.capacityPerBlock = capacityPerBlock;
		this.volatileOnDestruction = volatileOnDestruction;
	}

	/** Convenience constructor with default capacity and volatility. */
	public FluidTank(String displayName, Supplier<Short> fluidIdSupplier) {
		this(displayName, fluidIdSupplier, 50, true);
	}

	// -------------------------------------------------------------------------

	@Override
	public void initData() {
		ElementInformation vanillaFactory = ElementKeyMap.getInfo(ElementKeyMap.FACTORY_BASIC_ID);
		blockInfo = BlockConfig.newElement(ResourcesRefueled.getInstance(), name, vanillaFactory.getTextureIds());
		blockInfo.description = buildDescription();
		blockInfo.price = (int) (vanillaFactory.price * 2);
		blockInfo.mass = vanillaFactory.mass * 1.5f;
		blockInfo.volume = vanillaFactory.volume * 0.5f;   // compact pressure vessel
		blockInfo.maxHitPointsFull = vanillaFactory.maxHitPointsFull * 2; // reinforced, but volatile
		blockInfo.shoppable = true;
		blockInfo.canActivate = false; // activated through the manager module
		blockInfo.systemBlock = true;
		blockInfo.type = vanillaFactory.type;
	}

	@Override
	public void postInitData() {
		resolvedFluidId = fluidIdSupplier.get();
	}

	@Override
	public void initResources() {
		// TODO: custom tank textures (sealed metal cylinder aesthetic)
	}

	// -------------------------------------------------------------------------
	// Accessors for the manager module and kill listener
	// -------------------------------------------------------------------------

	/**
	 * Returns the element ID of the fluid this tank stores.
	 * Only valid after {@link #postInitData()} has been called.
	 */
	public short getFluidId() {
		return resolvedFluidId;
	}

	// -------------------------------------------------------------------------

	private String buildDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append("A pressurised storage block for bulk fluid. Each block holds ").append(capacityPerBlock).append(" units.\n").append("Place multiple blocks adjacent to form a larger tank with combined capacity. Connect with pipes to fill or drain.");
		if(volatileOnDestruction) {
			sb.append("\nWARNING: Destroying a loaded tank triggers an explosion proportional to stored fluid volume. Handle with care during construction and combat.");
		}
		return sb.toString();
	}
}


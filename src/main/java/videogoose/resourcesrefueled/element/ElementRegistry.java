package videogoose.resourcesrefueled.element;

import api.common.GameCommon;
import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.element.ElementInformation;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.block.pipes.PipeFilter;
import videogoose.resourcesrefueled.element.block.pipes.PipePump;
import videogoose.resourcesrefueled.element.block.pipes.PipeValve;
import videogoose.resourcesrefueled.element.block.systems.FluidTank;
import videogoose.resourcesrefueled.element.block.systems.HeliogenCondenser;
import videogoose.resourcesrefueled.element.block.systems.HeliogenRefinery;
import videogoose.resourcesrefueled.element.item.HeliogenCanisterEmpty;
import videogoose.resourcesrefueled.element.item.HeliogenCanisterFilled;
import videogoose.resourcesrefueled.element.item.HeliogenPlasma;

/**
 * Central registry that defines and registers all mod blocks/chambers/weapons/etc. by implementing the ElementInterface and adding them to the enum.
 */
public enum ElementRegistry {
	//Items
	HELIOGEN_PLASMA(new HeliogenPlasma()),
	HELIOGEN_CANISTER_EMPTY(new HeliogenCanisterEmpty()),
	HELIOGEN_CANISTER_FILLED(new HeliogenCanisterFilled()),

	//Pipe network blocks
	PIPE_VALVE(new PipeValve()),
	PIPE_FILTER(new PipeFilter()),
	PIPE_PUMP(new PipePump()),
	FLUID_TANK(new FluidTank()),

	//Heliogen production blocks
	HELIOGEN_CONDENSER(new HeliogenCondenser()),
	HELIOGEN_REFINERY(new HeliogenRefinery());

	public static ElementInformation MAGMATIC_EXTRACTOR;
	public static ElementInformation VAPOR_SIPHON;
	public final ElementInterface elementInterface;

	ElementRegistry(ElementInterface elementInterface) {
		this.elementInterface = elementInterface;
	}

	public static void registerElements() {
		for(ElementRegistry registry : values()) {
			registry.elementInterface.initData();
		}
		ResourcesRefueled.getInstance().logDebug("Initialized element data for " + values().length + " elements");

		for(ElementRegistry registry : values()) {
			registry.elementInterface.postInitData();
		}
		ResourcesRefueled.getInstance().logDebug("Initialized element data for " + values().length + " elements");

		for(ElementRegistry registry : values()) {
			if(!GameCommon.isDedicatedServer()) {
				registry.elementInterface.initResources();
			}
		}
		if(!GameCommon.isDedicatedServer()) {
			ResourcesRefueled.getInstance().logDebug("Initialized element resources for " + values().length + " elements");
		}

		for(ElementRegistry registry : values()) {
			BlockConfig.add(registry.getInfo());
		}
		ResourcesRefueled.getInstance().logDebug("Initialized element resources for " + values().length + " elements");
	}

	public static void doOverwrites() {
		MAGMATIC_EXTRACTOR.controlling.remove(Blocks.FACTORY_ENHANCER.getId());
		Blocks.FACTORY_ENHANCER.getInfo().controlledBy.remove(MAGMATIC_EXTRACTOR.id);

		VAPOR_SIPHON.controlling.remove(Blocks.FACTORY_ENHANCER.getId());
		Blocks.FACTORY_ENHANCER.getInfo().controlledBy.remove(VAPOR_SIPHON.id);
	}

	private static ElementInformation getInfoByName(String name) {
		for(ElementInformation info : BlockConfig.getElements()) {
			if(info.getName().equals(name)) {
				return info;
			}
		}
		throw new IllegalStateException("Element with name '" + name + "' not found");
	}

	public static boolean isPipe(short id) {
		return id == Blocks.PIPE.getId() || id == Blocks.PIPE_CROSS.getId() || id == Blocks.PIPE_TEE.getId() || id == Blocks.PIPE_ELBOW.getId() || id == PIPE_VALVE.getId() || id == PIPE_FILTER.getId() || id == PIPE_PUMP.getId();
	}

	public static boolean canInteractWithFluid(short id) {
		return id == FLUID_TANK.getId() || id == HELIOGEN_CONDENSER.getId() || id == HELIOGEN_REFINERY.getId() || id == MAGMATIC_EXTRACTOR.id || id == VAPOR_SIPHON.id;
	}

	public static void registerRRSBlocks() {
		MAGMATIC_EXTRACTOR = getInfoByName("Magmatic Extractor");
		VAPOR_SIPHON = getInfoByName("Vapor Siphon");
	}

	public ElementInformation getInfo() {
		return elementInterface.getInfo();
	}

	public short getId() {
		return getInfo().id;
	}
}
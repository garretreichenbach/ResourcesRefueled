package videogoose.resourcesreorganized.element;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.element.ElementInformation;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.element.block.inventory.ConveyorBelt;
import videogoose.resourcesreorganized.element.block.inventory.ItemPump;
import videogoose.resourcesreorganized.element.block.inventory.ItemTube;
import videogoose.resourcesreorganized.element.block.pipes.PipeFilter;
import videogoose.resourcesreorganized.element.block.pipes.PipePump;
import videogoose.resourcesreorganized.element.block.pipes.PipeValve;
import videogoose.resourcesreorganized.element.block.systems.FluidPort;
import videogoose.resourcesreorganized.element.block.systems.FluidTank;
import videogoose.resourcesreorganized.element.block.systems.HeliogenCondenser;
import videogoose.resourcesreorganized.element.block.systems.HeliogenRefinery;
import videogoose.resourcesreorganized.element.item.FluidCanister;
import videogoose.resourcesreorganized.element.item.HeliogenPlasma;
import videogoose.resourcesreorganized.manager.ConfigManager;

/**
 * Central registry that defines and registers all mod blocks/chambers/weapons/etc. by implementing the ElementInterface and adding them to the enum.
 */
public enum ElementRegistry {
	//Items
	HELIOGEN_PLASMA(new HeliogenPlasma()),
	FLUID_CANISTER(new FluidCanister()),

	//Item network blocks
	CONVEYOR_BELT(new ConveyorBelt()),
	ITEM_TUBE(new ItemTube()),
	ITEM_PUMP(new ItemPump()),

	//Pipe network blocks
	PIPE_VALVE(new PipeValve()),
	PIPE_FILTER(new PipeFilter()),
	PIPE_PUMP(new PipePump()),
	FLUID_TANK(new FluidTank()),
	FLUID_PORT(new FluidPort()),

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
		ResourcesReorganized.getInstance().logDebug("Initialized element data for " + values().length + " elements");

		for(ElementRegistry registry : values()) {
			registry.elementInterface.postInitData();
		}
		ResourcesReorganized.getInstance().logDebug("Initialized element data for " + values().length + " elements");

		for(ElementRegistry registry : values()) {
			registry.elementInterface.initResources();
		}
		ResourcesReorganized.getInstance().logDebug("Initialized element resources for " + values().length + " elements");

		for(ElementRegistry registry : values()) {
			BlockConfig.add(registry.getInfo());
		}
		ResourcesReorganized.getInstance().logDebug("Initialized element resources for " + values().length + " elements");
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
		return id == FLUID_TANK.getId() || id == FLUID_PORT.getId() || id == HELIOGEN_CONDENSER.getId() || id == HELIOGEN_REFINERY.getId() || id == MAGMATIC_EXTRACTOR.id || id == VAPOR_SIPHON.id;
	}

	public static boolean isFluidTransportPipeInteractable(short id) {
		return id == PIPE_VALVE.getId() || id == PIPE_FILTER.getId() || id == PIPE_PUMP.getId();
	}

	public static boolean isContainer(short id) {
		return id == FLUID_CANISTER.getId() || id == FLUID_TANK.getId() || id == FLUID_PORT.getId();
	}

	public static void registerRRSBlocks() {
		MAGMATIC_EXTRACTOR = getInfoByName("Magmatic Extractor");
		VAPOR_SIPHON = getInfoByName("Vapor Siphon");
	}

	public static short getIdByName(String containerType) {
		for(ElementRegistry registry : values()) {
			if(registry.getInfo().getName().equalsIgnoreCase(containerType)) {
				return registry.getId();
			}
		}
		return -1;
	}

	public static double getCapacityForContainer(short containerId) {
		if(containerId == FLUID_TANK.getId()) {
			return ConfigManager.getCapacityPerTank();
		} else if(containerId == FLUID_PORT.getId()) {
			return ConfigManager.getCapacityPerPort();
		} else if(containerId == FLUID_CANISTER.getId()) {
			return ConfigManager.getCapacityPerCanister();
		} else {
			return 0;
		}
	}

	public ElementInformation getInfo() {
		return elementInterface.getInfo();
	}

	public short getId() {
		return getInfo().id;
	}
}
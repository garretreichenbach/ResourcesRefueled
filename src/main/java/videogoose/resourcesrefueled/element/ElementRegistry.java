package videogoose.resourcesrefueled.element;

import api.common.GameCommon;
import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import videogoose.resourcesrefueled.element.block.pipes.FluidFilter;
import videogoose.resourcesrefueled.element.block.pipes.FluidPipe;
import videogoose.resourcesrefueled.element.block.pipes.FluidPump;
import videogoose.resourcesrefueled.element.block.pipes.FluidValve;
import videogoose.resourcesrefueled.element.block.systems.FluidTank;
import videogoose.resourcesrefueled.element.block.systems.HeliogenCondenser;
import videogoose.resourcesrefueled.element.block.systems.HeliogenRefinery;
import videogoose.resourcesrefueled.element.block.systems.HeliogenRefineryController;
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
	FLUID_PIPE(new FluidPipe()),
	FLUID_VALVE(new FluidValve()),
	FLUID_FILTER(new FluidFilter()),
	FLUID_PUMP(new FluidPump()),

	HELIOGEN_TANK(new FluidTank("Heliogen Tank", HELIOGEN_CANISTER_FILLED::getId, 50, true)),

	//Heliogen production blocks
	HELIOGEN_CONDENSER(new HeliogenCondenser()),
	HELIOGEN_REFINERY(new HeliogenRefinery()),
	HELIOGEN_REFINERY_CONTROLLER(new HeliogenRefineryController());

	public final ElementInterface elementInterface;

	ElementRegistry(ElementInterface elementInterface) {
		this.elementInterface = elementInterface;
	}

	public static void registerElements() {
		for(ElementRegistry registry : values()) {
			registry.elementInterface.initData();
		}

		for(ElementRegistry registry : values()) {
			registry.elementInterface.postInitData();
		}

		for(ElementRegistry registry : values()) {
			if(!GameCommon.isDedicatedServer()) {
				registry.elementInterface.initResources();
			}
		}

		for(ElementRegistry registry : values()) {
			BlockConfig.add(registry.getInfo());
		}
	}

	public ElementInformation getInfo() {
		return elementInterface.getInfo();
	}

	public short getId() {
		return getInfo().id;
	}
}
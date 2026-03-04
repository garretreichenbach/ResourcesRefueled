package videogoose.resourcesrefueled.industry;

import api.listener.Listener;
import api.mod.StarLoader;
import org.ithirahad.resourcesresourced.events.RRSRecipeAddEvent;
import org.ithirahad.resourcesresourced.industry.RRSRecipeManager;
import videogoose.resourcesrefueled.ResourcesRefueled;
import videogoose.resourcesrefueled.element.ElementRegistry;

import static org.ithirahad.resourcesresourced.industry.RRSRecipeManager.comp;

/**
 * Registers all ResourcesRefueled block assembly recipes into RRS's Block Assembler.
 * <p>
 * Listens for {@link RRSRecipeAddEvent}, which RRS fires at the end of its own
 * recipe pass. This guarantees that BLOCK_FACTORY_INDEX is initialised and all
 * RRS component IDs are resolved before we call {@link RRSRecipeManager#addBlock}.
 * <p>
 * Recipe philosophy:
 *  <li>Pipe network blocks use common T1 components — cheap and accessible.</li>
 *  <li>Heliogen processing/storage blocks require T2 components that reflect the</li>
 *      exotic nature of the fuel (Anbaric coils for containment, Thermyn charges
 *      for the heat-intensive refining process).
 *  <li>The controller block mirrors RRS's weapon computer cost (Metal Frames +
 *      Sheets + Circuitry + Crystal Panel).</li>
 */
public class RecipeManager {

	public static void initialize(ResourcesRefueled instance) {
		StarLoader.registerListener(RRSRecipeAddEvent.class, new Listener<RRSRecipeAddEvent>() {
			@Override
			public void onEvent(RRSRecipeAddEvent event) {
				registerRecipes();
			}
		}, instance);
	}

	private static void registerRecipes() {
		try {
			registerPipeRecipes();
			registerHeliogenRecipes();
		} catch(Exception e) {
			ResourcesRefueled.getInstance().logException("[ResourcesRefueled] Failed to register block assembly recipes", e);
		}
	}

	// -------------------------------------------------------------------------
	// Pipe network
	// -------------------------------------------------------------------------

	private static void registerPipeRecipes() {
		// Fluid Pipe — basic conduit, no logic required
		RRSRecipeManager.addBlock(ElementRegistry.FLUID_PIPE.getInfo(), comp(1, "Metal Frame"), comp(1, "Metal Sheet"));

		// Fluid Valve — mechanically controlled gate
		RRSRecipeManager.addBlock(ElementRegistry.FLUID_VALVE.getInfo(), comp(1, "Metal Frame"), comp(1, "Metal Sheet"), comp(1, "Standard Circuitry"));

		// Fluid Filter — selective gate, needs processing logic
		RRSRecipeManager.addBlock(ElementRegistry.FLUID_FILTER.getInfo(), comp(1, "Metal Frame"), comp(1, "Standard Circuitry"), comp(1, "Crystal Panel"));

		// Fluid Pump — active mover, draws power
		RRSRecipeManager.addBlock(ElementRegistry.FLUID_PUMP.getInfo(), comp(1, "Metal Frame"), comp(1, "Metal Sheet"), comp(1, "Energy Cell"), comp(1, "Standard Circuitry"));
	}

	// -------------------------------------------------------------------------
	// Heliogen production & storage
	// -------------------------------------------------------------------------

	private static void registerHeliogenRecipes() {
		// Heliogen Tank — pressurised vessel for volatile plasma.
		// Anbaric Distortion Coils provide the exotic containment field that
		// keeps compressed Heliogen stable.
		RRSRecipeManager.addBlock(ElementRegistry.HELIOGEN_TANK.getInfo(), comp(2, "Metal Frame"), comp(6, "Metal Sheet"), comp(2, "Anbaric Distortion Coil"));

		// Heliogen Condenser — converts stellar radiation into raw plasma.
		// Parsyne Focus captures the stellar energy; Anbaric Coil initiates the
		// condensation reaction; Crystal Energy Focus channels the beam output.
		RRSRecipeManager.addBlock(ElementRegistry.HELIOGEN_CONDENSER.getInfo(), comp(2, "Metal Frame"), comp(1, "Crystal Energy Focus"), comp(1, "Parsyne Amplifying Focus"), comp(1, "Anbaric Distortion Coil"));

		// Heliogen Refinery — heat-intensive compression of raw plasma into canisters.
		// Thermyn Power Charge drives the high-energy compression process.
		RRSRecipeManager.addBlock(ElementRegistry.HELIOGEN_REFINERY.getInfo(), comp(2, "Metal Frame"), comp(1, "Metal Sheet"), comp(1, "Thermyn Power Charge"), comp(1, "Standard Circuitry"));

		// Heliogen Refinery Controller — the computer block of the refinery multiblock.
		// Mirrors RRS weapon computer cost: Metal Frames + Sheets + logic + optics.
		RRSRecipeManager.addBlock(ElementRegistry.HELIOGEN_REFINERY_CONTROLLER.getInfo(), comp(1, "Metal Frame"), comp(6, "Metal Sheet"), comp(1, "Standard Circuitry"), comp(1, "Crystal Panel"));
	}
}


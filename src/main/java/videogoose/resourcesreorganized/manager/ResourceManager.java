package videogoose.resourcesreorganized.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.forms.Mesh;
import videogoose.resourcesreorganized.ResourcesReorganized;

import javax.imageio.ImageIO;
import java.util.HashMap;

public class ResourceManager {

	private static final HashMap<String, StarLoaderTexture> textures = new HashMap<>();
	private static final HashMap<String, Mesh> models = new HashMap<>();

	public static void loadResources() {
//		models.put("ConveyorBeltSingle", loadModel("ConveyorBeltSingle"));
//		models.put("ConveyorBeltEnd", loadModel("ConveyorBeltEnd"));
//		models.put("ConveyorBeltMid", loadModel("ConveyorBeltMid"));
//		models.put("ConveyorBeltPort", loadModel("ConveyorBeltPort"));
//		models.put("ConveyorBeltPortMid", loadModel("ConveyorBeltPortMid"));
	}

	public static StarLoaderTexture getTexture(String name) {
		return textures.get(name);
	}

	/** {@code true} if the named model loaded successfully (its zip existed and parsed). */
	public static boolean isModelLoaded(String name) {
		return models.get(name) != null;
	}

	/**
	 * The engine mesh-namespace reference for a mod model, matching what
	 * {@code MeshLoader.loadModObjMesh} registers ({@code modName + "~" + name}).
	 */
	public static String modelRef(String name) {
		return ResourcesReorganized.getInstance().getName() + "~" + name;
	}

	private static StarLoaderTexture loadTexture(String name) {
		try {
			// Use the mod's ModSkeleton classloader (via getJarResource) — the mod main class's
			// own classloader does not serve packaged jar resources.
			return StarLoaderTexture.newBlockTexture(ImageIO.read(ResourcesReorganized.getInstance().getJarResource("textures/" + name + ".png")));
		} catch(Exception exception) {
			ResourcesReorganized.getInstance().logException("Failed to load resource image: " + name, exception);
		}
		return null;
	}

	private static Mesh loadModel(String name) {
		try {
			return Controller.getResLoader().getMeshLoader().loadModObjMesh(ResourcesReorganized.getInstance(), name, ResourcesReorganized.getInstance().getJarResource("models/" + name + ".zip"), null);
		} catch(Exception exception) {
			ResourcesReorganized.getInstance().logException("Failed to load resource mesh: " + name, exception);
		}
		return null;
	}
}

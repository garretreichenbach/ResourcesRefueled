package videogoose.resourcesreorganized.manager;

import api.utils.textures.StarLoaderTexture;
import videogoose.resourcesreorganized.ResourcesReorganized;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public class ResourceManager {

	private static final HashMap<String, StarLoaderTexture> textures = new HashMap<>();

	public static void loadResources() {
		textures.put("fluid_tank", loadTexture("textures/fluid_tank/fluid_tank.png"));
	}

	public static StarLoaderTexture getTexture(String name) {
		return textures.get(name);
	}

	private static StarLoaderTexture loadTexture(String path) {
		try {
			return StarLoaderTexture.newBlockTexture(ImageIO.read(Objects.requireNonNull(ResourcesReorganized.getInstance().getClass().getClassLoader().getResourceAsStream(path))));
		} catch(IOException exception) {
			ResourcesReorganized.getInstance().logException("Failed to load resource image: " + path, exception);
		}
		return null;
	}
}

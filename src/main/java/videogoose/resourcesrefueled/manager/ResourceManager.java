package videogoose.spaceguard.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.core.ResourceException;
import org.schema.schine.graphicsengine.forms.Mesh;
import org.schema.schine.graphicsengine.forms.Sprite;
import org.schema.schine.resource.ResourceLoader;
import videogoose.spaceguard.SpaceGuard;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.HashMap;

public class ResourceManager {

	private static final String[] textureNames = {

	};

	private static final String[] spriteNames = {

	};

	private static final String[] modelNames = {

	};

	private static final HashMap<String, StarLoaderTexture> textureMap = new HashMap<>();
	private static final HashMap<String, Sprite> spriteMap = new HashMap<>();
	private static final HashMap<String, Mesh> modelMap = new HashMap<>();

	public static void loadResources(SpaceGuard instance, ResourceLoader loader) {
		StarLoaderTexture.runOnGraphicsThread(() -> {
			//Load Textures
			for(String textureName : textureNames) {
				try {
					if(textureName.endsWith("icon"))
						textureMap.put(textureName, StarLoaderTexture.newIconTexture(ImageIO.read(instance.getJarResource("textures/" + textureName + ".png"))));
					else
						textureMap.put(textureName, StarLoaderTexture.newBlockTexture(ImageIO.read(instance.getJarResource("textures/" + textureName + ".png"))));
				} catch(Exception exception) {
					instance.logException("Failed to load texture \"" + textureName + "\"", exception);
				}
			}

			//Load Sprites
			for(String spriteName : spriteNames) {
				try {
					Sprite sprite = StarLoaderTexture.newSprite(ImageIO.read(instance.getJarResource("sprites/" + spriteName + ".png")), instance, spriteName);
					sprite.setPositionCenter(false);
					sprite.setName(spriteName);
					spriteMap.put(spriteName, sprite);
				} catch(Exception exception) {
					instance.logException("Failed to load sprite \"" + spriteName + "\"", exception);
				}
			}

			//Load models
			for(String modelName : modelNames) {
				try {
					loader.getMeshLoader().loadModMesh(instance, modelName, instance.getJarResource("models/" + modelName + ".zip"), null);
					Mesh mesh = loader.getMeshLoader().getModMesh(instance, modelName);
					mesh.setFirstDraw(true);
					modelMap.put(modelName, mesh);
				} catch(ResourceException | IOException exception) {
					instance.logException("Failed to load model \"" + modelName + "\"", exception);
				}
			}
		});
	}

	public static StarLoaderTexture getTexture(String name) {
		return textureMap.get(name);
	}

	public static Sprite getSprite(String name) {
		return spriteMap.get(name);
	}

	public static Mesh getModel(String name) {
		return modelMap.get(name);
	}
}

package videogoose.resourcesreorganized.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.forms.Mesh;
import videogoose.resourcesreorganized.ResourcesReorganized;
import videogoose.resourcesreorganized.logistics.item.belt.BeltShape;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourceManager {


	private static final HashMap<String, StarLoaderTexture> textures = new HashMap<>();
	private static final HashMap<String, Mesh> models = new HashMap<>();

	/** Diffuse texture shared by every conveyor belt mesh. */
	private static final String CONVEYOR_TEXTURE = "conveyor/ConveyorBelt";

	public static void loadResources() {
		for(BeltShape shape : BeltShape.values()) {
			String name = shape.modelName();
			Mesh mesh = loadModel(name, name, shape.modelYawSteps());
			if(mesh == null && shape != BeltShape.STRAIGHT) {
				// A block whose mesh is missing NPEs the engine mid-render (getModelCount on a null mesh),
				// so register the straight belt's geometry under this shape's name instead: the block looks
				// wrong, which is loud but survivable, rather than taking the client down.
				ResourcesReorganized.getInstance().logInfo("Falling back to the straight conveyor model for " + name);
				mesh = loadModel(name, BeltShape.STRAIGHT.modelName(), 0);
			}
			models.put(name, mesh);
		}
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
			return StarLoaderTexture.newBlockTexture(ImageIO.read(ResourcesReorganized.getInstance().getJarResource("textures/" + name + ".png")));
		} catch(Exception exception) {
			ResourcesReorganized.getInstance().logException("Failed to load resource image: " + name, exception);
		}
		return null;
	}

	/**
	 * Packs {@code source}'s obj/mtl/texture into an in-memory zip and registers it in the engine mesh
	 * namespace under {@code registerAs}. The two differ only on the fallback path: {@code MeshLoader}
	 * finds the obj inside the archive by extension, so the registered name is free to differ from the
	 * geometry's own file name. {@code yawSteps} rotates the geometry into the mod's belt convention
	 * (see {@link BeltShape#modelYawSteps()}).
	 */
	private static Mesh loadModel(String registerAs, String source, int yawSteps) {
		try {
			byte[] zip = buildModelZip(source, CONVEYOR_TEXTURE, yawSteps);
			return Controller.getResLoader().getMeshLoader().loadModObjMesh(ResourcesReorganized.getInstance(), registerAs, new ByteArrayInputStream(zip), "none");
		} catch(Exception exception) {
			ResourcesReorganized.getInstance().logException("Failed to load conveyor model: " + source, exception);
		}
		return null;
	}

	private static byte[] buildModelZip(String name, String textureName, int yawSteps) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try(ZipOutputStream zip = new ZipOutputStream(out)) {
			addObjEntry(zip, "models/" + name + ".obj", yawSteps);
			addZipEntry(zip, "models/" + name + ".mtl", "models/" + name + ".mtl");
			addZipEntry(zip, "models/" + textureName + ".png", "models/" + textureName + ".png");
		}
		return out.toByteArray();
	}

	/**
	 * Copies an {@code .obj} into the archive, rotating its positions and normals by {@code yawSteps}
	 * quarter turns about {@code +Y}. A quarter turn maps the unit cell onto itself and preserves
	 * winding, so face definitions and the material library reference are passed through untouched.
	 */
	private static void addObjEntry(ZipOutputStream zip, String resourcePath, int yawSteps) throws IOException {
		if(((yawSteps % 4) + 4) % 4 == 0) {
			addZipEntry(zip, resourcePath, resourcePath);
			return;
		}
		StringBuilder rotated = new StringBuilder();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(
				ResourcesReorganized.getInstance().getJarResource(resourcePath), StandardCharsets.UTF_8))) {
			String line;
			while((line = reader.readLine()) != null) {
				rotated.append(rotateObjLine(line, yawSteps)).append('\n');
			}
		}
		zip.putNextEntry(new ZipEntry(resourcePath));
		zip.write(rotated.toString().getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	/** Rotates a {@code v}/{@code vn} line about {@code +Y}; every other line is returned unchanged. */
	private static String rotateObjLine(String line, int yawSteps) {
		boolean position = line.startsWith("v ");
		if(!position && !line.startsWith("vn ")) {
			return line;
		}
		String[] parts = line.trim().split("\\s+");
		if(parts.length < 4) {
			return line;
		}
		float x = Float.parseFloat(parts[1]);
		float y = Float.parseFloat(parts[2]);
		float z = Float.parseFloat(parts[3]);
		// Right-handed rotation about +Y: x' = x*cos + z*sin, z' = -x*sin + z*cos, for angle 90*steps.
		for(int i = 0; i < (((yawSteps % 4) + 4) % 4); i++) {
			float nx = z;
			z = -x;
			x = nx;
		}
		StringBuilder out = new StringBuilder(position ? "v " : "vn ");
		out.append(String.format(Locale.ROOT, "%.6f %.6f %.6f", x, y, z));
		// Vertex colours (or any trailing components) ride along after xyz.
		for(int i = 4; i < parts.length; i++) {
			out.append(' ').append(parts[i]);
		}
		return out.toString();
	}

	private static void addZipEntry(ZipOutputStream zip, String entryName, String resourcePath) throws IOException {
		try(InputStream in = ResourcesReorganized.getInstance().getJarResource(resourcePath)) {
			zip.putNextEntry(new ZipEntry(entryName));
			in.transferTo(zip);
			zip.closeEntry();
		}
	}
}

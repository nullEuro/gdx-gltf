package net.mgsx.gltf.demo;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Net.HttpMethods;
import com.badlogic.gdx.Net.HttpRequest;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.graphics.g3d.environment.BaseLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import net.mgsx.gltf.demo.data.ModelEntry;
import net.mgsx.gltf.demo.events.FileChangeEvent;
import net.mgsx.gltf.demo.events.IBLFolderChangeEvent;
import net.mgsx.gltf.demo.shaders.OutlineShader;
import net.mgsx.gltf.demo.shaders.OutlineShaderProvider;
import net.mgsx.gltf.demo.ui.GLTFDemoUI;
import net.mgsx.gltf.demo.util.GLTFInspector;
import net.mgsx.gltf.demo.util.NodeUtil;
import net.mgsx.gltf.demo.util.SafeHttpResponseListener;
import net.mgsx.gltf.loaders.glb.GLBAssetLoader;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.loaders.shared.texture.PixmapBinaryLoaderHack;
import net.mgsx.gltf.scene3d.attributes.FogAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneRenderableSorter;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import net.mgsx.gltf.scene3d.utils.EnvironmentUtil;
import net.mgsx.gltf.scene3d.utils.LightUtils;
import net.mgsx.gltf.scene3d.utils.LightUtils.LightsInfo;

public class GLTFDemo extends ApplicationAdapter
{
	// change this to test asset manager or direct loading
	private static boolean USE_ASSET_MANAGER = true;
	
	public static String AUTOLOAD_ENTRY = null;
	public static String AUTOLOAD_VARIANT = null;
	public static String alternateMaps = null;
	
	private static final String TAG = "GLTFDemo";
	
	public static enum ShaderMode{
		GOURAUD,	// https://en.wikipedia.org/wiki/Gouraud_shading#Comparison_with_other_shading_techniques
//		PHONG,   	// https://en.wikipedia.org/wiki/Phong_shading
		PBR_MR, 
//		PBR_MRSG
		CeilShading
	}
	
	private ShaderMode shaderMode = ShaderMode.PBR_MR;
	
	private String samplesPath;
	
	private Stage stage;
	private Skin skin;
	private Array<ModelEntry> entries;
	
	private FileHandle rootFolder;
	private CameraInputController cameraControl;
	
	private Scene scene;
	
	private SceneAsset rootModel;

	private SceneManager sceneManager;
	private GLTFDemoUI ui;
	private Cubemap diffuseCubemap;
	private Cubemap environmentCubemap;
	private Cubemap specularCubemap;
	private Texture brdfLUT;
	
	private ModelBatch outlineExtBatch;
	
	private AssetManager assetManager;
	private String lastFileName;
	
	private ShapeRenderer shapeRenderer;
	private final BoundingBox sceneBox = new BoundingBox();
	private SceneSkybox skybox;
	private DirectionalLight defaultLight;
	private boolean shadersValid;
	private boolean outlineShaderValid;
	
	private FrameBuffer depthFbo;
	
	private SpriteBatch spriteBatch;
	
	private ShaderProgram outlineShader;
	
	public GLTFDemo() {
		this("models");
	}
	
	public GLTFDemo(String samplesPath) {
		this.samplesPath = samplesPath;
	}
	
	@Override
	public void create() {
		
		assetManager = new AssetManager();
		Texture.setAssetManager(assetManager);
		
		assetManager.setLoader(SceneAsset.class, ".gltf", new GLTFAssetLoader());
		assetManager.setLoader(SceneAsset.class, ".glb", new GLBAssetLoader());
		
		shapeRenderer = new ShapeRenderer();
		
		spriteBatch = new SpriteBatch();
		
		createUI();
		
		createSceneManager();
		
		loadModelIndex();
	}
	
	private void createSceneManager()
	{
		// set environment maps
		
		if(alternateMaps != null){
			diffuseCubemap = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), 
					"textures/" + alternateMaps + "/diffuse/diffuse_", ".jpg", EnvironmentUtil.FACE_NAMES_NEG_POS);
			
			environmentCubemap = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), 
					"textures/" + alternateMaps + "/environment/environment_", ".jpg", EnvironmentUtil.FACE_NAMES_NEG_POS);
			
			specularCubemap = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), 
					"textures/" + alternateMaps + "/specular/specular_", "_", ".jpg", 10, EnvironmentUtil.FACE_NAMES_NEG_POS);
		}else{
			diffuseCubemap = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), 
					"textures/diffuse/diffuse_", "_0.jpg", EnvironmentUtil.FACE_NAMES_FULL);
			
			environmentCubemap = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), 
					"textures/environment/environment_", "_0.png", EnvironmentUtil.FACE_NAMES_FULL);
			
			specularCubemap = EnvironmentUtil.createCubemap(new InternalFileHandleResolver(), 
					"textures/specular/specular_", "_", ".jpg", 10, EnvironmentUtil.FACE_NAMES_FULL);
		}
		
		
		brdfLUT = new Texture(Gdx.files.internal("textures/brdfLUT.png"));
		
		sceneManager = new SceneManager();
		
		sceneManager.setSkyBox(skybox = new SceneSkybox(environmentCubemap));
		
		setEnvironment();
	}
	
	private void setEnvironment()
	{
		// TODO config UI based
		
		sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));
		
		sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));

		if(brdfLUT != null){
			sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
		}
		
		sceneManager.environment.set(new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 0f));
		
		defaultLight = new DirectionalLightEx();
		resetDefaultLight();
		sceneManager.environment.add(defaultLight);
		
		sceneManager.setAmbientLight(1f);
		ui.ambiantSlider.setValue(1f);
	}
	
	protected void loadIBL(FileHandle file) 
	{
		// format from CMFT
		Cubemap newEnvironmentCubemap = null;
		Cubemap newDiffuseCubemap = null;
		Cubemap newSpecularCubemap = null;
		
		try{
			newEnvironmentCubemap = EnvironmentUtil.createCubemap(new AbsoluteFileHandleResolver(), 
					file.path() + "/environment/environment_", ".jpg", EnvironmentUtil.FACE_NAMES_NEG_POS);
			
			newDiffuseCubemap = EnvironmentUtil.createCubemap(new AbsoluteFileHandleResolver(), 
					file.path() + "/diffuse/diffuse_", ".jpg", EnvironmentUtil.FACE_NAMES_NEG_POS);
			
			newSpecularCubemap = EnvironmentUtil.createCubemap(new AbsoluteFileHandleResolver(), 
					file.path() + "/specular/specular_", "_", ".jpg", 10, EnvironmentUtil.FACE_NAMES_NEG_POS);
		}catch(GdxRuntimeException e){
			e.printStackTrace();
			return;
		}
		
		// cleanup
		if(diffuseCubemap != null){
			diffuseCubemap.dispose();
			diffuseCubemap = null;
		}
		if(specularCubemap != null){
			specularCubemap.dispose();
			specularCubemap = null;
		}
		if(environmentCubemap != null){
			environmentCubemap.dispose();
			environmentCubemap = null;
		}
		
		// update
		diffuseCubemap = newDiffuseCubemap;
		specularCubemap = newSpecularCubemap;
		environmentCubemap = newEnvironmentCubemap;

		// update skybox
		skybox.set(environmentCubemap);
		
		changeIBLOptions();
	}
	
	protected void changeIBLOptions() {
		
		// remove all
		sceneManager.environment.remove(PBRCubemapAttribute.DiffuseEnv);
		sceneManager.environment.remove(PBRCubemapAttribute.SpecularEnv);
		sceneManager.environment.remove(PBRTextureAttribute.BRDFLUTTexture);
		
		// enable some of them
		if(ui.IBLEnabled.isOn()){
			sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));
			if(ui.IBLSpecular.isOn()){
				sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
			}
			if(ui.IBLLookup.isOn()){
				sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
			}
		}
		
		invalidateShaders();
	}

	
	private void loadModelIndex() 
	{
		rootFolder = Gdx.files.internal(samplesPath);	
		
		String indexFilename = Gdx.app.getType() == ApplicationType.WebGL || Gdx.app.getType() == ApplicationType.Android ? "model-index-web.json" : "model-index.json";
		
		FileHandle file = rootFolder.child(indexFilename);
		
		entries = new Json().fromJson(Array.class, ModelEntry.class, file);
		
		ui.entrySelector.setItems(entries);
		
		if(AUTOLOAD_ENTRY != null && AUTOLOAD_VARIANT != null){
			for(int i=0 ; i<entries.size ; i++){
				ModelEntry entry = entries.get(i);
				if(entry.name.equals(AUTOLOAD_ENTRY)){
					ui.entrySelector.setSelected(entry);
					// will be auto select if there is only one variant.
					if(entry.variants.size != 1){
						ui.variantSelector.setSelected(AUTOLOAD_VARIANT);
					}
					break;
				}
			}
		}
	}

	private void createUI()
	{
		stage = new Stage(new ScreenViewport());
		Gdx.input.setInputProcessor(stage);
		skin = new Skin(Gdx.files.internal("skins/uiskin.json"));
		
		ui = new GLTFDemoUI(skin);
		ui.setFillParent(true);
		
		stage.addActor(ui);
		
		ui.shaderSelector.setSelected(shaderMode);
		
		ui.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if(event instanceof FileChangeEvent){
					ui.entrySelector.setSelectedIndex(0);
					ui.variantSelector.setSelectedIndex(0);
					load(((FileChangeEvent) event).file);
				}else if(event instanceof IBLFolderChangeEvent){
					loadIBL(((IBLFolderChangeEvent) event).file);
				}
			}
		});
		
		ui.entrySelector.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				ui.setEntry(ui.entrySelector.getSelected(), rootFolder);
				setImage(ui.entrySelector.getSelected());
			}
		});
		
		ui.variantSelector.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				load(ui.entrySelector.getSelected(), ui.variantSelector.getSelected());
			}
		});
		
		ui.animationSelector.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				setAnimation(ui.animationSelector.getSelected());
			}
		});
		
		ui.cameraSelector.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				setCamera(ui.cameraSelector.getSelected());
			}
		});
		
		ui.shaderSelector.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				setShader(ui.shaderSelector.getSelected());
			}
		});
		
		ChangeListener shaderOptionListener = new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				invalidateShaders();
			}
		};
		
		ui.shaderSRGB.addListener(shaderOptionListener);
		
		ui.sceneSelector.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				load(ui.sceneSelector.getSelected());
			}
		});
		
		ui.lightShadow.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				setShadow(ui.lightShadow.isOn());
			}
		});
		
		ui.btAllAnimations.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if(ui.btAllAnimations.isChecked()){
					scene.animations.playAll();
				}else{
					scene.animations.stopAll();
				}
			}
		});
		
		ui.fogEnabled.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if(ui.fogEnabled.isOn()){
					sceneManager.environment.set(new ColorAttribute(ColorAttribute.Fog, ui.fogColor.value));
					sceneManager.environment.set(new FogAttribute(FogAttribute.FogEquation));
				}else{
					sceneManager.environment.remove(ColorAttribute.Fog);
					sceneManager.environment.remove(FogAttribute.FogEquation);
				}
				invalidateShaders();
			}
		});
		
		ui.skyBoxEnabled.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if(ui.skyBoxEnabled.isOn()){
					sceneManager.setSkyBox(skybox);
				}else{
					sceneManager.setSkyBox(null);
				}
			}
		});
		
		ui.IBLEnabled.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				changeIBLOptions();
			}
		});
		
		ui.IBLSpecular.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				changeIBLOptions();
			}
		});
		
		ui.IBLLookup.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				changeIBLOptions();
			}
		});
		
		ui.outlineDistFalloffOption.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				invalidateOutlineShaders();
			}
		});
		
		ui.outlinesEnabled.addListener(new ChangeListener() {
			
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				invalidateOutlineShaders();
			}
		});
		
	}
	
	protected void setShadow(boolean isOn) {
		
		if(isOn){
			// change first direction light to shadow light (1 only supported for now)
			DirectionalLight oldLight = sceneManager.getFirstDirectionalLight();
			if(oldLight != null && !(oldLight instanceof DirectionalShadowLight)){
				DirectionalLight newLight = new DirectionalShadowLight().setBounds(sceneBox).set(oldLight);
				sceneManager.environment.remove(oldLight);
				sceneManager.environment.add(newLight);
				if(oldLight == defaultLight){
					defaultLight = newLight;
				}
			}
		}else{
			// remove all shadow lights, converting back to classic light
			DirectionalLightsAttribute dla = sceneManager.environment.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
			if(dla != null){
				Array<BaseLight> lightsToRemove = new Array<BaseLight>();
				Array<BaseLight> lightsToAdd = new Array<BaseLight>();
				for(DirectionalLight oldLight : dla.lights){
					if(oldLight instanceof DirectionalShadowLight){
						((DirectionalShadowLight)oldLight).dispose();
						lightsToRemove.add(oldLight);
						DirectionalLight newLight = new DirectionalLightEx().set(oldLight);
						lightsToAdd.add(newLight);
						if(oldLight == defaultLight){
							defaultLight = newLight;
						}
					}
				}
				sceneManager.environment.remove(lightsToRemove);
				sceneManager.environment.add(lightsToAdd);
			}
		}
		
		invalidateShaders();
	}

	private void invalidateShaders() {
		shadersValid = false;
	}
	private void invalidateOutlineShaders() {
		outlineShaderValid = false;
	}
	
	private void validateShaders(){
		if(!shadersValid){
			shadersValid = true;
			sceneManager.setShaderProvider(createShaderProvider(shaderMode, rootModel.maxBones));
			sceneManager.setDepthShaderProvider(PBRShaderProvider.createDepthShaderProvider(rootModel.maxBones));
			
			if(outlineExtBatch != null) outlineExtBatch.dispose();
			OutlineShaderProvider outlineShaderProvider = new OutlineShaderProvider(rootModel.maxBones);
			outlineExtBatch = new ModelBatch(outlineShaderProvider, new SceneRenderableSorter());
		}
		if(!outlineShaderValid){
			outlineShaderValid = true;
			if(outlineShader != null) outlineShader.dispose();
			if(ui.outlinesEnabled.isOn()){
				String prefix = "";
				if(ui.outlineDistFalloffOption.isOn()){
					prefix += "#define DISTANCE_FALLOFF\n";
				}
				outlineShader = new ShaderProgram(
						Gdx.files.classpath("net/mgsx/gltf/demo/shaders/outline.vs.glsl").readString(),
						prefix + Gdx.files.classpath("net/mgsx/gltf/demo/shaders/outline.fs.glsl").readString());
				if(!outlineShader.isCompiled()) throw new GdxRuntimeException("Outline Shader failed: " + outlineShader.getLog());
			}
		}
	}

	protected void setImage(ModelEntry entry) {
		if(entry.screenshot != null){
			if(entry.url != null){
				HttpRequest httpRequest = new HttpRequest(HttpMethods.GET);
				httpRequest.setUrl(entry.url + entry.screenshot);

				Gdx.net.sendHttpRequest(httpRequest, new SafeHttpResponseListener(){
					@Override
					protected void handleData(byte[] bytes) {
						Pixmap pixmap = PixmapBinaryLoaderHack.load(bytes, 0, bytes.length);
						ui.setImage(new Texture(pixmap));
						pixmap.dispose();
					}
					@Override
					protected void handleError(Throwable t) {
						Gdx.app.error(TAG, "request error", t);
					}
					@Override
					protected void handleEnd() {
					}
				});
			}else{
				FileHandle file = rootFolder.child(entry.name).child(entry.screenshot);
				if(file.exists()){
					ui.setImage(new Texture(file));
				}else{
					Gdx.app.error("DEMO UI", "file not found " + file.path());
				}
			}
		}
	}

	private void setShader(ShaderMode shaderMode) {
		this.shaderMode = shaderMode;
		invalidateShaders();
	}
	
	private ShaderProvider createShaderProvider(ShaderMode shaderMode, int maxBones){
		
		// fit lights and bones to current scene.
		LightsInfo info = LightUtils.getLightsInfo(new LightsInfo(), sceneManager.environment);
		Gdx.app.log(TAG, "Reset shaders. Lights( dirs: " + info.dirLights + ", points: " + info.pointLights + ", spots: " + info.spotLights + " )");
		
		switch(shaderMode){
		default:
		case GOURAUD:
			{
				Config config = new DefaultShader.Config();
				config.numBones = maxBones;
				config.numDirectionalLights = info.dirLights;
				config.numPointLights = info.pointLights;
				config.numSpotLights = info.spotLights;
				
				return new DefaultShaderProvider(config);
			}
//		case PHONG:
//			// TODO phong variant (pixel based lighting)
//		case PBR_MRSG:
//			// TODO SG shader variant
		case PBR_MR:
			{
				PBRShaderConfig config = PBRShaderProvider.defaultConfig();
				config.manualSRGB = ui.shaderSRGB.getSelected();
				config.numBones = maxBones;
				config.numDirectionalLights = info.dirLights;
				config.numPointLights = info.pointLights;
				config.numSpotLights = info.spotLights;
				return PBRShaderProvider.createDefault(config);
			}
			case CeilShading:
			{
				PBRShaderConfig config = PBRShaderProvider.defaultConfig();
				config.vertexShader = Gdx.files.classpath("net/mgsx/gltf/demo/shaders/gltf-ceil-shading.vs.glsl").readString();
				config.fragmentShader = Gdx.files.classpath("net/mgsx/gltf/demo/shaders/gltf-ceil-shading.fs.glsl").readString();
				config.manualSRGB = ui.shaderSRGB.getSelected();
				config.numBones = maxBones;
				config.numDirectionalLights = info.dirLights;
				config.numPointLights = info.pointLights;
				config.numSpotLights = info.spotLights;
				config.numVertexColors = 3;
				return PBRShaderProvider.createDefault(config);
			}
		}
	}

	private void setAnimation(String name) {
		if(scene != null && scene.animationController != null){
			if(name == null || name.isEmpty()){
				scene.animationController.setAnimation(null);
			}else{
				scene.animationController.animate(name, -1, 1f, null, 0f);
			}
		}
	}

	private void clearScene(){
		if(scene != null){
			sceneManager.removeScene(scene);
			scene = null;
		}
		sceneManager.environment.remove(defaultLight);
	}
	
	private void load(ModelEntry entry, String variant) {
		
		clearScene();
		
		if(rootModel != null){
			rootModel.dispose();
			rootModel = null;
			if(lastFileName != null){
				if(USE_ASSET_MANAGER){
					assetManager.unload(lastFileName);
				}
				lastFileName = null;
			}
		}
		
		
		if(variant.isEmpty()) return;
		
		final String fileName = entry.variants.get(variant);
		if(fileName == null) return;
		
		if(entry.url != null){
			
			final Table waitUI = new Table(skin);
			waitUI.add("LOADING...").expand().center();
			waitUI.setFillParent(true);
			stage.addActor(waitUI);
			
			HttpRequest httpRequest = new HttpRequest(HttpMethods.GET);
			httpRequest.setUrl(entry.url + variant + "/" + fileName);

			Gdx.net.sendHttpRequest(httpRequest, new SafeHttpResponseListener(){
				@Override
				protected void handleData(byte[] bytes) {
					Gdx.app.log(TAG, "loading " + fileName);
					
					if(fileName.endsWith(".gltf")){
						throw new GdxRuntimeException("remote gltf format not supported.");
					}else if(fileName.endsWith(".glb")){
						rootModel = new GLBLoader().load(bytes);
					}else{
						throw new GdxRuntimeException("unknown file extension for " + fileName);
					}
					
					load();
					
					Gdx.app.log(TAG, "loaded " + fileName);
				}
				@Override
				protected void handleError(Throwable t) {
					Gdx.app.error(TAG, "request error", t);
				}
				@Override
				protected void handleEnd() {
					waitUI.remove();
				}
			});
		}else{
			FileHandle baseFolder = rootFolder.child(entry.name).child(variant);
			FileHandle glFile = baseFolder.child(fileName);
			
			load(glFile);
		}
	}
	
	private void load(FileHandle glFile){
		Gdx.app.log(TAG, "loading " + glFile.name());
		
		lastFileName = glFile.path();
		
		if(USE_ASSET_MANAGER){
			assetManager.load(lastFileName, SceneAsset.class);
			assetManager.finishLoading();
			rootModel = assetManager.get(lastFileName, SceneAsset.class);
		}else{
			if(glFile.extension().equalsIgnoreCase("glb")){
				rootModel = new GLBLoader().load(glFile);
			}else if(glFile.extension().equalsIgnoreCase("gltf")){
				rootModel = new GLTFLoader().load(glFile);
			}
		}
		
		load();
		
		Gdx.app.log(TAG, "loaded " + glFile.path());
		
		new GLTFInspector().inspect(rootModel);
	}
	
	private void load()
	{
		if(rootModel.scenes.size > 1){
			ui.setScenes(rootModel.scenes);
			ui.sceneSelector.setSelectedIndex(rootModel.scenes.indexOf(rootModel.scene, true));
		}else{
			ui.setScenes(null);
			load(new Scene(rootModel.scene));
		}
	}
	
	protected void load(String name) {
		int index = ui.sceneSelector.getItems().indexOf(name, false) - 1;
		if(index < 0){
			return;
		}
		load(new Scene(rootModel.scenes.get(index)));
	}
	
	private void load(Scene scene)
	{
		clearScene();
		
		this.scene = scene;
		
		scene.modelInstance.calculateBoundingBox(sceneBox);
		
		ui.setMaterials(scene.modelInstance.materials);
		ui.setAnimations(scene.modelInstance.animations);
		ui.setNodes(NodeUtil.getAllNodes(new Array<Node>(), scene.modelInstance));
		ui.setCameras(scene.cameras);
		ui.setLights(scene.lights);
		
		if(scene.getDirectionalLightCount() == 0){
			resetDefaultLight();
			sceneManager.environment.add(defaultLight);
		}
		
		sceneManager.addScene(scene, true);
		
		setShadow(ui.lightShadow.isOn());
		
		DirectionalLight light = sceneManager.getFirstDirectionalLight();
		if(light instanceof DirectionalShadowLight){
			((DirectionalShadowLight)light).setBounds(sceneBox);
		}
		
		invalidateShaders();
	}
	
	private void resetDefaultLight() {
		// light direction based on environnement map SUN
		defaultLight.direction.set(-.5f,-.5f,-.7f).nor();
		defaultLight.color.set(Color.WHITE);
		if(defaultLight instanceof DirectionalLightEx){
			DirectionalLightEx light = (DirectionalLightEx)defaultLight;
			light.intensity = 1f;
			light.updateColor();
		}
		ui.lightDirectionControl.set(defaultLight.direction);
	}

	protected void setCamera(String name) 
	{
		if(name == null) return;
		if(name.isEmpty()){
			PerspectiveCamera camera = new PerspectiveCamera(60, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			camera.up.set(Vector3.Y);
			
			BoundingBox bb = this.sceneBox;
			
			Vector3 center = bb.getCenter(new Vector3());
			camera.position.set(bb.max).sub(center).scl(3).add(center);
			camera.lookAt(center);
			
			float size = Math.max(bb.getWidth(), Math.max(bb.getHeight(), bb.getDepth()));
			camera.near = size / 1000f;
			camera.far = size * 30f;
			
			camera.update(true);
			
			cameraControl = new CameraInputController(camera);
			cameraControl.translateUnits = bb.max.dst(bb.min);
			cameraControl.target.set(center);
			cameraControl.pinchZoomFactor = bb.max.dst(bb.min);
			
			
			sceneManager.setCamera(camera);
		}else{
			Camera camera = scene.getCamera(name);
			cameraControl = new CameraInputController(camera);
			sceneManager.setCamera(camera);
		}
		Gdx.input.setInputProcessor(new InputMultiplexer(stage, cameraControl));
	}

	@Override
	public void resize(int width, int height) {
		stage.getViewport().update(width, height, true);
		sceneManager.updateViewport(width, height);
	}
	
	@Override
	public void render() {
		float delta = Gdx.graphics.getDeltaTime();
		stage.act();
		
		// recreate shaders if needed
		validateShaders();

		sceneManager.update(delta);
		
		if(cameraControl != null){
			cameraControl.update();
		}
		
		Gdx.gl.glClearColor(ui.fogColor.value.r, ui.fogColor.value.g, ui.fogColor.value.b, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		
		sceneManager.setAmbientLight(ui.ambiantSlider.getValue());
		
		ColorAttribute fog = sceneManager.environment.get(ColorAttribute.class, ColorAttribute.Fog);
		if(fog != null) fog.color.set(ui.fogColor.value);
		
		FogAttribute fogEquation = sceneManager.environment.get(FogAttribute.class, FogAttribute.FogEquation);
		if(fogEquation != null){
			fogEquation.value.set(
					MathUtils.lerp(sceneManager.camera.near, sceneManager.camera.far, (ui.fogEquation.value.x + 1f) / 2f),
					MathUtils.lerp(sceneManager.camera.near, sceneManager.camera.far, (ui.fogEquation.value.y + 1f) / 2f),
					10f * (ui.fogEquation.value.z + 1f) / 2f
					);
		}
		
		skybox.getColor().set(ui.skyBoxColor.value);
		
		DirectionalLight light = sceneManager.getFirstDirectionalLight();
		if(light != null){
			float lum = ui.lightSlider.getValue();
			if(light instanceof DirectionalLightEx){
				DirectionalLightEx lightEx = (DirectionalLightEx)light;
				lightEx.intensity = lum;
				lightEx.updateColor();
			}
			light.direction.set(ui.lightDirectionControl.value).nor();
			
			PBRFloatAttribute shadowBias = sceneManager.environment.get(PBRFloatAttribute.class, PBRFloatAttribute.ShadowBias);
			shadowBias.value = ui.shadowBias.getValue() / 50f;
		}

		sceneManager.render();

		if(ui.outlinesExtEnabled.isOn()){
			
			OutlineShader.extrusionRate = ui.outlinesExtWidth.getValue() * .1f;
			
			OutlineShader.extrusionColor.set(ui.outlinesExtColor.value);
			
			ModelBatch oldBatch = sceneManager.getBatch();
			sceneManager.setBatch(outlineExtBatch);

			sceneManager.renderColors();
			
			sceneManager.setBatch(oldBatch);
		}

		if(ui.outlinesEnabled.isOn()){
			captureDepth();

			outlineShader.begin();
			float size = 1 - ui.outlinesWidth.getValue();
			
			// float depthMin = ui.outlineDepthMin.getValue() * .001f;
			float depthMin = (float)Math.pow(ui.outlineDepthMin.getValue(), 10); // 0.35f
			float depthMax = (float)Math.pow(ui.outlineDepthMax.getValue(), 10); // 0.9f
			
			// TODO use an integer instead and divide w and h
			outlineShader.setUniformf("u_size", Gdx.graphics.getWidth() * size, Gdx.graphics.getHeight() * size);
			outlineShader.setUniformf("u_depth_min", depthMin);
			outlineShader.setUniformf("u_depth_max", depthMax);
			outlineShader.setUniformf("u_inner_color", ui.outlineInnerColor.getValue());
			outlineShader.setUniformf("u_outer_color", ui.outlineOuterColor.getValue());
			
			if(ui.outlineDistFalloffOption.isOn()){
				
				float distanceFalloff = ui.outlineDistFalloff.getValue();
				if(distanceFalloff <= 0){
					distanceFalloff = .001f;
				}
				outlineShader.setUniformf("u_depthRange", sceneManager.camera.far / (sceneManager.camera.near * distanceFalloff));
			}
			
			spriteBatch.enableBlending();
			spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, 1, 1);
			spriteBatch.setShader(outlineShader);
			spriteBatch.begin();
			spriteBatch.draw(depthFbo.getColorBufferTexture(), 0, 0, 1, 1, 0f, 0f, 1f, 1f);
			spriteBatch.end();
			spriteBatch.setShader(null);
		}
		
		renderOverlays();
		
		int shaderCount = 0;
		ShaderProvider shaderProvider = sceneManager.getBatch().getShaderProvider();
		if(shaderProvider instanceof PBRShaderProvider){
			shaderCount = ((PBRShaderProvider) shaderProvider).getShaderCount();
		}
		ui.shaderCount.setText(String.valueOf(shaderCount));
		
		stage.draw();
	}
	
	protected void captureDepth() {
		depthFbo = ensureFBO(depthFbo, true);
		depthFbo.begin();
		Gdx.gl.glClearColor(1f, 1f, 1f, 0.0f);
		Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT | GL20.GL_COLOR_BUFFER_BIT);
		sceneManager.renderDepth();
		depthFbo.end();
	}

	private FrameBuffer ensureFBO(FrameBuffer fbo, boolean hasDepth) {
		int w = Gdx.graphics.getBackBufferWidth();
		int h = Gdx.graphics.getBackBufferHeight();
		if(fbo == null || fbo.getWidth() != w || fbo.getHeight() != h){
			if(fbo != null) fbo.dispose();
			fbo = new FrameBuffer(Format.RGBA8888, w, h, hasDepth);
		}
		return fbo;
	}

	private void renderOverlays() {
		if(ui.skeletonButton.isChecked() && scene != null){
			shapeRenderer.setProjectionMatrix(sceneManager.camera.combined);
			shapeRenderer.begin(ShapeType.Line);
			drawSkeleton(scene.modelInstance.nodes);
			shapeRenderer.end();
		}
	}

	private static final Vector3 v1 = new Vector3();
	
	private void drawSkeleton(Iterable<Node> iterable) {
		for(Node node : iterable){
			if(node.parts == null || node.parts.size == 0){
				
				float s = cameraControl.translateUnits / 100f; // .03f;
				shapeRenderer.setColor(Color.WHITE);
				node.globalTransform.getTranslation(v1);
				shapeRenderer.box(v1.x, v1.y, v1.z, s,s,s);
			}
			drawSkeleton(node.getChildren());
		}
		
	}
	
}

package net.mgsx.gltf;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Net.HttpMethods;
import com.badlogic.gdx.Net.HttpRequest;
import com.badlogic.gdx.assets.loaders.resolvers.ClasspathFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
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

import net.mgsx.gltf.demo.ModelEntry;
import net.mgsx.gltf.loaders.GLBLoader;
import net.mgsx.gltf.loaders.GLTFLoader;
import net.mgsx.gltf.scene3d.NodeAnimationPlus;
import net.mgsx.gltf.scene3d.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.PBRShader;
import net.mgsx.gltf.scene3d.PBRShaderProvider;
import net.mgsx.gltf.scene3d.Scene;
import net.mgsx.gltf.scene3d.SceneAsset;
import net.mgsx.gltf.scene3d.SceneManager;
import net.mgsx.gltf.scene3d.SceneSkybox;
import net.mgsx.gltf.ui.GLTFDemoUI;
import net.mgsx.gltf.util.EnvironmentUtil;
import net.mgsx.gltf.util.NodeUtil;
import net.mgsx.gltf.util.SafeHttpResponseListener;

public class GLTFDemo extends ApplicationAdapter
{
	private static String AUTOLOAD_ENTRY = null; // "BoomBox" "BarramundiFish"
	private static String AUTOLOAD_VARIANT = null; // "glTF-Binary"  "glTF"
	
	private static final boolean USE_DEFAULT_ENV_MAP = true;
	
	private static final String TAG = "GLTFDemo";
	
	public static enum ShaderMode{
		FLAT, GOURAUD, PHONG, PBR_MR, PBR_SG, 
	}
	
	private ShaderMode shaderMode = ShaderMode.PBR_MR;
	
	private String samplesPath;
	
	private Stage stage;
	private Skin skin;
	private Array entries;
	
	private FileHandle rootFolder;
	private CameraInputController cameraControl;
	
	private Scene scene;
	
	private SceneAsset rootModel;

	private SceneManager sceneManager;
	private GLTFDemoUI ui;
	
	public GLTFDemo() {
		this("models");
	}
	
	public GLTFDemo(String samplesPath) {
		this.samplesPath = samplesPath;
	}
	
	@Override
	public void create() {
		
		createSceneManager();
		
		createUI();
		
		loadModelIndex();
		
		if(AUTOLOAD_ENTRY != null && AUTOLOAD_VARIANT != null){
			load(AUTOLOAD_ENTRY, AUTOLOAD_VARIANT);
		}
	}
	
	private void createSceneManager()
	{
		sceneManager = new SceneManager(createShaderProvider(shaderMode, 12));
		
		// set environment maps
		
		Cubemap diffuseCubemap = EnvironmentUtil.createCubemap(new ClasspathFileHandleResolver(), 
				"net/mgsx/gltf/assets/diffuse/diffuse_", "_0.jpg");

		Cubemap defaultEnvironmentCubemap = EnvironmentUtil.createCubemap(new ClasspathFileHandleResolver(), 
				"net/mgsx/gltf/assets/environment/environment_", "_0.png");

		Cubemap altEnvironmentCubemap = EnvironmentUtil.createCubemap(new ClasspathFileHandleResolver(), 
				"net/mgsx/gltf/assets/demo_skybox_", ".png");
		
		Cubemap environmentCubemap = USE_DEFAULT_ENV_MAP ? defaultEnvironmentCubemap : altEnvironmentCubemap;
		
		Cubemap mipmapCubemap = EnvironmentUtil.createCubemap(new ClasspathFileHandleResolver(), 
				"net/mgsx/gltf/assets/specular/specular_", "_", ".jpg", 10);
		
		sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));
		
		sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(mipmapCubemap));

		sceneManager.setSkyBox(new SceneSkybox(environmentCubemap));
		
		// light direction based on environnement map SUN
		sceneManager.directionalLights.first().direction.set(-.5f,-.5f,-.7f).nor();
	}
	
	private void loadModelIndex() 
	{
		rootFolder = Gdx.files.internal(samplesPath);	
		
		FileHandle file = rootFolder.child("model-index.json");
		
		entries = new Json().fromJson(Array.class, ModelEntry.class, file);
		
		ui.entrySelector.setItems(entries);
	}

	private void createUI()
	{
		stage = new Stage(new ScreenViewport());
		Gdx.input.setInputProcessor(stage);
		skin = new Skin(Gdx.files.internal("skins/uiskin.json"));
		Table base = new Table(skin);
		base.setFillParent(true);
		
		base.defaults().expand().top().left();
		
		stage.addActor(base);
		
		base.add(ui = new GLTFDemoUI(skin));
		
		ui.shaderSelector.setSelected(shaderMode);
		
		ui.lightDirectionControl.set(sceneManager.directionalLights.first().direction);
		
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
	}
	
	protected void setImage(ModelEntry entry) {
		if(entry.screenshot != null){
			if(entry.url != null){
				HttpRequest httpRequest = new HttpRequest(HttpMethods.GET);
				httpRequest.setUrl(entry.url + entry.screenshot);

				Gdx.net.sendHttpRequest(httpRequest, new SafeHttpResponseListener(){
					@Override
					protected void handleData(byte[] bytes) {
						Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
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
		sceneManager.batch.dispose();
		sceneManager.batch = new ModelBatch(createShaderProvider(shaderMode, rootModel.maxBones));
	}
	
	private ShaderProvider createShaderProvider(ShaderMode shaderMode, int maxBones){
		switch(shaderMode){
		default:
		case FLAT:
		case GOURAUD:
		case PHONG:
			Config config = new DefaultShader.Config();
			config.numBones = maxBones;
			return new DefaultShaderProvider(config);
		case PBR_SG:
			// TODO SG shader variant
		case PBR_MR:
			return PBRShaderProvider.createDefault(maxBones);
		}
	}

	private void load(String entryName, String variant) {
		for(ModelEntry item : ui.entrySelector.getItems()){
			if(item.name.equals(entryName)){
				load(item, variant);
				return;
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

	private void load(ModelEntry entry, String variant) {
		
		if(scene != null){
			sceneManager.removeScene(scene);
		}
		
		if(rootModel != null){
			rootModel.dispose();
		}
		
		if(variant.isEmpty()) return;
		
		final String fileName = entry.variants.get(variant);
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
			
			Gdx.app.log(TAG, "loading " + fileName);
			
			if(fileName.endsWith(".gltf")){
				rootModel = new GLTFLoader().load(glFile, baseFolder);
			}else if(fileName.endsWith(".glb")){
				rootModel = new GLBLoader().load(glFile);
			}else{
				throw new GdxRuntimeException("unknown file extension " + glFile.extension());
			}
			
			load();
			
			Gdx.app.log(TAG, "loaded " + glFile.path());
		}
	}
	
	private void load()
	{
		scene = new Scene(rootModel.scene, true);
		
		// XXX patch animation because of overload ....
		for(Animation anim : rootModel.scene.animations){
			Animation newAnim = new Animation();
			newAnim.id = anim.id;
			newAnim.duration = anim.duration;
			for(NodeAnimation nodeAnim : anim.nodeAnimations){
				NodeAnimationPlus newNodeAnim = new NodeAnimationPlus();
				newNodeAnim.set(nodeAnim);
				newAnim.nodeAnimations.add(newNodeAnim);
			}
			scene.modelInstance.animations.add(anim);
		}
		
		ui.setMaterials(scene.modelInstance.materials);
		ui.setAnimations(rootModel.animations);
		ui.setCameras(rootModel.cameraMap);
		ui.setNodes(NodeUtil.getAllNodes(new Array<Node>(), scene.modelInstance));
		
		setShader(shaderMode);
		
		sceneManager.addScene(scene);
	}
	
	protected void setCamera(String name) 
	{
		if(name == null) return;
		if(name.isEmpty()){
			PerspectiveCamera camera = new PerspectiveCamera(60, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			camera.up.set(Vector3.Y);
			
			BoundingBox bb = scene.modelInstance.calculateBoundingBox(new BoundingBox());
			
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
			
			
			sceneManager.setCamera(camera);
		}else{
			Camera camera = rootModel.createCamera(name);
			Node cameraNode = scene.modelInstance.getNode(name, true);
			cameraControl = new CameraInputController(camera);
			sceneManager.setCamera(camera, cameraNode);
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

		sceneManager.update(delta);
		
		if(cameraControl != null){
			cameraControl.update();
		}
		
		float l = 0f;
		
		Gdx.gl.glClearColor(l,l,l, 0);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		
		sceneManager.setAmbiantLight(ui.ambiantSlider.getValue());
		
		float IBLScale = ui.lightFactorSlider.getValue();
		PBRShader.ScaleIBLAmbient.r = ui.ambiantSlider.getValue() * IBLScale;
		PBRShader.ScaleIBLAmbient.g = ui.specularSlider.getValue() * IBLScale;
		
		float lum = ui.lightSlider.getValue();
		sceneManager.directionalLights.first().color.set(lum, lum, lum, 1);
		sceneManager.directionalLights.first().direction.set(ui.lightDirectionControl.value).nor();
		
		sceneManager.directionalLights.first().color.r *= IBLScale;
		sceneManager.directionalLights.first().color.g *= IBLScale;
		sceneManager.directionalLights.first().color.b *= IBLScale;
		
		sceneManager.render();
		
		stage.draw();
	}
	
}

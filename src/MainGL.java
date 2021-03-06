import camera.Camera;
import model.*;
import model.shape.Cube;
import model.shape.DrawData;
import movement.MovementHandler;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import render.NormalRenderer;
import util.Matrix4f;
import util.Vector3f;
import util.Vector4f;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Created by msi1 on 7/5/2018.
 */
public class MainGL
{
    public static void main(String[] args) throws IOException
    {
        new MainGL().start();
    }

    private long window;
    private ArrayList<Integer> vaos;
    private ArrayList<Integer> vbos;
    private int windowWidth;
    private int windowHeight;
    private GLCallbackHandler callbackHandler;
    private MovementHandler movementHandler;

    private static final float FOV = 70.0f;
    private static final float NEAR_PLANE = 0.01f;
    private static final float FAR_PLANE = 10000.0f;
    private Matrix4f projectionMatrix;
    private Vector3f diffuseColor; // TODO change this with time
    private Camera camera;

    private HashMap<String, GLObject> objectsMap;
    private ArrayList<GLObject> objects;
    private ArrayList<Tree> trees;
    private ArrayList<Grass> grasses;
    private NormalRenderer normalRenderer;
    private ParticleMaster particleMaster;

    private int startPositionIndexForGrassCubes;
    private int startNormalIndexForGrassCubes;
    private int startTextureIndexForGrassCubes;

    public MainGL()
    {
        this.vaos = new ArrayList<>();
        this.vbos = new ArrayList<>();
        this.objects = new ArrayList<>();
        this.trees = new ArrayList<>();
        this.grasses = new ArrayList<>();
        this.objectsMap = new HashMap<>();
        this.diffuseColor = new Vector3f(1.0f, 1.0f, 1.0f);
        this.camera = new Camera(new Vector3f(0.0f, 8.5f / 25.0f, 8.0f / 25.0f));
        this.movementHandler = new MovementHandler(camera, objectsMap, trees);
        this.callbackHandler = new GLCallbackHandler(camera, movementHandler);
        this.windowWidth = 1200;
        this.windowHeight = 800;
    }

    private void start() throws FileNotFoundException
    {
        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
        System.exit(0);
    }

    private void init() throws FileNotFoundException
    {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, "Hello World!", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);

        GL.createCapabilities();

        callbackHandler.setWindow(window);
        createProjectionMatrix();
        addObjects();
        addTrees();
//        addGrasses();
        addCallbacks();
        movementHandler.startThread();
        createVao();
        setVao();
        createRenderers();
        startLightingThread();
    }

    private void addGrasses()
    {
        Grass firstGrass = new Grass(new Vector3f(0.0f, 15.2f / 25.0f, -16.0f / 25.0f), new Vector3f(0.0f, 0.5f, 0.0f));
        grasses.add(firstGrass);

        particleMaster = new ParticleMaster(grasses);
    }

    private void addTrees()
    {
        Cube firstTreeBody = new Cube(new Vector3f(0.0f, 10.6f / 25.0f, -30.0f / 25.0f), 1.0f / 25.0f, 10.f / 25.0f, 1.0f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg");
        Tree firstTree = new Tree(firstTreeBody, 4, 1);
        trees.add(firstTree);

        Cube secondTreeBody = new Cube(new Vector3f(0.0f / 25.0f, 10.6f / 25.0f, 30.0f / 25.0f), 1.0f / 25.0f, 10.f / 25.0f, 1.0f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg");
        Tree secondTree = new Tree(secondTreeBody, 3, 1);
        trees.add(secondTree);

        Cube threeTreeBody = new Cube(new Vector3f(25.0f / 25.0f, 10.6f / 25.0f, -30.0f / 25.0f), 1.0f / 25.0f, 10.f / 25.0f, 1.0f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg");
        Tree threeTree = new Tree(threeTreeBody, 3, 1);
        trees.add(threeTree);
    }

    private void startLightingThread()
    {
        new Thread(() ->
        {
            Vector3f goodLight = new Vector3f(1.0f, 1.0f, 1.0f);
            Vector3f mediumLight = new Vector3f(0.5f, 0.5f, 0.5f);
            Vector3f zeroLight = new Vector3f(0.0f, 0.0f, 0.0f);

            while (true)
            {
                try
                {
                    diffuseColor = goodLight;
                    Thread.sleep(5000);
                    diffuseColor = mediumLight;
                    Thread.sleep(50);
                    diffuseColor = goodLight;
                    Thread.sleep(50);
                    diffuseColor = mediumLight;
                    Thread.sleep(50);
                    diffuseColor = goodLight;
                    Thread.sleep(50);
                    diffuseColor = mediumLight;
                    Thread.sleep(50);
                    diffuseColor = goodLight;
                    Thread.sleep(50);
                    diffuseColor = mediumLight;
                    Thread.sleep(50);
                    diffuseColor = zeroLight;
                    Thread.sleep(5000);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void createRenderers() throws FileNotFoundException
    {
        normalRenderer = new NormalRenderer("NormalVertexShader.vert",
                "NormalFragmentShader.frag", vaos.get(0));
        normalRenderer.loadProjectionMatrix(projectionMatrix);
    }

    private void setVao()
    {
        ArrayList<DrawData> drawData = collectDrawData();
        ArrayList<Float[]> positions = collectPositions(drawData);
        ArrayList<Float[]> normals = collectNormals(drawData);
        ArrayList<Float[]> textureCoordinates = collectTextureCoordinates(drawData);

        startPositionIndexForGrassCubes = setVaoIndex(positions, 0, 3);
        startTextureIndexForGrassCubes = setVaoIndex(textureCoordinates, 1, 2);
        startNormalIndexForGrassCubes = setVaoIndex(normals, 2, 3);
    }

    private ArrayList<Float[]> collectTextureCoordinates(ArrayList<DrawData> drawData)
    {
        ArrayList<Float[]> textureCoordinates = new ArrayList<>();

        for (DrawData data : drawData)
        {
            textureCoordinates.add(data.getTextureCoordinates());
        }

        return textureCoordinates;
    }

    private ArrayList<Float[]> collectNormals(ArrayList<DrawData> drawData)
    {
        ArrayList<Float[]> normals = new ArrayList<>();

        for (DrawData data : drawData)
        {
            normals.add(data.getNormals());
        }

        return normals;
    }

    private ArrayList<Float[]> collectPositions(ArrayList<DrawData> drawData)
    {
        ArrayList<Float[]> positions = new ArrayList<>();

        for (DrawData data : drawData)
        {
            positions.add(data.getVertices());
        }

        return positions;
    }

    private ArrayList<DrawData> collectDrawData()
    {
        ArrayList<DrawData> drawData = new ArrayList<>();

        for (GLObject object : objects)
        {
            ArrayList<Cube> cubes = object.getCubicParts();
            for (Cube cube : cubes)
            {
                drawData.add(cube.getDrawData());
            }
        }

        for (Tree tree : trees)
        {
            ArrayList<Cube> cubes = tree.getCubes();
            for (Cube cube : cubes)
            {
                drawData.add(cube.getDrawData());
            }
        }

        return drawData;
    }

    private int setVaoIndex(ArrayList<Float[]> list, int index, int size)
    {
        int vaoId = vaos.get(0);
        int finalSize = GLUtil.findSize(list);
        GL30.glBindVertexArray(vaoId);

        try(MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer buffer = stack.mallocFloat(finalSize);
            for (Float[] array : list)
            {
                buffer.put(GLUtil.toPrimitiveFloatArray(array));
            }

            buffer.flip();

            int vboId = GL15.glGenBuffers();
            vbos.add(vboId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, 4 * size, 0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        GL30.glBindVertexArray(0);

        return finalSize;
    }

    private ArrayList<Cube> loadGrasses()
    {
        ArrayList<Cube> allCubes = new ArrayList<>();
        ArrayList<DrawData> allDrawData = new ArrayList<>();

        for (Grass grass : grasses)
        {
            allCubes.addAll(grass.getCubes());
        }

        for (Cube cube : allCubes)
        {
            allDrawData.add(cube.getDrawData());
        }

        ArrayList<Float[]> positions = collectPositions(allDrawData);
        ArrayList<Float[]> normals = collectNormals(allDrawData);
        ArrayList<Float[]> textureCoordinates = collectTextureCoordinates(allDrawData);

        setVaoIndex(positions, 0, 3, startPositionIndexForGrassCubes);
        setVaoIndex(textureCoordinates, 1, 2, startTextureIndexForGrassCubes);
        setVaoIndex(normals, 2, 3, startNormalIndexForGrassCubes);

        return allCubes;
    }

    private void setVaoIndex(ArrayList<Float[]> list, int index, int size, int startingIndex)
    {
        int vaoId = vaos.get(0);
        GL30.glBindVertexArray(vaoId);

        try(MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer buffer = stack.mallocFloat(GLUtil.findSize(list));
            for (Float[] array : list)
            {
                buffer.put(GLUtil.toPrimitiveFloatArray(array));
            }

            buffer.flip();

            int vboId = GL15.glGenBuffers();
            vbos.add(vboId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, 4 * size, startingIndex);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        GL30.glBindVertexArray(0);
    }

    private void createVao() // TODO this place might cause a problem
    {
        int vaoId = GL30.glGenVertexArrays();
        vaos.add(vaoId);
    }

    private void createProjectionMatrix()
    {
        float aspect = (float) windowWidth / (float) windowHeight;
        float yScale = 1.0f / (float) Math.tan(Math.toRadians(FOV / 2.0f));
        float xScale = yScale / aspect;
        float zp = FAR_PLANE + NEAR_PLANE;
        float zm = FAR_PLANE - NEAR_PLANE;

        projectionMatrix = new Matrix4f(new Vector4f(xScale, 0.0f, 0.0f, 0.0f),
                new Vector4f(0.0f, yScale, 0.0f, 0.0f), new Vector4f(0.0f, 0.0f, -zp / zm, -1.0f),
                new Vector4f(0.0f, 0.0f, -(2.0f*FAR_PLANE*NEAR_PLANE)/zm, 0));
    }

    private void addCallbacks()
    {
        glfwSetKeyCallback(window, callbackHandler.getKeyCallback());
        glfwSetMouseButtonCallback(window, callbackHandler.getMouseButtonCallback());
        glfwSetCursorPosCallback(window, callbackHandler.getCursorPosCallback());
    }

    private void addObjects()
    {
//        Front Wall
        GLObject frontWall = new GLObject();
        frontWall.addCube(new Vector3f(0f / 25.0f, 7.5f / 25.0f, 11.0f / 25.0f), 20f / 25.0f,
                15f / 25.0f, 1f / 25.0f, Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Front Wall", frontWall);
        objects.add(frontWall);

        //Left Wall
        GLObject leftWall = new GLObject();
        leftWall.addCube(new Vector3f(10f / 25.0f, 7.5f / 25.0f, 1f / 25.0f), 1f / 25.0f, 15f / 25.0f,
                20f / 25.0f, Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Left Wall", leftWall);
        objects.add(leftWall);

        //Right Wall
        GLObject rightWall = new GLObject();
        rightWall.addCube(new Vector3f(-10f / 25.0f, 1.75f / 25.0f, 1f / 25.0f), 1f / 25.0f, 3.5f / 25.0f, 20f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        rightWall.addCube(new Vector3f(-10f / 25.0f, 11.75f / 25.0f, 1f / 25.0f), 1f / 25.0f, 6.5f / 25.0f, 20f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        rightWall.addCube(new Vector3f(-10f / 25.0f, 6.0f / 25.0f, -5.25f / 25.0f), 1 / 25.0f, 5f / 25.0f, 7.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        rightWall.addCube(new Vector3f(-10f / 25.0f, 6.0f / 25.0f, 7.25f / 25.0f), 1.0f / 25.0f, 5.0f / 25.0f, 7.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Right Wall", rightWall);
        objects.add(rightWall);
//
        //Back Wall
        GLObject backWall = new GLObject();
        backWall.addCube(new Vector3f(0f / 25.0f, 2.5f / 25.0f, -9f / 25.0f), 20f / 25.0f, 5f / 25.0f, 1f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        backWall.addCube(new Vector3f(-6.25f / 25.0f, 10.0f / 25.0f, -9f / 25.0f), 7.5f / 25.0f, 10f / 25.0f, 1f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        backWall.addCube(new Vector3f(6.25f / 25.0f, 10.0f / 25.0f, -9f / 25.0f), 7.5f / 25.0f, 10f / 25.0f, 1f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Back Wall", backWall);
        objects.add(backWall);
//
        //Roof
        GLObject roof = new GLObject();
        roof.addCube(new Vector3f(0f / 25.0f, 0f / 25.0f, 1f / 25.0f), 20f / 25.0f, 1f / 25.0f, 20f / 25.0f,
                Visibility.VisibleOutside, "textures\\wallTile.jpg", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Roof", roof);
        objects.add(roof);

//        //Table
        GLObject table = new GLObject();
        table.addCube(new Vector3f(-1f / 25.0f, 12f / 25.0f, 0f / 25.0f), 6f / 25.0f, 0.5f / 25.0f, 4f / 25.0f,
                Visibility.VisibleOutside, "textures\\tableTile.jpg", 5f / 25.0f, 5f / 25.0f);
        table.addCube(new Vector3f(-3.3f / 25.0f, 13.5f / 25.0f, 1.3f / 25.0f), 0.5f / 25.0f, 3f / 25.0f, 0.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\tableTile.jpg", 5f / 25.0f, 5f / 25.0f);
        table.addCube(new Vector3f(1.3f / 25.0f, 13.5f / 25.0f, 1.3f / 25.0f), 0.5f / 25.0f, 3f / 25.0f, 0.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\tableTile.jpg", 5f / 25.0f, 5f / 25.0f);
        table.addCube(new Vector3f(-3.3f / 25.0f, 13.5f / 25.0f, -1.3f / 25.0f), 0.5f / 25.0f, 3f / 25.0f, 0.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\tableTile.jpg", 5f / 25.0f, 5f / 25.0f);
        table.addCube(new Vector3f(1.3f / 25.0f, 13.5f / 25.0f, -1.3f / 25.0f), 0.5f / 25.0f, 3f / 25.0f, 0.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\tableTile.jpg", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Table", table);
        objects.add(table);
//
        //Window
        GLObject window = new GLObject(new Vector3f(-10f / 25.0f, 6f / 25.0f, 3.5f / 25.0f), RotationAxisType.ParallelY);
        window.addCube(new Vector3f(-10f / 25.0f, 6f / 25.0f, 1f / 25.0f), 1f / 25.0f, 5f / 25.0f, 5f / 25.0f,
                Visibility.VisibleOutside, "textures\\glassTile.png", 5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Window", window);
        objects.add(window);
//
//        //Door
        GLObject door = new GLObject(new Vector3f(-2.5f / 25.0f, 10f / 25.0f, -9f / 25.0f), RotationAxisType.ParallelY);
        door.addCube(new Vector3f(0f / 25.0f, 10f / 25.0f, -9f / 25.0f), 5f / 25.0f, 10f / 25.0f, 1f / 25.0f,
                Visibility.VisibleOutside, "textures\\tableTile.jpg", "Door", 5f / 25.0f, 5f / 25.0f);
        door.addCube(new Vector3f(2f / 25.0f, 10f / 25.0f, -8.25f / 25.0f), 0.5f / 25.0f, 0.5f / 25.0f, 0.5f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg", "Major Knob",5f / 25.0f, 5f / 25.0f);
        door.addCube(new Vector3f(1.5f / 25.0f, 10f / 25.0f, -8.25f / 25.0f), 0.5f / 25.0f, 0.25f / 25.0f, 0.25f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg", "Minor Knob",5f / 25.0f, 5f / 25.0f);
        objectsMap.put("Door", door);
        objects.add(door);
//
//
//        //Clock
        GLObject clock = new GLObject();
        clock.addCube(new Vector3f(9.4f / 25.0f, 4f / 25.0f, 1f / 25.0f), 0.2f / 25.0f, 2f / 25.0f, 2f / 25.0f,
                Visibility.VisibleOutside, "textures\\clock.jpg");
        clock.addCube(new Vector3f(9.3f / 25.0f, 3.55f / 25.0f, 1f / 25.0f), 0.2f / 25.0f, 0.9f / 25.0f, 0.1f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg", "Minute bar", 2f, 2f);
        clock.addCube(new Vector3f(9.3f / 25.0f, 4f / 25.0f, 1.35f / 25.0f), 0.2f / 25.0f, 0.1f / 25.0f, 0.7f / 25.0f,
                Visibility.VisibleOutside, "textures\\knobTile.jpg", "Hour bar", 2f, 2f);
        clock.setCubeTransformationData("Minute bar", new Vector3f(9.6f / 25.0f, 4f / 25.0f, 1f / 25.0f), RotationAxisType.ParallelX);
        clock.setCubeTransformationData("Hour bar", new Vector3f(9.6f / 25.0f, 4f / 25.0f, 1f / 25.0f), RotationAxisType.ParallelX);
        objectsMap.put("Clock", clock);
        objects.add(clock);

        // Floor
        GLObject floor = new GLObject();
        floor.addCube(new Vector3f(0.0f, 15.5f / 25.0f, 1.0f / 25.0f), 20.0f / 25.0f, 1.0f / 25.0f, 20.0f / 25.0f,
                Visibility.VisibleOutside, "textures\\floorTile.jpg", 2.0f / 25.0f, 2.0f / 25.0f);
        objectsMap.put("Floor", floor);
        objects.add(floor);

        // outside lol
        GLObject yard = new GLObject();
        yard.addCube(new Vector3f(0.0f, 15.6f / 25.0f, 1.0f / 25.0f), 100.0f / 25.0f, 1.0f / 25.0f, 100.0f / 25.0f,
                Visibility.VisibleOutside, "textures\\yardTile.jpg", 4.0f / 25.0f, 4.0f / 25.0f);
        objectsMap.put("Yard", yard);
        objects.add(yard);
//
//        //Skybox
//        GLObject skybox = new GLObject();
//        skybox.addCube(new Vector3f(25.0f / 25.0f, -10.5f / 25.0f, 25.0f / 25.0f), 0f / 25.0f, 12.5f / 25.0f, 0f / 25.0f,
//                Visibility.VisibleInside, "textures\\sky.jpg");
//        objectsMap.put("Skybox", skybox);
//        objects.add(skybox);

    }

    private void loop() {
        // Set the clear color
        glClearColor(0.4f, 0.7f, 1.0f, 1.0f);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        GLObject door = objectsMap.get("Door");
        GLObject windowObj = objectsMap.get("Window");

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while ( !glfwWindowShouldClose(window) ) {
            glEnable(GL_DEPTH_TEST);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

//            for (GLObject object : objects)
//            {
//                normalRenderer.render(object, camera, diffuseColor);
//            }
//            for (Grass grass : grasses)
//            {
//                grass.addParticle();
//            }
//
//            ArrayList<Cube> cubes = loadGrasses();

            int cubeCounter = 0;

            for (GLObject object : objects)
            {
                float selectionEffect = 1.0f;
                float alpha = 1.0f;

                if ((object == door && movementHandler.isDoorSelected()) || (object == windowObj && movementHandler.isWindowSelected()))
                {
                    selectionEffect = 0.5f;
                }
                if (object == windowObj)
                {
                    alpha = 0.5f;
                }

                normalRenderer.render(object, camera, diffuseColor, cubeCounter, selectionEffect, alpha);
                cubeCounter += object.getCubicParts().size();
            }

            for (Tree tree : trees)
            {
                float selectionEffect = 1.0f;
                float alpha = 1.0f;

                normalRenderer.render(tree, camera, diffuseColor, cubeCounter, selectionEffect, alpha);
                cubeCounter += tree.getNumberOfTrees();
            }

//            for (Cube cube : cubes)
//            {
//                float selectionEffect = 1.0f;
//                float alpha = 1.0f;
//
//                normalRenderer.renderParticleCube(cube, camera, diffuseColor, cubeCounter, selectionEffect, alpha);
//                cubeCounter++;
//            }

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
            glfwSwapBuffers(window); // swap the color buffers
        }

        cleanUp();
    }

    private void cleanUp()
    {
        for (int vaoId : vaos)
        {
            GL30.glDeleteVertexArrays(vaoId);
        }

        for (int vboId : vbos)
        {
            GL15.glDeleteBuffers(vboId);
        }

        normalRenderer.cleanUp();

        // TODO clean cubes and objects here?
    }
}

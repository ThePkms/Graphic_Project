import camera.Camera;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import util.Matrix4f;
import util.Vector3f;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;

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
    private Matrix4f projectionMatrix;
    private Camera camera;
    private static final float FOV = 70.0f;
    private static final float NEAR_PLANE = 0.01f;
    private static final float FAR_PLANE = 10000.0f;
    private int windowWidth;
    private int windowHeight;

    public MainGL()
    {
        this.vaos = new ArrayList<>();
        this.vbos = new ArrayList<>();
        this.camera = new Camera(new Vector3f(0, 0, 0), 0, 0, 0);
        windowWidth = 1200;
        windowHeight = 800;
    }

    private void start() throws FileNotFoundException
    {
        init();

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

        // TODO create cubes and objects here?
    }

    private void loop() {
        // Set the clear color
        glClearColor(0.4f, 0.7f, 1.0f, 1.0f);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while ( !glfwWindowShouldClose(window) ) {
            glEnable(GL_DEPTH_TEST);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            // TODO the rendering must happen here, we MUST do it clean, I'm still not sure exactly how

            // TODO We have to handle poll events too, don't think about them now, I know how to them

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

        // TODO clean cubes and objects here?
    }
}
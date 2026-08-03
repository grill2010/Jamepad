package com.studiohartman.jamepad;

import com.badlogic.gdx.jnigen.loader.SharedLibraryLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * This class handles initializing the native library, connecting to controllers, and managing the
 * list of controllers.
 *
 * Generally, after creating a ControllerManager object and calling initSDLGamepad() on it, you
 * would access the states of the attached gamepads by calling getState().
 *
 * For some applications (but probably very few), getState may have a performance impact. In this
 * case, it may make sense to use the getControllerIndex() method to access the objects used
 * internally by  ControllerManager.
 *
 * @author William Hartman
 */
public class ControllerManager {
    /*JNI

    #include <SDL3/SDL.h>
    #include <stdio.h>

    static int lastGamepadCount = -1;

    static int jamepad_count_gamepads() {
        int count = 0;
        SDL_JoystickID *ids = SDL_GetGamepads(&count);
        if (ids != NULL) {
            SDL_free(ids);
        }
        return count;
    }

    // Pulls only device add/remove events off the queue. Draining the whole queue
    // would eat events belonging to any other SDL user in the process.
    static bool jamepad_take_device_events() {
        SDL_Event drained[32];
        bool sawAny = false;

        while (SDL_PeepEvents(drained, 32, SDL_GETEVENT,
                              SDL_EVENT_JOYSTICK_ADDED, SDL_EVENT_JOYSTICK_REMOVED) > 0) {
            sawAny = true;
        }
        while (SDL_PeepEvents(drained, 32, SDL_GETEVENT,
                              SDL_EVENT_GAMEPAD_ADDED, SDL_EVENT_GAMEPAD_REMOVED) > 0) {
            sawAny = true;
        }

        return sawAny;
    }
    */

    private static final boolean IS_UNIX = System.getProperty("os.name", "").toLowerCase().contains("nix") ||
            System.getProperty("os.name", "").toLowerCase().contains("nux");

    private final Configuration configuration;
    private final String mappingsPath;
    private boolean isInitialized;
    private ControllerIndex[] controllers;
    private SystemMotionSensors systemMotionSensors;

    /**
     * Default constructor. Makes a manager for 4 controllers with the built in mappings from here:
     * https://github.com/gabomdq/SDL_GameControllerDB
     */
    public ControllerManager() {
        this(new Configuration(), "/gamecontrollerdb.txt");
    }

    /**
     * Constructor. Uses built-in mappings from here: https://github.com/gabomdq/SDL_GameControllerDB
     *
     * @param configuration see {@link Configuration and its fields}
     */
    public ControllerManager(Configuration configuration) {
        this(configuration, "/gamecontrollerdb.txt");
    }

    /**
     * Constructor.
     *
     * @param mappingsPath The path to a file containing SDL controller mappings.
     * @param configuration see {@link Configuration and its fields}
     */
    public ControllerManager(Configuration configuration, String mappingsPath) {
        this.configuration = configuration;
        this.mappingsPath = mappingsPath;
        isInitialized = false;
        controllers = new ControllerIndex[configuration.maxNumControllers];

        if (configuration.loadNativeLibrary) {
            new SharedLibraryLoader().load("jamepad");
        }
    }

    /**
     * Set a hint with normal priority.
     * @param name the hint to set
     * @param value the value of the hint variable
     * @return true if the hint was set, false otherwise.
     */
    public boolean setSdlHint(String name, String value) {
        return nativeSetSdlHint(name, value);
    }

    private native boolean nativeSetSdlHint(String name, String value); /*
        return SDL_SetHint(name, value) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Initialize the ControllerIndex library. This loads the native library and initializes SDL
     * in the native code.
     *
     * @throws IllegalStateException If the native code fails to initialize or if SDL is already initialized
     */
    public void initSDLGamepad() throws IllegalStateException {
        if(isInitialized) {
            throw new IllegalStateException("SDL is already initialized!");
        }
        Configuration.SonyControllerFeature sonyControllerFeature = configuration.useSonyControllerFeatures;

        //Initialize SDL
        if (!nativeInitSDLGamepad(!configuration.useRawInput, sonyControllerFeature.getValue(),
                configuration.useControllerMotionSensors, configuration.useSystemMotionSensors)) {
            throw new IllegalStateException("Failed to initialize SDL in native method!");
        } else {
            isInitialized = true;
        }

        if (configuration.useSystemMotionSensors) {
            systemMotionSensors = new SystemMotionSensors();
        }

        if(Objects.equals(Configuration.SonyControllerFeature.DUALSENSE_FEATURES_AND_HAPTICS, sonyControllerFeature)) {
            if(IS_UNIX){
                String audioDriverName = getCurrentAudioDriverName();
                if(!nativeInitHapticsOnUnix()) {
                    System.err.println("Failed to load SLD Audio for DualSense haptics with pipewire" + getLastNativeError() + ". Try again with standard driver");
                    if(!nativeInitHaptics(audioDriverName == null || audioDriverName.isEmpty() ? "pulse" : audioDriverName)) {
                        System.err.println("Failed to load SLD Audio for DualSense haptics " + getLastNativeError());
                        sonyControllerFeature = Configuration.SonyControllerFeature.DUALSENSE_FEATURES; // Fallback
                    }
                }
            } else {
                if(!nativeInitHaptics()) {
                    System.err.println("Failed to load SLD Audio for DualSense haptics " + getLastNativeError());
                    sonyControllerFeature = Configuration.SonyControllerFeature.DUALSENSE_FEATURES; // Fallback
                }
            }
        }

        //Set controller mappings. The possible exception is caught, since stuff will still work ok
        //for most people if mapping aren't set.
        try {
            addMappingsFromFile(mappingsPath);
        } catch (IOException | IllegalStateException e) {
            System.err.println("Failed to load mapping with original location \"" + mappingsPath + "\", " +
                    "Falling back of SDL's built in mappings");
            e.printStackTrace();
        }

        //Connect and keep track of the controllers
        for(int i = 0; i < controllers.length; i++) {
            controllers[i] = new ControllerIndex(i, sonyControllerFeature, configuration.useControllerMotionSensors);
        }
    }
    private native boolean nativeInitSDLGamepad(boolean disableRawInput, int sonyControllerFeature,
                                                boolean useControllerMotionSensors,
                                                boolean useSystemMotionSensors); /*
        if (disableRawInput) {
            SDL_SetHint(SDL_HINT_JOYSTICK_RAWINPUT, "0");
        }
        if (useControllerMotionSensors) {
            // Motion data arrives in the extended report, which some drivers only send once asked.
            SDL_SetHint(SDL_HINT_JOYSTICK_ENHANCED_REPORTS, "1");
        }
        if(sonyControllerFeature != 0) {
            // SDL 3 folded SDL_HINT_JOYSTICK_HIDAPI_PS4_RUMBLE and ..._PS5_RUMBLE into this one hint.
            SDL_SetHint(SDL_HINT_JOYSTICK_ENHANCED_REPORTS, "1");
            SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI_PS4, "1");
            SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI_PS5, "1");
            SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI_PS5_PLAYER_LED, "1");
            SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
        }

        SDL_InitFlags initFlags = SDL_INIT_EVENTS | SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD;
        if (useSystemMotionSensors) {
            initFlags |= SDL_INIT_SENSOR;
        }

        if (!SDL_Init(initFlags)) {
            printf("NATIVE METHOD: SDL_Init failed: %s\n", SDL_GetError());
            return JNI_FALSE;
        }

        //Sensor updates are high frequency. Nobody drains them unless motion is switched on,
        //so keep them off the queue entirely in that case.
        SDL_SetEventEnabled(SDL_EVENT_GAMEPAD_SENSOR_UPDATE, useControllerMotionSensors);
        SDL_SetEventEnabled(SDL_EVENT_SENSOR_UPDATE, useSystemMotionSensors);

        //SDL emits a device-added event for every controller that is already plugged in,
        //which would otherwise look like a hotplug the first time update() runs.
        SDL_PumpEvents();
        jamepad_take_device_events();
        lastGamepadCount = jamepad_count_gamepads();

        return JNI_TRUE;
    */

    private native boolean nativeInitHaptics(String audioDriverName); /*
        if(SDL_WasInit(SDL_INIT_AUDIO) != 0) {
            return JNI_TRUE;
        }
        SDL_SetHint(SDL_HINT_AUDIO_DRIVER, audioDriverName);

        return SDL_InitSubSystem(SDL_INIT_AUDIO) ? JNI_TRUE : JNI_FALSE;
    */

    private native boolean nativeInitHaptics(); /*
        if(SDL_WasInit(SDL_INIT_AUDIO) != 0) {
            return JNI_TRUE;
        }

        return SDL_InitSubSystem(SDL_INIT_AUDIO) ? JNI_TRUE : JNI_FALSE;
    */

    private native boolean nativeInitHapticsOnUnix(); /*
        if(SDL_WasInit(SDL_INIT_AUDIO) != 0) {
            return JNI_TRUE;
        }
        SDL_SetHint(SDL_HINT_AUDIO_DRIVER, "pipewire"); // Seems to work better with controller haptics

        return SDL_InitSubSystem(SDL_INIT_AUDIO) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * This method quits all the native stuff. Call it when you're done with Jamepad.
     */
    public void quitSDLGamepad() {
        for(ControllerIndex c: controllers) {
            c.close();
        }
        if (systemMotionSensors != null) {
            systemMotionSensors.close();
            systemMotionSensors = null;
        }
        nativeCloseSDLGamepad();
        controllers = new ControllerIndex[0];
        isInitialized = false;
    }
    private native void nativeCloseSDLGamepad(); /*
        SDL_Quit();
    */

    /**
     * The motion sensors built into this machine, for handhelds whose IMU is not part of a
     * gamepad. Requires {@link Configuration#useSystemMotionSensors}.
     *
     * <p>Controllers that report their own motion, including the Steam Deck, are read
     * through {@link ControllerIndex#getSensorState()} instead.
     *
     * @return the system sensors, or null if they were not requested
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public SystemMotionSensors getSystemMotionSensors() throws IllegalStateException {
        verifyInitialized();
        return systemMotionSensors;
    }

    /**
     * Return the state of a controller at the passed index. This is probably the way most people
     * should use this library. It's simpler and less verbose, and controller connections and
     * disconnections are automatically handled.
     *
     * Also, no exceptions are thrown here (unless Jamepad isn't initialized), so you don't need
     * to have a million try/catches or anything.
     *
     * The returned state is immutable. This means an object is allocated every time you call this
     * (unless the controller is disconnected). This shouldn't be a big deal (even for games) if your
     * GC is tuned well, but if this is a problem for you, you can go directly through the internal
     * ControllerIndex objects using getControllerIndex().
     *
     * update() is called each time this method is called. Buttons are also queried, so values
     * returned from isButtonJustPressed() in ControllerIndex may not be what you expect. Calling
     * this method will have side effects if you are using the ControllerIndex objects yourself.
     * This should be fine unless you are mixing and matching this method with ControllerIndex
     * objects, which you probably shouldn't do anyway.
     *
     * @param index The index of the controller to be checked
     * @return The state of the controller at the passed index.
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public ControllerState getState(int index) throws IllegalStateException {
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            update();
            return ControllerState.getInstanceFromController(controllers[index]);
        } else {
            return ControllerState.getDisconnectedControllerInstance();
        }
    }

    /**
     * Starts vibrating the controller at this given index. If this fails for one reason or another (e.g.
     * the controller at that index doesn't support haptics, or if there is no controller at that index),
     * this method will return false.
     *
     * Each call to this function cancels any previous rumble effect, and calling it with 0 intensity stops any rumbling.
     *
     * @param index The index of the controller that will be vibrated
     * @param leftMagnitude The intensity of the left rumble motor (0-1)
     * @param rightMagnitude The intensity of the rught rumble motor (0-1)
     * @return Whether or not vibration was successfully started
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public boolean doVibration(int index, float leftMagnitude, float rightMagnitude, int duration_ms) throws IllegalStateException {
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            try {
                return controllers[index].doVibration(leftMagnitude, rightMagnitude, duration_ms);
            } catch (ControllerUnpluggedException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Sends adaptive trigger effects to the controller at this given index.
     * It the controller is not a DualSense controller calling this function doesn't have any effect.
     *
     * DualSense trigger effect <a href="https://controllers.fandom.com/wiki/Sony_DualSense#FFB_Trigger_Modes">documentation</a>.
     *
     * @param index The index of the controller that will be used to send the adaptive trigger data
     * @param leftTriggerEffect The left trigger effect type
     * @param triggerDataLeft The left trigger adaptive data
     * @param rightTriggerEffect The right trigger effect type
     * @param triggerDataRight The right trigger adaptive data
     * @return true if the adaptive trigger data was sent successfully, false otherwise
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public boolean sendAdaptiveTriggerEffects(int index, byte leftTriggerEffect, byte[] triggerDataLeft, byte rightTriggerEffect, byte[] triggerDataRight){
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            try {
                return controllers[index].sendAdaptiveTriggerEffects(leftTriggerEffect, triggerDataLeft, rightTriggerEffect, triggerDataRight);
            } catch (ControllerUnpluggedException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Sends haptic feedback audio data to the controller at this given index.
     * Audio Data must be in 3KHZ, 2 channel, 16-bit Little-Endian PCM format.
     * It the controller is not a DualSense controller calling this function doesn't have any effect.
     *
     * @param index The index of the controller that will be used to send the adaptive trigger data
     * @param hapticFeedback the haptic feedback audio data
     * @return true if the haptic feedback audio data was sent successfully, false otherwise
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public boolean sendHapticFeedbackAudioData(int index, byte[] hapticFeedback) {
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            try {
                return controllers[index].sendHapticFeedbackAudioPacket(hapticFeedback);
            } catch (ControllerUnpluggedException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Checks if the controller at the given index supports touchpad inputs
     *
     * @param index The index of the controller to check if it has touchpad support
     * @return true if the controller supports touchpad inputs, false otherwise
     */
    public boolean isSupportingTouchpadData(int index) {
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            return controllers[index].isSupportingTouchpadData();
        }

        return false;
    }

    /**
     * Checks if the controller at the given index supports sensor data
     *
     * @param index The index of the controller to check if it has sensor data support
     * @return true if the controller supports sensor data, false otherwise
     */
    public boolean isSupportingSensorData(int index) {
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            return controllers[index].isSupportingSensorData();
        }

        return false;
    }

    /**
     * Checks if the controller at the given index supports haptic feedback
     *
     * @param index The index of the controller to check if it has haptic feedback support
     * @return true if the controller supports haptic feedback, false otherwise
     */
    public boolean isSupportingHaptics(int index) {
        verifyInitialized();

        if(index < controllers.length && index >= 0) {
            return controllers[index].isSupportingHaptics();
        }

        return false;
    }

    /**
     * Returns the ControllerIndex object with the passed index (0 for p1, 1 for p2, etc.).
     *
     * You should only use this method if you're worried about the object allocations from getState().
     * If you decide to do things this way, your code will be a good bit more verbose and you'll
     * need to deal with potential exceptions.
     *
     * It is generally safe to store objects returned from this method. They will only change internally
     * if you call quitSDLGamepad() followed by a call to initSDLGamepad().
     *
     * Calling update() will run through all the controllers to check for newly plugged in or unplugged
     * controllers. You could do this from your code, but keep that in mind.
     *
     * @param index the index of the ControllerIndex that will be returned
     * @return The internal ControllerIndex object for the passed index.
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public ControllerIndex getControllerIndex(int index) {
        verifyInitialized();
        return controllers[index];
    }

    /**
     * Return the number of controllers that are actually connected. This may disagree with
     * the ControllerIndex objects held in here if something has been plugged in or unplugged
     * since update() was last called.
     *
     * @return the number of connected controllers.
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public int getNumControllers() {
        verifyInitialized();
        return nativeGetNumRollers();
    }
    private native int nativeGetNumRollers(); /*
        return jamepad_count_gamepads();
    */

    /**
     * Refresh the connected controllers in the controller list if something has been connected or
     * unplugged.
     *
     * If there hasn't been a change in whether controller are connected or not, nothing will happen.
     *
     * @return True if the controller list was refreshed, false otherwise
     * @throws IllegalStateException if Jamepad was not initialized
     */
    public boolean update() {
        verifyInitialized();
        if (nativeControllerConnectedOrDisconnected()) {
            for (int i = 0; i < controllers.length; i++) {
                controllers[i].reconnectController();
            }
            return true;
        }
        return false;
    }

    private native boolean nativeControllerConnectedOrDisconnected(); /*
        SDL_UpdateGamepads();
        SDL_PumpEvents();

        bool changed = jamepad_take_device_events();

        int nowNum = jamepad_count_gamepads();
        if (lastGamepadCount < 0) {
            lastGamepadCount = nowNum;
        }
        if (nowNum != lastGamepadCount) {
            lastGamepadCount = nowNum;
            changed = true;
        }

        return changed ? JNI_TRUE : JNI_FALSE;
    */


    /**
     * This method adds mappings held in the specified file. The file is copied to the temp folder so
     * that it can be read by the native code (if running from a .jar for instance)
     *
     * @param path The path to the file containing controller mappings.
     * @throws IOException if the file cannot be read, copied to a temp folder, or deleted.
     * @throws IllegalStateException if the mappings cannot be applied to SDL
     */
    public void addMappingsFromFile(String path) throws IOException, IllegalStateException {
        InputStream source = getClass().getResourceAsStream(path);
        if(source==null) source = ClassLoader.getSystemResourceAsStream(path);
        if(source==null && new File(path).exists()) source = new FileInputStream(path);
        if(source==null) throw new IOException("Cannot open resource from classpath "+path);

        if(configuration.loadDatabaseInMemory) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int read;
            byte[] data = new byte[4096];

            while((read = source.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, read);
            }
            source.close();

            byte[] b = buffer.toByteArray();
            if(!nativeAddMappingsFromBuffer(b, b.length)) {
                throw new IllegalStateException("Failed to set SDL controller mappings! Falling back to build in SDL mappings.");
            }
        }
        else {
            /*
            Copy the file to a temp folder. SDL can't read files held in .jars, and that's probably how
            most people would use this library.
             */
            Path extractedLoc =  Files.createTempFile(null, null).toAbsolutePath();
            
            Files.copy(source, extractedLoc, StandardCopyOption.REPLACE_EXISTING);
    
            if(!nativeAddMappingsFromFile(extractedLoc.toString())) {
                throw new IllegalStateException("Failed to set SDL controller mappings! Falling back to build in SDL mappings.");
            }
    
            Files.delete(extractedLoc);
        }
    }

    private native boolean nativeAddMappingsFromFile(String path); /*
        if(SDL_AddGamepadMappingsFromFile(path) < 0) {
            printf("NATIVE METHOD: Failed to load mappings from \"%s\"\n", path);
            printf("               %s\n", SDL_GetError());
            return JNI_FALSE;
        }

        return JNI_TRUE;
    */

    private native boolean nativeAddMappingsFromBuffer(byte[] buffer, int length); /*
        SDL_IOStream *io = SDL_IOFromMem(buffer, (size_t) length);

        if(io == NULL) {
            printf("NATIVE METHOD: Failed to create SDL_IOFromMem\n");
            printf("               %s\n", SDL_GetError());
            return JNI_FALSE;
        }

        //Closes the stream for us, on success and on failure alike.
        if(SDL_AddGamepadMappingsFromIO(io, true) < 0) {
            printf("NATIVE METHOD: Failed to load mappings from SDL_IOFromMem\n");
            printf("               %s\n", SDL_GetError());
            return JNI_FALSE;
        }

        return JNI_TRUE;
    */

    /**
     * @return last error message logged by the native lib. Use this for debugging purposes.
     */
    public native String getLastNativeError(); /*
        return env->NewStringUTF(SDL_GetError());
    */

    public native String getCurrentAudioDriverName(); /*
    const char* drv = SDL_GetCurrentAudioDriver();
    if (drv == NULL) {
        // Return empty string
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(drv);
    */

    private boolean verifyInitialized() throws IllegalStateException {
        if(!isInitialized) {
            throw new IllegalStateException("The SDL gamepad subsystem is not initialized!");
        }
        return true;
    }
}

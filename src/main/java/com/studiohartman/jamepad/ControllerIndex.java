package com.studiohartman.jamepad;

import java.util.*;

/**
 * This class is the main thing you're gonna need to deal with if you want lots of
 * control over your gamepads or want to avoid lots of ControllerState allocations.
 *
 * A Controller index cannot be made from outside the Jamepad package. You're gonna need to go
 * through a ControllerManager to get your controllers.
 *
 * A ControllerIndex represents the controller at a given index. There may or may not actually
 * be a controller at that index. Exceptions are thrown if the controller is not connected.
 *
 * @author William Hartman
 */
public final class ControllerIndex {
    /*JNI
    #include <SDL3/SDL.h>
    #include <stdio.h>
    #include <string.h>

    // Gamepad handles cross the JNI boundary as jlong, so go through intptr_t to keep
    // 32-bit targets free of precision-loss errors.
    static SDL_Gamepad *jamepad_pad(jlong controllerPtr) {
        return (SDL_Gamepad *)(intptr_t) controllerPtr;
    }

    static SDL_Joystick *jamepad_joystick(jlong controllerPtr) {
        return SDL_GetGamepadJoystick(jamepad_pad(controllerPtr));
    }

    // SDL reports the hardware sample time on sensor events only; SDL_GetGamepadSensorData
    // hands back values without one. Keep the newest timestamp per device so the polling
    // API can still report when a sample was actually taken.
    #define JAMEPAD_MAX_SENSOR_DEVICES 16

    typedef struct {
        SDL_JoystickID id;
        Uint64 accelTimestamp;
        Uint64 gyroTimestamp;
    } JamepadSensorClock;

    static JamepadSensorClock jamepad_sensor_clocks[JAMEPAD_MAX_SENSOR_DEVICES];

    static JamepadSensorClock *jamepad_sensor_clock(SDL_JoystickID id, bool create) {
        JamepadSensorClock *slot = NULL;

        for (int i = 0; i < JAMEPAD_MAX_SENSOR_DEVICES; i++) {
            if (jamepad_sensor_clocks[i].id == id) {
                return &jamepad_sensor_clocks[i];
            }
            if (slot == NULL && jamepad_sensor_clocks[i].id == 0) {
                slot = &jamepad_sensor_clocks[i];
            }
        }

        if (!create || slot == NULL) {
            return NULL;
        }

        slot->id = id;
        slot->accelTimestamp = 0;
        slot->gyroTimestamp = 0;
        return slot;
    }

    // Records every pending sensor event regardless of which device it belongs to, so it
    // does not matter which controller happens to be polled first.
    static void jamepad_take_sensor_events() {
        SDL_Event events[32];
        int count;

        while ((count = SDL_PeepEvents(events, 32, SDL_GETEVENT,
                                       SDL_EVENT_GAMEPAD_SENSOR_UPDATE,
                                       SDL_EVENT_GAMEPAD_SENSOR_UPDATE)) > 0) {
            for (int i = 0; i < count; i++) {
                const SDL_GamepadSensorEvent *event = &events[i].gsensor;
                JamepadSensorClock *clock = jamepad_sensor_clock(event->which, true);
                if (clock == NULL) {
                    continue;
                }

                if (event->sensor == SDL_SENSOR_ACCEL) {
                    clock->accelTimestamp = event->sensor_timestamp;
                } else if (event->sensor == SDL_SENSOR_GYRO) {
                    clock->gyroTimestamp = event->sensor_timestamp;
                }
            }
        }
    }

    static void jamepad_read_sensor_state(JNIEnv *env, SDL_Gamepad *pad, jobject sensorState) {
        float accel[3] = { 0.0f, 0.0f, 0.0f };
        float gyro[3] = { 0.0f, 0.0f, 0.0f };

        SDL_GetGamepadSensorData(pad, SDL_SENSOR_ACCEL, accel, 3);
        SDL_GetGamepadSensorData(pad, SDL_SENSOR_GYRO, gyro, 3);

        jamepad_take_sensor_events();

        Uint64 accelTimestamp = 0;
        Uint64 gyroTimestamp = 0;
        JamepadSensorClock *clock =
            jamepad_sensor_clock(SDL_GetJoystickID(SDL_GetGamepadJoystick(pad)), false);
        if (clock != NULL) {
            accelTimestamp = clock->accelTimestamp;
            gyroTimestamp = clock->gyroTimestamp;
        }

        jclass clazz = env->GetObjectClass(sensorState);
        jmethodID update_method = env->GetMethodID(clazz, "update", "(FFFFFFJJ)V");
        env->CallVoidMethod(sensorState, update_method,
                            accel[0], accel[1], accel[2],
                            gyro[0], gyro[1], gyro[2],
                            (jlong) accelTimestamp, (jlong) gyroTimestamp);
    }
    */

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase().contains("mac")
            || System.getProperty("os.name", "").toLowerCase().contains("darwin");

    private static final float AXIS_MAX_VAL = 32767;
    private final int index;
    private long controllerPtr;

    private final boolean[] heldDownButtons;
    private final boolean[] justPressedButtons;

    private final Configuration.SonyControllerFeature sonyControllerFeature;

    private final boolean motionSensorsRequested;

    private String controllerGuid = "";

    private boolean supportsTouchpad = false;

    private boolean supportsSensors = false;

    private boolean hasAccelerometer = false;

    private boolean hasGyroscope = false;

    private boolean supportsHaptic = false;

    private boolean needToClearTriggerEffect = false;

    private final SensorState sensorState = new SensorState();

    private final Map<Integer, TouchState> touchStates = new HashMap<>();

    private Timer hapticsTimer;

    private static final String EMPTY_GUID = "00000000000000000000000000000000";

    /**
     * Constructor. Builds a controller at the given index and attempts to connect to it.
     * This is only accessible in the Jamepad package, so people can't go trying to make controllers
     * before the native library is loaded or initialized.
     *
     * @param index The index of the controller
     * @param sonyControllerFeature The indication for the controller if it should use Sony controller
     *                                  features like the touchpad and adaptive triggers
     * @param motionSensorsRequested Whether to turn on the gyroscope and accelerometer if the
     *                                  controller has them
     */
    ControllerIndex(int index, Configuration.SonyControllerFeature sonyControllerFeature,
                    boolean motionSensorsRequested) {
        this.index = index;
        this.sonyControllerFeature = sonyControllerFeature;
        this.motionSensorsRequested = motionSensorsRequested;

        heldDownButtons = new boolean[ControllerButton.values().length];
        justPressedButtons = new boolean[ControllerButton.values().length];
        for(int i = 0; i < heldDownButtons.length; i++) {
            heldDownButtons[i] = false;
            justPressedButtons[i] = false;
        }
        connectController();
    }

    private void connectController() {
        controllerPtr = nativeConnectController(index);
        if (controllerPtr == 0) {
            controllerGuid = EMPTY_GUID;
            supportsTouchpad = false;
            supportsSensors = false;
            hasAccelerometer = false;
            hasGyroscope = false;
            supportsHaptic = false;
            return;
        }
        controllerGuid = nativeGetDeviceGuid(controllerPtr);
        if(!Objects.equals(Configuration.SonyControllerFeature.NONE, sonyControllerFeature)) {
            supportsTouchpad = nativeIsTouchpadSupported(controllerPtr);
        }
        if(motionSensorsRequested) {
            int enabledSensors = nativeEnableSensors(controllerPtr);
            hasAccelerometer = (enabledSensors & SENSOR_ACCEL) != 0;
            hasGyroscope = (enabledSensors & SENSOR_GYRO) != 0;
            supportsSensors = enabledSensors != 0;
        }
        if(nativeIsDualSenseController(controllerPtr) &&
                Objects.equals(Configuration.SonyControllerFeature.DUALSENSE_FEATURES_AND_HAPTICS, sonyControllerFeature)){
            boolean result = nativeEnableHaptics();
            if(result) {
                connectHaptics(1_000, 0);
            } else {
                System.out.println("Enable haptics failed: " + getLastNativeError());
            }
        }
    }

    private void connectHaptics(final int timeout, final int count) {
        final Timer oldTimer = hapticsTimer;
        if (oldTimer != null) {
            try {
                oldTimer.cancel();
            } catch (Throwable ignored) {
                // ignore
            }
            hapticsTimer = null;
        }

        if (!isConnected()) {
            return;
        }

        final Timer localTimer = new Timer(true); // daemon
        hapticsTimer = localTimer;

        localTimer.schedule(new TimerTask() {
            @Override public void run() {
                try {
                    if (!isConnected()) {
                        return; // If not connected anymore skip connect haptics
                    }

                    supportsHaptic = nativeConnectHaptics(IS_WINDOWS || IS_MAC, ControllerIndex.this);

                    if (!supportsHaptic) {
                        if (count == 0) {
                            connectHaptics(10_000, count + 1);
                        } else {
                            System.out.println("Connect haptics failed: " + getLastNativeError());
                        }
                    }
                } finally {
                    // Always cancel our own timer
                    try {
                        localTimer.cancel();
                    } catch (Throwable ignored) {
                        // ignore
                    }

                    // Only clear the field if nobody replaced it in the meantime
                    if (hapticsTimer == localTimer) {
                        hapticsTimer = null;
                    }
                }
            }
        }, timeout);
    }


    /**
     * Polls SDL manually.
     * If you use the *Fast* getters below, you MUST call poll() first.
     */
    public void poll() throws ControllerUnpluggedException {
        ensureConnected();
        nativePoll(controllerPtr);
    }

    private native void nativePoll(long controllerPtr); /*
        SDL_UpdateGamepads();
    */

    /**
     * @return last error message logged by the native lib. Use this for debugging purposes.
     */
    public native String getLastNativeError(); /*
        return env->NewStringUTF(SDL_GetError());
    */

    private native long nativeConnectController(int index); /*
        //SDL 3 opens gamepads by instance id, so map our slot onto the current device list.
        int count = 0;
        SDL_JoystickID *ids = SDL_GetGamepads(&count);
        if (ids == NULL) {
            return 0;
        }

        jlong result = 0;
        if (index >= 0 && index < count) {
            result = (jlong)(intptr_t) SDL_OpenGamepad(ids[index]);
        }

        SDL_free(ids);
        return result;
    */

    private native boolean nativeIsTouchpadSupported(long controllerPtr); /*{
        return SDL_GetNumGamepadTouchpads(jamepad_pad(controllerPtr)) > 0 ? JNI_TRUE : JNI_FALSE;
    }*/

    private static final int SENSOR_ACCEL = 1;

    private static final int SENSOR_GYRO = 2;

    // A controller may expose only one of the two, so enable them independently and report
    // back which ones actually came up.
    private native int nativeEnableSensors(long controllerPtr); /*
        SDL_Gamepad* pad = jamepad_pad(controllerPtr);
        jint enabled = 0;

        if (SDL_GamepadHasSensor(pad, SDL_SENSOR_ACCEL) &&
            SDL_SetGamepadSensorEnabled(pad, SDL_SENSOR_ACCEL, true)) {
            enabled |= 1;
        }
        if (SDL_GamepadHasSensor(pad, SDL_SENSOR_GYRO) &&
            SDL_SetGamepadSensorEnabled(pad, SDL_SENSOR_GYRO, true)) {
            enabled |= 2;
        }

        return enabled;
    */

    /*JNI
    // The DualSense exposes its haptic motors as channels 3 and 4 of a 4-channel
    // 48kHz playback device. Callers hand us 3kHz stereo, and SDL 3's audio stream
    // does the resampling that SDL_AudioCVT used to do by hand.
    static SDL_AudioDeviceID haptics_output = 0;
    static SDL_AudioStream *haptics_stream = NULL;
    static Uint8 *haptics_remix_buf = NULL;
    static int haptics_remix_capacity = 0;

    static void jamepad_close_haptics() {
        if (haptics_stream != NULL) {
            SDL_DestroyAudioStream(haptics_stream);
            haptics_stream = NULL;
        }
        if (haptics_output != 0) {
            SDL_CloseAudioDevice(haptics_output);
            haptics_output = 0;
        }
        if (haptics_remix_buf != NULL) {
            SDL_free(haptics_remix_buf);
            haptics_remix_buf = NULL;
            haptics_remix_capacity = 0;
        }
    }
    */

    private native boolean nativeEnableHaptics(); /*
        //Nothing to preallocate any more; just make sure the audio subsystem came up.
        return SDL_WasInit(SDL_INIT_AUDIO) != 0 ? JNI_TRUE : JNI_FALSE;
    */

    private native boolean nativeConnectHaptics(boolean isWindowsOrMac, Object instance); /*
        if(haptics_output != 0) {
            return JNI_TRUE; // already initialized
        }

        int count = 0;
        SDL_AudioDeviceID *devices = SDL_GetAudioPlaybackDevices(&count);
        if (devices == NULL) {
            return JNI_FALSE;
        }

        const char* wanted = isWindowsOrMac ? "Wireless Controller" : "DualSense";

        SDL_AudioSpec deviceSpec;
        SDL_zero(deviceSpec);
        deviceSpec.format = SDL_AUDIO_S16LE;
        deviceSpec.channels = 4;
        deviceSpec.freq = 48000;

        SDL_AudioSpec sourceSpec;
        SDL_zero(sourceSpec);
        sourceSpec.format = SDL_AUDIO_S16LE;
        sourceSpec.channels = 4;
        sourceSpec.freq = 3000;

        jboolean result = JNI_FALSE;

        for (int i = 0; i < count; i++) {
            const char* device_name = SDL_GetAudioDeviceName(devices[i]);
            if (device_name == NULL || !strstr(device_name, wanted)) {
                continue;
            }

            SDL_AudioDeviceID opened = SDL_OpenAudioDevice(devices[i], &deviceSpec);
            if (opened == 0) {
                continue;
            }

            //If the device did not really open with four channels, SDL will downmix and
            //the two haptic channels disappear into the speaker mix.
            SDL_AudioSpec actual;
            SDL_zero(actual);
            if (SDL_GetAudioDeviceFormat(opened, &actual, NULL) && actual.channels != 4) {
                printf("NATIVE METHOD: DualSense haptics device \"%s\" opened with %d channels "
                       "instead of 4, haptic channels will be lost\n", device_name, actual.channels);
            }

            SDL_AudioStream *stream = SDL_CreateAudioStream(&sourceSpec, &deviceSpec);
            if (stream == NULL) {
                SDL_CloseAudioDevice(opened);
                continue;
            }

            if (!SDL_BindAudioStream(opened, stream)) {
                SDL_DestroyAudioStream(stream);
                SDL_CloseAudioDevice(opened);
                continue;
            }

            haptics_output = opened;
            haptics_stream = stream;
            result = JNI_TRUE;
            break;
        }

        SDL_free(devices);
        return result;
    */

    /**
     * Close the connection to this controller.
     */
    public void close() {
        final Timer timer = hapticsTimer;
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Throwable ignored) {
                // ignore
            }
            hapticsTimer = null;
        }
        if(controllerPtr != 0) {
            if(needToClearTriggerEffect){
                // clear trigger effects
                nativeSendAdaptiveTriggerEffects(controllerPtr, (byte) 0x05, new byte[10], 10, (byte) 0x05, new byte[10], 10);
            }
            nativeClose(controllerPtr);
            controllerPtr = 0;
        }
        touchStates.clear();
    }

    private native void nativeClose(long controllerPtr); /*
        SDL_Gamepad* pad = jamepad_pad(controllerPtr);
        if(pad) {
            SDL_CloseGamepad(pad);
        }
        jamepad_close_haptics();
    */

    boolean isUsingSonyControllerFeatures() {
        return !Objects.equals(Configuration.SonyControllerFeature.NONE, sonyControllerFeature);
    }

    public String getControllerGuid() {
        return controllerGuid;
    }

    public boolean isSupportingTouchpadData() {
        return supportsTouchpad;
    }

    /**
     * @return true if motion sensors were requested through
     * {@link Configuration#useControllerMotionSensors} and this controller brought up at
     * least one of them
     */
    public boolean isSupportingSensorData() {
        return supportsSensors;
    }

    /**
     * @return true if accelerometer readings of {@link #getSensorState()} are live. When
     * false those axes stay at zero.
     */
    public boolean isSupportingAccelerometer() {
        return hasAccelerometer;
    }

    /**
     * @return true if gyroscope readings of {@link #getSensorState()} are live. When false
     * those axes stay at zero.
     */
    public boolean isSupportingGyroscope() {
        return hasGyroscope;
    }

    /**
     * The rate the controller reports motion samples at, in samples per second, or 0 if SDL
     * cannot tell. Useful as a fallback when a driver does not supply sample timestamps.
     *
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public float getSensorDataRate() throws ControllerUnpluggedException {
        ensureConnected();
        if (!supportsSensors) {
            return 0;
        }
        return nativeGetSensorDataRate(controllerPtr, hasGyroscope ? SENSOR_GYRO : SENSOR_ACCEL);
    }

    private native float nativeGetSensorDataRate(long controllerPtr, int sensor); /*
        return SDL_GetGamepadSensorDataRate(jamepad_pad(controllerPtr),
                                            sensor == 2 ? SDL_SENSOR_GYRO : SDL_SENSOR_ACCEL);
    */

    public boolean isSupportingHaptics() { return supportsHaptic; }

    /**
     * Get the current sony configuration feature of this controller.
     *
     * @return the indication of which sony features this controller is using.
     */
    public Configuration.SonyControllerFeature getSonyControllerFeatureConfig() {
        return sonyControllerFeature;
    }

    /**
     * Close and reconnect to the native gamepad at the index associated with this ControllerIndex object.
     * This will refresh the gamepad represented here. This should be called if something is plugged
     * in or unplugged.
     *
     * @return whether or not the controller could successfully reconnect.
     */
    public boolean reconnectController() {
        close();
        connectController();

        return isConnected();
    }

    /**
     * Return whether or not the controller is currently connected. This first checks that the controller
     * was successfully connected to our SDL backend. Then we check if the controller is currently plugged
     * in.
     *
     * @return Whether or not the controller is plugged in.
     */
    public boolean isConnected() {
        return controllerPtr != 0 && nativeIsConnected(controllerPtr);
    }
    private native boolean nativeIsConnected(long controllerPtr); /*
        SDL_Gamepad* pad = jamepad_pad(controllerPtr);
        if (pad && SDL_GamepadConnected(pad)) {
            return JNI_TRUE;
        }
        return JNI_FALSE;
    */

    /**
     * Returns the index of the current controller.
     * @return The index of the current controller.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return true of controller can vibrate
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean canVibrate() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeCanVibrate(controllerPtr);
    }

    private native boolean nativeCanVibrate(long controllerPtr); /*
        //SDL_JoystickHasRumble is gone in SDL 3; the capability is a gamepad property now.
        SDL_PropertiesID props = SDL_GetGamepadProperties(jamepad_pad(controllerPtr));
        return SDL_GetBooleanProperty(props, SDL_PROP_GAMEPAD_CAP_RUMBLE_BOOLEAN, false) ? JNI_TRUE : JNI_FALSE;
    */

    private native boolean nativeDoVibration(long controllerPtr, int leftMagnitude, int rightMagnitude, int duration_ms); /*
        return SDL_RumbleGamepad(jamepad_pad(controllerPtr),
                                 (Uint16) leftMagnitude, (Uint16) rightMagnitude,
                                 (Uint32) duration_ms) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Vibrate the controller using the new rumble API
     * Each call to this function cancels any previous rumble effect, and calling it with 0 intensity stops any rumbling.
     *
     * This will return false if the controller doesn't support vibration or if SDL was unable to start
     * vibration (maybe the controller doesn't support left/right vibration, maybe it was unplugged in the
     * middle of trying, etc...)
     *
     * @param leftMagnitude The intensity of the left rumble motor (this should be between 0 and 1)
     * @param rightMagnitude The intensity of the right rumble motor (this should be between 0 and 1)
     * @return Whether or not the controller was able to be vibrated (i.e. if haptics are supported)
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean doVibration(float leftMagnitude, float rightMagnitude, int duration_ms) throws ControllerUnpluggedException {
        ensureConnected();

        //Check the values are appropriate
        boolean leftInRange = leftMagnitude >= 0 && leftMagnitude <= 1;
        boolean rightInRange = rightMagnitude >= 0 && rightMagnitude <= 1;
        if(!(leftInRange && rightInRange)) {
            throw new IllegalArgumentException("The passed values are not in the range 0 to 1!");
        }

        return nativeDoVibration(controllerPtr, (int) (65535 * leftMagnitude), (int) (65535 * rightMagnitude), duration_ms);
    }

    /**
     * Returns whether or not a given button has been pressed.
     *
     * @param toCheck The ControllerButton to check the state of
     * @return Whether or not the button is pressed.
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isButtonPressed(ControllerButton toCheck) throws ControllerUnpluggedException {
        updateButton(toCheck);
        return heldDownButtons[toCheck.ordinal()];
    }

    /**
     * Returns whether or not a given button has just been pressed since you last made a query
     * about that button (either through this method, isButtonPressed(), or through the ControllerState
     * side of things). If the button was not pressed the last time you checked but is now, this method
     * will return true.
     *
     * @param toCheck The ControllerButton to check the state of
     * @return Whether or not the button has just been pressed.
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isButtonJustPressed(ControllerButton toCheck) throws ControllerUnpluggedException {
        updateButton(toCheck);
        return justPressedButtons[toCheck.ordinal()];
    }

    private void updateButton(ControllerButton button) throws ControllerUnpluggedException {
        ensureConnected();

        int slot = button.ordinal();
        boolean currButtonIsPressed = nativeCheckButton(controllerPtr, button.getSdlValue());
        justPressedButtons[slot] = (currButtonIsPressed && !heldDownButtons[slot]);
        heldDownButtons[slot] = currButtonIsPressed;
    }

    private native boolean nativeCheckButton(long controllerPtr, int buttonIndex); /*
        SDL_UpdateGamepads();
        return SDL_GetGamepadButton(jamepad_pad(controllerPtr), (SDL_GamepadButton) buttonIndex) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Returns if a given button is available on controller.
     *
     * @param toCheck The ControllerButton to check
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isButtonAvailable(ControllerButton toCheck) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeButtonAvailable(controllerPtr, toCheck.getSdlValue());
    }

    private native boolean nativeButtonAvailable(long controllerPtr, int buttonIndex); /*
        return SDL_GamepadHasButton(jamepad_pad(controllerPtr), (SDL_GamepadButton) buttonIndex) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Returns the current state of a passed axis.
     *
     * @param toCheck The ControllerAxis to check the state of
     * @return The current state of the requested axis.
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public float getAxisState(ControllerAxis toCheck) throws ControllerUnpluggedException {
        ensureConnected();

        return nativeCheckAxis(controllerPtr, toCheck.getSdlValue()) / AXIS_MAX_VAL;
    }

    private native int nativeCheckAxis(long controllerPtr, int axisIndex); /*
        SDL_UpdateGamepads();
        return SDL_GetGamepadAxis(jamepad_pad(controllerPtr), (SDL_GamepadAxis) axisIndex);
    */

    /**
     * Returns if passed axis is available on controller.
     *
     * @param toCheck The ControllerAxis to check
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isAxisAvailable(ControllerAxis toCheck) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeAxisAvailable(controllerPtr, toCheck.getSdlValue());
    }

    private native boolean nativeAxisAvailable(long controllerPtr, int axisIndex); /*
        return SDL_GamepadHasAxis(jamepad_pad(controllerPtr), (SDL_GamepadAxis) axisIndex) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Returns the implementation dependent name of this controller.
     *
     * @return The name of this controller
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public String getName() throws ControllerUnpluggedException {
        ensureConnected();

        String controllerName = nativeGetName(controllerPtr);

        //Return a descriptive string instead of null if the attached controller does not have a name
        if(controllerName == null) {
            return "Unnamed Controller";
        }
        return controllerName;
    }

    private native String nativeGetName(long controllerPtr); /*
        const char* name = SDL_GetGamepadName(jamepad_pad(controllerPtr));
        return name == NULL ? NULL : env->NewStringUTF(name);
    */

    /**
     * Returns the instance ID of the current controller, which uniquely identifies
     * the device from the time it is connected until it is disconnected.
     *
     * @return The instance ID of the current controller
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public int getDeviceInstanceID() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetDeviceInstanceID(controllerPtr);
    }

    private native int nativeGetDeviceInstanceID(long controllerPtr); /*
        return (jint) SDL_GetJoystickID(jamepad_joystick(controllerPtr));
     */

    /**
     * @return player index if set and supported, -1 otherwise
     */
    public int getPlayerIndex() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetPlayerIndex(controllerPtr);
    }

    private native int nativeGetPlayerIndex(long controllerPtr); /*
        return SDL_GetGamepadPlayerIndex(jamepad_pad(controllerPtr));
    */

    /**
     * Sets player index. At the time being, this doesn't seem to change the indication lights on
     * a controller on Windows, Linux and Mac, but only an internal representation index.
     * @param index index to set
     */
    public void setPlayerIndex(int index) throws ControllerUnpluggedException {
        ensureConnected();
        nativeSetPlayerIndex(controllerPtr, index);
    }

    private native void nativeSetPlayerIndex(long controllerPtr, int index); /*
        SDL_SetGamepadPlayerIndex(jamepad_pad(controllerPtr), index);
    */

    /**
     * @return current power state of the game controller, see {@link ControllerPowerLevel} enum values
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public ControllerPowerLevel getPowerLevel() throws ControllerUnpluggedException {
        ensureConnected();
        return ControllerPowerLevel.fromSdlValue(nativeGetPowerInfo(controllerPtr) >> 8);
    }

    /**
     * Returns the remaining battery charge as a percentage.
     *
     * @return the battery charge between 0 and 100, or -1 if the controller cannot report it
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public int getBatteryPercentage() throws ControllerUnpluggedException {
        ensureConnected();
        return (byte) (nativeGetPowerInfo(controllerPtr) & 0xFF);
    }

    /**
     * SDL 3 reports the power state and the battery percentage together, so both come
     * back packed into one int: the state in the high byte, the percentage in the low
     * byte (as a signed byte, so an unknown percentage arrives as -1).
     */
    private native int nativeGetPowerInfo(long controllerPtr); /*
        int percent = -1;
        SDL_PowerState state = SDL_GetGamepadPowerInfo(jamepad_pad(controllerPtr), &percent);
        if (percent < 0 || percent > 100) {
            percent = -1;
        }
        return ((int) state << 8) | (percent & 0xFF);
    */


    /**
     * To use this function Sony controller features must be enabled in configuration of the
     * {@link com.studiohartman.jamepad.ControllerManager}.
     * @param finger the index of the finger of interest
     * @return a TouchState object containing the touch information of the finger.
     * If the operation was not successful e.g. because the controller doesn't have
     * a touchpad then a default TouchState object is returned.
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public TouchState getTouchpadFinger(int finger) throws ControllerUnpluggedException {
        ensureConnected();

        TouchState touchState = touchStates.get(finger);
        if(touchState == null){
            touchState = new TouchState();
            touchStates.put(finger, touchState);
        }
        if(!supportsTouchpad){
            return touchState;
        }
        nativeGetTouchpadFinger(controllerPtr, finger, touchState);

        return touchState;
    }

    private native void nativeGetTouchpadFinger(long controllerPtr, int finger, Object touchState); /*
        SDL_UpdateGamepads();

        bool down = false;
        float x, y, pressure;
        if(SDL_GetGamepadTouchpadFinger(jamepad_pad(controllerPtr), 0, finger, &down, &x, &y, &pressure)) {
            jclass clazz = env->GetObjectClass(touchState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(ZFF)V");

            env->CallVoidMethod(touchState, update_method, down ? JNI_TRUE : JNI_FALSE, x, y);
        }
     */

    /**
     * To use this function Sony controller features must be enabled in configuration of the
     * {@link com.studiohartman.jamepad.ControllerManager}.
     * @return a SensorState object containing the sensor information of the controller.
     * If {@link Configuration#useControllerMotionSensors} is off, or the controller has no
     * motion hardware, a default SensorState will be returned.
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public SensorState getSensorState() throws ControllerUnpluggedException {
        ensureConnected();
        if(!supportsSensors) {
            return sensorState;
        }
        nativeGetSensorState(controllerPtr, sensorState);

        return sensorState;
    }

    private native void nativeGetSensorState(long controllerPtr, Object sensorState);/*
        SDL_UpdateGamepads();
        jamepad_read_sensor_state(env, jamepad_pad(controllerPtr), sensorState);
    */

    /**
     * Send adaptive trigger effects to the controller.
     * If the controller is not a DualSense controller calling this function doesn't have any effect.
     * @param leftTriggerEffect the left trigger effect type
     * @param triggerDataLeft the left trigger adaptive data
     * @param rightTriggerEffect the right trigger effect type
     * @param triggerDataRight the right trigger adaptive data
     * @return true if the adaptive trigger data was sent successfully, false otherwise
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean sendAdaptiveTriggerEffects(byte leftTriggerEffect, byte[] triggerDataLeft, byte rightTriggerEffect, byte[] triggerDataRight) throws ControllerUnpluggedException {
        ensureConnected();

        if(!hasBasicDualSenseFeatures() || !nativeIsDualSenseController(controllerPtr)) {
            return false;
        }

        needToClearTriggerEffect = true;
        return nativeSendAdaptiveTriggerEffects(controllerPtr, leftTriggerEffect, triggerDataLeft, triggerDataLeft.length, rightTriggerEffect, triggerDataRight, triggerDataRight.length);
    }

    private native boolean nativeIsDualSenseController(long controllerPtr); /*
        SDL_Gamepad* pad = jamepad_pad(controllerPtr);
        Uint16 sonyVendorId = 0x054c;
        Uint16 dualSenseProductId = 0x0ce6;
        Uint16 dualSenseEdgeProductId = 0x0df2;

        Uint16 vendorId = SDL_GetGamepadVendor(pad);
        Uint16 productId = SDL_GetGamepadProduct(pad);

        return vendorId == sonyVendorId && (productId == dualSenseProductId || productId == dualSenseEdgeProductId) ? JNI_TRUE : JNI_FALSE;
    */

    // PS5 trigger effect documentation:
    // https://controllers.fandom.com/wiki/Sony_DualSense#FFB_Trigger_Modes
    /*JNI
    typedef struct
    {
        Uint8 ucEnableBits1;                // 0
        Uint8 ucEnableBits2;                // 1
        Uint8 ucRumbleRight;                // 2
        Uint8 ucRumbleLeft;                 // 3
        Uint8 ucHeadphoneVolume;            // 4
        Uint8 ucSpeakerVolume;              // 5
        Uint8 ucMicrophoneVolume;           // 6
        Uint8 ucAudioEnableBits;            // 7
        Uint8 ucMicLightMode;               // 8
        Uint8 ucAudioMuteBits;              // 9
        Uint8 rgucRightTriggerEffect[11];   // 10
        Uint8 rgucLeftTriggerEffect[11];    // 21
        Uint8 rgucUnknown1[6];              // 32
        Uint8 ucLedFlags;                   // 38
        Uint8 rgucUnknown2[2];              // 39
        Uint8 ucLedAnim;                    // 41
        Uint8 ucLedBrightness;              // 42
        Uint8 ucPadLights;                  // 43
        Uint8 ucLedRed;                     // 44
        Uint8 ucLedGreen;                   // 45
        Uint8 ucLedBlue;                    // 46
    } DS5EffectsState_t;
     */

    private native boolean nativeSendAdaptiveTriggerEffects(long controllerPtr,
                                                            byte leftTriggerEffect,
                                                            byte[] triggerDataLeft,
                                                            int leftTriggerDataSize,
                                                            byte rightTriggerEffect,
                                                            byte[] triggerDataRight,
                                                            int rightTriggerDataSize); /*
        SDL_Gamepad* pad = jamepad_pad(controllerPtr);

        DS5EffectsState_t state;
        SDL_zero(state);

        state.ucEnableBits1 |= (0x04 | 0x08); // Modify right and left trigger effect respectively
        state.rgucLeftTriggerEffect[0] = leftTriggerEffect;
        SDL_memcpy(state.rgucLeftTriggerEffect + 1, triggerDataLeft, leftTriggerDataSize);
        state.rgucRightTriggerEffect[0] = rightTriggerEffect;
        SDL_memcpy(state.rgucRightTriggerEffect + 1, triggerDataRight, rightTriggerDataSize);

        return SDL_SendGamepadEffect(pad, &state, sizeof(state)) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Send haptic feedback audio data to the controller.
     * Audio Data must be in 3KHZ, 2 channel, 16-bit Little-Endian PCM format.
     * If the controller is not a DualSense controller calling this function doesn't have any effect.
     * @param hapticFeedback the haptic feedback audio data
     * @return true if the haptic feedback audio data was sent successfully, false otherwise
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean sendHapticFeedbackAudioPacket(byte[] hapticFeedback) throws ControllerUnpluggedException {
        ensureConnected();

        if(!hasBasicDualSenseFeatures() || !nativeIsDualSenseController(controllerPtr)) {
            return false;
        }

        return nativeSendHapticFeedback(hapticFeedback, hapticFeedback.length);
    }

    private native boolean nativeSendHapticFeedback(byte[] hapticFeedback, int hapticFeedbackSize); /*
        if(haptics_stream == NULL) {
            return JNI_FALSE;
        }

        //Input is 3kHz stereo; the DualSense wants the haptics on channels 3 and 4 of a
        //4-channel stream, so every 4-byte frame becomes 8 bytes with the speaker pair silenced.
        int remixed = hapticFeedbackSize * 2;
        if (remixed > haptics_remix_capacity) {
            Uint8 *grown = (Uint8 *) SDL_realloc(haptics_remix_buf, remixed);
            if (grown == NULL) {
                return JNI_FALSE;
            }
            haptics_remix_buf = grown;
            haptics_remix_capacity = remixed;
        }

	    for (int i = 0; i + 4 <= hapticFeedbackSize; i += 4)
	    {
		    SDL_memset(haptics_remix_buf + i * 2, 0, 4);
		    SDL_memcpy(haptics_remix_buf + (i * 2) + 4, hapticFeedback + i, 4);
	    }

	    //SDL 3 resamples 3kHz -> 48kHz inside the stream.
	    return SDL_PutAudioStreamData(haptics_stream, haptics_remix_buf, remixed) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * @return The number of available raw buttons on this controller.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public int getNumRawButtons() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetNumRawButtons(controllerPtr);
    }

    private native int nativeGetNumRawButtons(long controllerPtr); /*
        return SDL_GetNumJoystickButtons(jamepad_joystick(controllerPtr));
    */

    /**
     * @return The number of available raw axes on this controller.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public int getNumRawAxes() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetNumRawAxes(controllerPtr);
    }

    private native int nativeGetNumRawAxes(long controllerPtr); /*
        return SDL_GetNumJoystickAxes(jamepad_joystick(controllerPtr));
    */

    /**
     * Returns the raw pressed state of the specified button index.
     * <p>
     * The button index corresponds to the raw layout of the controller,
     * independent of any mappings.
     *
     * @param buttonIndex The raw button index to check.
     * @return {@code true} if the button is currently pressed, {@code false} otherwise.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public boolean getRawButtonPressed(int buttonIndex) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetRawButtonPressed(controllerPtr, buttonIndex);
    }

    private native boolean nativeGetRawButtonPressed(long controllerPtr, int buttonIndex); /*
        return SDL_GetJoystickButton(jamepad_joystick(controllerPtr), buttonIndex) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Returns the raw state of the specified axis index.
     * <p>
     * The returned value ranges from -32768 to 32767 depending on the axis position.
     * The axis index corresponds to the raw physical controller axes,
     * independent of any mappings.
     *
     * @param axisIndex The raw axis index to read.
     * @return The current value of the axis.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public int getRawAxisState(int axisIndex) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetRawAxisState(controllerPtr, axisIndex);
    }

    private native int nativeGetRawAxisState(long controllerPtr, int axisIndex); /*
        return SDL_GetJoystickAxis(jamepad_joystick(controllerPtr), axisIndex);
    */

    /**
     * @return The USB Vendor ID (VID) of the controller.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public int getVendorId() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetVendorId(controllerPtr);
    }

    private native int nativeGetVendorId(long controllerPtr); /*
        return SDL_GetJoystickVendor(jamepad_joystick(controllerPtr));
    */

    /**
     * @return The USB Product ID (PID) of the controller.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public int getProductId() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetProductId(controllerPtr);
    }

    private native int nativeGetProductId(long controllerPtr); /*
        return SDL_GetJoystickProduct(jamepad_joystick(controllerPtr));
    */

    /**
     * Returns the implementation-dependent device name of this controller.
     * <p>
     * This is usually the device name as reported by the operating system or driver.
     *
     * @return The device name string.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public String getDeviceName() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetDeviceName(controllerPtr);
    }

    private native String nativeGetDeviceName(long controllerPtr); /*
        const char* name = SDL_GetJoystickName(jamepad_joystick(controllerPtr));
        return name == NULL ? NULL : env->NewStringUTF(name);
    */

    /**
     * Returns the unique GUID (Globally Unique Identifier) string for this controller.
     * <p>
     * The GUID identifies the hardware model and variant and can be used for
     * distinguishing between different types of controllers.
     *
     * @return The device GUID as a hexadecimal string.
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public String getDeviceGuid() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetDeviceGuid(controllerPtr);
    }

    private native String nativeGetDeviceGuid(long controllerPtr); /*
        SDL_GUID guid = SDL_GetJoystickGUID(jamepad_joystick(controllerPtr));
        char guid_str[33];
        SDL_GUIDToString(guid, guid_str, sizeof(guid_str));
        return env->NewStringUTF(guid_str);
    */

    /**
     * Convenience method to throw an exception if the controller is not connected.
     */
    private void ensureConnected() throws ControllerUnpluggedException {
        if(!isConnected()) {
            throw new ControllerUnpluggedException("Controller at index " + index + " is not connected!");
        }
    }

    /**
     * Convenience method to check if the controller supports basic DualSense features.
     * @return true if the controller supports basic DualSense features
     */
    private boolean hasBasicDualSenseFeatures() {
        return Objects.equals(Configuration.SonyControllerFeature.DUALSENSE_FEATURES, sonyControllerFeature) ||
                Objects.equals(Configuration.SonyControllerFeature.DUALSENSE_FEATURES_AND_HAPTICS, sonyControllerFeature);
    }

    /********************************/
    /*** Fast getters (NO UPDATE) ***/
    /********************************/

    public float getAxisStateFast(ControllerAxis toCheck) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeCheckAxisNoUpdate(controllerPtr, toCheck.getSdlValue()) / AXIS_MAX_VAL;
    }

    private native int nativeCheckAxisNoUpdate(long controllerPtr, int axisIndex); /*
        return SDL_GetGamepadAxis(jamepad_pad(controllerPtr), (SDL_GamepadAxis) axisIndex);
    */

    public boolean isButtonPressedFast(ControllerButton toCheck) throws ControllerUnpluggedException {
        updateButtonFast(toCheck);
        return heldDownButtons[toCheck.ordinal()];
    }

    public boolean isButtonJustPressedFast(ControllerButton toCheck) throws ControllerUnpluggedException {
        updateButtonFast(toCheck);
        return justPressedButtons[toCheck.ordinal()];
    }

    private void updateButtonFast(ControllerButton button) throws ControllerUnpluggedException {
        ensureConnected();
        int slot = button.ordinal();
        boolean currButtonIsPressed = nativeCheckButtonNoUpdate(controllerPtr, button.getSdlValue());
        justPressedButtons[slot] = (currButtonIsPressed && !heldDownButtons[slot]);
        heldDownButtons[slot] = currButtonIsPressed;
    }

    private native boolean nativeCheckButtonNoUpdate(long controllerPtr, int buttonIndex); /*
        return SDL_GetGamepadButton(jamepad_pad(controllerPtr), (SDL_GamepadButton) buttonIndex) ? JNI_TRUE : JNI_FALSE;
    */

    public TouchState getTouchpadFingerFast(int finger) throws ControllerUnpluggedException {
        ensureConnected();

        TouchState touchState = touchStates.get(finger);
        if (touchState == null) {
            touchState = new TouchState();
            touchStates.put(finger, touchState);
        }
        if (!supportsTouchpad) {
            return touchState;
        }

        nativeGetTouchpadFingerNoUpdate(controllerPtr, finger, touchState);
        return touchState;
    }

    private native void nativeGetTouchpadFingerNoUpdate(long controllerPtr, int finger, Object touchState); /*
        bool down = false;
        float x, y, pressure;
        if(SDL_GetGamepadTouchpadFinger(jamepad_pad(controllerPtr), 0, finger, &down, &x, &y, &pressure)) {
            jclass clazz = env->GetObjectClass(touchState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(ZFF)V");
            env->CallVoidMethod(touchState, update_method, down ? JNI_TRUE : JNI_FALSE, x, y);
        }
    */

    public SensorState getSensorStateFast() throws ControllerUnpluggedException {
        ensureConnected();
        if (!supportsSensors) {
            return sensorState;
        }
        nativeGetSensorStateNoUpdate(controllerPtr, sensorState);
        return sensorState;
    }

    private native void nativeGetSensorStateNoUpdate(long controllerPtr, Object sensorState);/*
        jamepad_read_sensor_state(env, jamepad_pad(controllerPtr), sensorState);
    */
}
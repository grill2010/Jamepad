package com.studiohartman.jamepad;

import com.badlogic.gdx.utils.SharedLibraryLoader;

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
    #include "SDL.h"
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

    private String controllerGuid = "";

    private boolean supportsTouchpad = false;

    private boolean supportsSensors = false;

    private boolean supportsHaptic = false;

    private boolean needToClearTriggerEffect = false;

    private final SensorState sensorState = new SensorState();

    private final Map<Integer, TouchState> touchStates = new HashMap<>();

    private Timer hapticsTimer;

    private volatile boolean closing = false;

    private static final String EMPTY_GUID = "00000000000000000000000000000000";

    /**
     * Constructor. Builds a controller at the given index and attempts to connect to it.
     * This is only accessible in the Jamepad package, so people can't go trying to make controllers
     * before the native library is loaded or initialized.
     *
     * @param index The index of the controller
     * @param sonyControllerFeature The indication for the controller if it should use Sony controller
     *                                  features like motion data and touchpad
     */
    ControllerIndex(int index, Configuration.SonyControllerFeature sonyControllerFeature) {
        this.index = index;
        this.sonyControllerFeature = sonyControllerFeature;

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
            supportsHaptic = false;
            return;
        }
        controllerGuid = nativeGetDeviceGuid(controllerPtr);
        if(!Objects.equals(Configuration.SonyControllerFeature.NONE, sonyControllerFeature)) {
            supportsTouchpad = nativeIsTouchpadSupported(controllerPtr);
            supportsSensors = nativeEnableSensors(controllerPtr);
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

        if (closing) {
            return;
        }

        final Timer localTimer = new Timer(true); // daemon
        hapticsTimer = localTimer;

        localTimer.schedule(new TimerTask() {
            @Override public void run() {
                try {
                    if (closing || !isConnected()) {
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
        SDL_GameControllerUpdate();
    */

    /**
     * @return last error message logged by the native lib. Use this for debugging purposes.
     */
    public native String getLastNativeError(); /*
        return env->NewStringUTF(SDL_GetError());
    */

    private native long nativeConnectController(int index); /*
        return (jlong) SDL_GameControllerOpen(index);
    */

    private native boolean nativeIsTouchpadSupported(long controllerPtr); /*{
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerGetNumTouchpads(pad) > 0 ? JNI_TRUE : JNI_FALSE;
    }*/

    private native boolean nativeEnableSensors(long controllerPtr); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        if(SDL_GameControllerHasSensor(pad, SDL_SENSOR_ACCEL) && SDL_GameControllerHasSensor(pad, SDL_SENSOR_GYRO)) {
            SDL_GameControllerSetSensorEnabled(pad, SDL_SENSOR_ACCEL, SDL_TRUE);
		    SDL_GameControllerSetSensorEnabled(pad, SDL_SENSOR_GYRO, SDL_TRUE);

		    return JNI_TRUE;
        }
        return JNI_FALSE;
    */

    /*JNI
    uint8_t *haptics_resampler_buf = NULL;
     */

    /*JNI
    #include <stdio.h>
    */

    private native boolean nativeEnableHaptics(); /*
        SDL_AudioCVT cvt;
	    int result = SDL_BuildAudioCVT(&cvt, AUDIO_S16LSB, 4, 3000, AUDIO_S16LSB, 4, 48000);
	    if(result < 0) {
	        return JNI_FALSE;
	    }
	    cvt.len = 240;  // 10 16bit stereo samples
	    haptics_resampler_buf = (uint8_t*) SDL_calloc(cvt.len * cvt.len_mult, sizeof(uint8_t));
	    return JNI_TRUE;
    */

    /*JNI
    SDL_AudioDeviceID haptics_output = 0;
     */

    /*JNI
    #include <string.h>
    */

    private native boolean nativeConnectHaptics(boolean isWindowsOrMac, Object instance); /*
        if(haptics_output != 0) {
            return JNI_TRUE; // already initialized
        }

        SDL_AudioSpec want, have;
        SDL_zero(want);
        want.freq = 48000;
        want.format = AUDIO_S16LSB;
        want.channels = 4;
        want.samples = 480; // 10ms buffer
        want.callback = NULL;

        for (int i = 0; i < SDL_GetNumAudioDevices(0); i++) {
            const char* device_name = SDL_GetAudioDeviceName(i, 0);
            if (isWindowsOrMac) {
                if (device_name == NULL || !strstr(device_name, "Wireless Controller")) {
                    continue;
                }
            } else {
                if (device_name == NULL || !strstr(device_name, "DualSense")) {
                    continue;
                }
            }
            haptics_output = SDL_OpenAudioDevice(device_name, 0, &want, &have, 0);
            if (haptics_output == 0) {
                continue;
            }
            SDL_PauseAudioDevice(haptics_output, 0);
            return JNI_TRUE;
        }

        return JNI_FALSE;
    */

    /**
     * Close the connection to this controller.
     */
    public void close() {
        this.closing = true;
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

    /*JNI
    #include <cstdlib>
     */

    private native void nativeClose(long controllerPtr); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        if(pad && SDL_GameControllerGetAttached(pad)) {
            SDL_GameControllerClose(pad);
        }
        pad = NULL;
        if(haptics_resampler_buf != NULL) {
            free(haptics_resampler_buf);
            haptics_resampler_buf = NULL;
        }
        if (haptics_output > 0)
        {
            SDL_CloseAudioDevice(haptics_output);
		    haptics_output = 0;
		}
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

    public boolean isSupportingSensorData() {
        return supportsSensors;
    }

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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        if (pad && SDL_GameControllerGetAttached(pad)) {
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
        SDL_Joystick* joystick = SDL_GameControllerGetJoystick((SDL_GameController*) controllerPtr);
        return SDL_JoystickHasRumble(joystick);
    */

    private native boolean nativeDoVibration(long controllerPtr, int leftMagnitude, int rightMagnitude, int duration_ms); /*
        SDL_Joystick* joystick = SDL_GameControllerGetJoystick((SDL_GameController*) controllerPtr);
        return SDL_JoystickRumble(joystick, leftMagnitude, rightMagnitude,  duration_ms) == 0;
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
        updateButton(toCheck.ordinal());
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
        updateButton(toCheck.ordinal());
        return justPressedButtons[toCheck.ordinal()];
    }

    private void updateButton(int buttonIndex) throws ControllerUnpluggedException {
        ensureConnected();

        boolean currButtonIsPressed = nativeCheckButton(controllerPtr, buttonIndex);
        justPressedButtons[buttonIndex] = (currButtonIsPressed && !heldDownButtons[buttonIndex]);
        heldDownButtons[buttonIndex] = currButtonIsPressed;
    }

    private native boolean nativeCheckButton(long controllerPtr, int buttonIndex); /*
        SDL_GameControllerUpdate();
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerGetButton(pad, (SDL_GameControllerButton) buttonIndex);
    */

    /**
     * Returns if a given button is available on controller.
     *
     * @param toCheck The ControllerButton to check
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isButtonAvailable(ControllerButton toCheck) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeButtonAvailable(controllerPtr, toCheck.ordinal());
    }

    private native boolean nativeButtonAvailable(long controllerPtr, int buttonIndex); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerHasButton(pad, (SDL_GameControllerButton) buttonIndex);
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

        return nativeCheckAxis(controllerPtr, toCheck.ordinal()) / AXIS_MAX_VAL;
    }

    private native int nativeCheckAxis(long controllerPtr, int axisIndex); /*
        SDL_GameControllerUpdate();
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerGetAxis(pad, (SDL_GameControllerAxis) axisIndex);
    */

    /**
     * Returns if passed axis is available on controller.
     *
     * @param toCheck The ControllerAxis to check
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isAxisAvailable(ControllerAxis toCheck) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeAxisAvailable(controllerPtr, toCheck.ordinal());
    }

    private native boolean nativeAxisAvailable(long controllerPtr, int axisIndex); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerHasAxis(pad, (SDL_GameControllerAxis) axisIndex);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return env->NewStringUTF(SDL_GameControllerName(pad));
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
        SDL_Joystick* joystick = SDL_GameControllerGetJoystick((SDL_GameController*) controllerPtr);
        return SDL_JoystickInstanceID(joystick);
     */

    /**
     * @return player index if set and supported, -1 otherwise
     */
    public int getPlayerIndex() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetPlayerIndex(controllerPtr);
    }

    private native int nativeGetPlayerIndex(long controllerPtr); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerGetPlayerIndex(pad);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerSetPlayerIndex(pad, index);
    */

    /**
     * @return current power level of game controller, see {@link ControllerPowerLevel} enum values
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public ControllerPowerLevel getPowerLevel() throws ControllerUnpluggedException {
        ensureConnected();
        return ControllerPowerLevel.valueOf(nativeGetPowerLevel(controllerPtr));
    }

    private native int nativeGetPowerLevel(long controllerPtr); /*
        SDL_Joystick* joystick = SDL_GameControllerGetJoystick((SDL_GameController*) controllerPtr);
        return SDL_JoystickCurrentPowerLevel(joystick);
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
        SDL_GameControllerUpdate();
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;

        Uint8 touch_state;
        float x, y, pressure;
        int result = SDL_GameControllerGetTouchpadFinger(pad, 0, finger, &touch_state, &x, &y, &pressure);
        if(result == 0) {
            jclass clazz = env->GetObjectClass(touchState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(ZFF)V");

            env->CallVoidMethod(touchState, update_method, touch_state == 0 ? JNI_FALSE : JNI_TRUE, x, y);
        }
     */

    /**
     * To use this function Sony controller features must be enabled in configuration of the
     * {@link com.studiohartman.jamepad.ControllerManager}.
     * @return a SensorState object containing the sensor information of the controller.
     * If Sony controller features are not enabled or if the controller doesn't support sensor
     * data then a default SensorState will be returned.
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

    /*JNI
    #include <chrono>
     */

    private native void nativeGetSensorState(long controllerPtr, Object sensorState);/*
        SDL_GameControllerUpdate();
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;

        float gyro_data[3], accel_data[3];
		int resultGyro = SDL_GameControllerGetSensorData(pad, SDL_SENSOR_GYRO, &gyro_data[0], 3);
		int resultAccel = SDL_GameControllerGetSensorData(pad, SDL_SENSOR_ACCEL, &accel_data[0], 3);
		Uint64 microsecondsSinceEpoch = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

		if(resultGyro == 0 && resultAccel == 0) {
		   jclass clazz = env->GetObjectClass(sensorState);
		   jmethodID update_method = env->GetMethodID(clazz, "update", "(FFFFFFJ)V");

		   env->CallVoidMethod(sensorState, update_method, accel_data[0], accel_data[1], accel_data[2], gyro_data[0], gyro_data[1], gyro_data[2], microsecondsSinceEpoch);
		}
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        Uint16 sonyVendorId = 0x054c;
        Uint16 dualSenseProductId = 0x0ce6;
        Uint16 dualSenseEdgeProductId = 0x0df2;

        Uint16 vendorId = SDL_GameControllerGetVendor(pad);
        Uint16 productId = SDL_GameControllerGetProduct(pad);

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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;

        DS5EffectsState_t state;
        SDL_zero(state);

        state.ucEnableBits1 |= (0x04 | 0x08); // Modify right and left trigger effect respectively
        state.rgucLeftTriggerEffect[0] = leftTriggerEffect;
        SDL_memcpy(state.rgucLeftTriggerEffect + 1, triggerDataLeft, leftTriggerDataSize);
        state.rgucRightTriggerEffect[0] = rightTriggerEffect;
        SDL_memcpy(state.rgucRightTriggerEffect + 1, triggerDataRight, rightTriggerDataSize);

        return SDL_GameControllerSendEffect(pad, &state, sizeof(state)) == 0 ? JNI_TRUE : JNI_FALSE;
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
        if(haptics_output == 0) {
            return JNI_FALSE;
        }

        SDL_AudioCVT cvt;
        // Haptics samples are coming in at 3KHZ, but the DualSense expects 48KHZ
        SDL_BuildAudioCVT(&cvt, AUDIO_S16LSB, 4, 3000, AUDIO_S16LSB, 4, 48000);
        cvt.len = hapticFeedbackSize * 2;
        cvt.buf = haptics_resampler_buf;
        // Remix to 4 channels
	    for (int i=0; i < hapticFeedbackSize; i+=4)
	    {
		    SDL_memset(haptics_resampler_buf + i * 2, 0, 4);
		    SDL_memcpy(haptics_resampler_buf + (i * 2) + 4, hapticFeedback + i, 4);
	    }
	    // Resample to 48kHZ
	    if (SDL_ConvertAudio(&cvt) != 0)
	    {
		    return JNI_FALSE;
	    }

	    if (SDL_QueueAudio(haptics_output, cvt.buf, cvt.len_cvt) < 0)
	    {
		    return JNI_FALSE;
	    }

	    return JNI_TRUE;
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return SDL_JoystickNumButtons(joy);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return SDL_JoystickNumAxes(joy);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return SDL_JoystickGetButton(joy, buttonIndex) ? JNI_TRUE : JNI_FALSE;
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return SDL_JoystickGetAxis(joy, axisIndex);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return SDL_JoystickGetVendor(joy);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return SDL_JoystickGetProduct(joy);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);
        return env->NewStringUTF(SDL_JoystickName(joy));
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        SDL_Joystick* joy = SDL_GameControllerGetJoystick(pad);

        SDL_JoystickGUID guid = SDL_JoystickGetGUID(joy);
        char guid_str[33];
        SDL_JoystickGetGUIDString(guid, guid_str, sizeof(guid_str));
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
        return nativeCheckAxisNoUpdate(controllerPtr, toCheck.ordinal()) / AXIS_MAX_VAL;
    }

    private native int nativeCheckAxisNoUpdate(long controllerPtr, int axisIndex); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerGetAxis(pad, (SDL_GameControllerAxis) axisIndex);
    */

    public boolean isButtonPressedFast(ControllerButton toCheck) throws ControllerUnpluggedException {
        updateButtonFast(toCheck.ordinal());
        return heldDownButtons[toCheck.ordinal()];
    }

    public boolean isButtonJustPressedFast(ControllerButton toCheck) throws ControllerUnpluggedException {
        updateButtonFast(toCheck.ordinal());
        return justPressedButtons[toCheck.ordinal()];
    }

    private void updateButtonFast(int buttonIndex) throws ControllerUnpluggedException {
        ensureConnected();
        boolean currButtonIsPressed = nativeCheckButtonNoUpdate(controllerPtr, buttonIndex);
        justPressedButtons[buttonIndex] = (currButtonIsPressed && !heldDownButtons[buttonIndex]);
        heldDownButtons[buttonIndex] = currButtonIsPressed;
    }

    private native boolean nativeCheckButtonNoUpdate(long controllerPtr, int buttonIndex); /*
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;
        return SDL_GameControllerGetButton(pad, (SDL_GameControllerButton) buttonIndex);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;

        Uint8 touch_state;
        float x, y, pressure;
        int result = SDL_GameControllerGetTouchpadFinger(pad, 0, finger, &touch_state, &x, &y, &pressure);
        if(result == 0) {
            jclass clazz = env->GetObjectClass(touchState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(ZFF)V");
            env->CallVoidMethod(touchState, update_method, touch_state == 0 ? JNI_FALSE : JNI_TRUE, x, y);
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
        SDL_GameController* pad = (SDL_GameController*) controllerPtr;

        float gyro_data[3], accel_data[3];
        int resultGyro  = SDL_GameControllerGetSensorData(pad, SDL_SENSOR_GYRO,  &gyro_data[0], 3);
        int resultAccel = SDL_GameControllerGetSensorData(pad, SDL_SENSOR_ACCEL, &accel_data[0], 3);

        Uint64 microsecondsSinceEpoch =
            std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::system_clock::now().time_since_epoch()
            ).count();

        if(resultGyro == 0 && resultAccel == 0) {
            jclass clazz = env->GetObjectClass(sensorState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(FFFFFFJ)V");
            env->CallVoidMethod(sensorState, update_method,
                accel_data[0], accel_data[1], accel_data[2],
                gyro_data[0], gyro_data[1], gyro_data[2],
                microsecondsSinceEpoch
            );
        }
    */
}
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

    // Jamepad hands out controller state by polling, so nothing here ever reads the events SDL
    // queues for every sample it gathers. Left in place they reach SDL_MAX_QUEUED_EVENTS, 65535 of
    // them, within minutes of a controller with motion hardware being open, and from then on SDL
    // refuses every new event whatever its type: the sensor updates the sample times above come
    // from, and the device added and removed ones hot plug is noticed by. The sample times freeze
    // for as long as the process lives while the readings themselves keep coming, because those are
    // kept on the joystick rather than in the queue.
    //
    // Dropping them costs nothing, because the queue is only ever an output: a joystick's state is
    // written before its event is built, a gamepad's buttons and axes are translated out of that
    // state on each read rather than accumulated from events, the watcher that turns joystick events
    // into gamepad ones has already run by the time an event is in the queue, and no part of SDL's
    // own joystick code reads the queue back.
    //
    // Only events of the joystick and gamepad subsystems are dropped, and of those, none that
    // anything here reads: not the four device added and removed ones ControllerManager takes for
    // hot plug, and not the sensor updates taken above. Anything else in the process keeps its
    // events.
    static void jamepad_drain_polled_events() {
        jamepad_take_sensor_events();

        SDL_FlushEvents(SDL_EVENT_JOYSTICK_AXIS_MOTION, SDL_EVENT_JOYSTICK_BUTTON_UP);
        SDL_FlushEvents(SDL_EVENT_JOYSTICK_BATTERY_UPDATED, SDL_EVENT_JOYSTICK_UPDATE_COMPLETE);
        SDL_FlushEvents(SDL_EVENT_GAMEPAD_AXIS_MOTION, SDL_EVENT_GAMEPAD_BUTTON_UP);
        SDL_FlushEvents(SDL_EVENT_GAMEPAD_REMAPPED, SDL_EVENT_GAMEPAD_TOUCHPAD_UP);
        SDL_FlushEvents(SDL_EVENT_GAMEPAD_UPDATE_COMPLETE, SDL_EVENT_GAMEPAD_STEAM_HANDLE_UPDATED);
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

    /**
     * Read once when the controller is connected, the same way {@link #numTouchpads} is. These
     * identify the hardware, so they cannot change while one device stays plugged into one index,
     * and a different device arriving goes through {@link #connectController()} which refreshes
     * them. Cached because callers use them to recognise particular hardware, which means asking
     * every poll, and a crossing per question is a lot to pay for an answer that never moves.
     */
    private int vendorId = 0;

    private int productId = 0;

    private boolean supportsTouchpad = false;

    private int numTouchpads = 0;

    private boolean supportsSensors = false;

    private boolean hasAccelerometer = false;

    private boolean hasGyroscope = false;

    /**
     * Volatile because it is written on the timer thread that connects the audio haptics a second after the
     * controller arrives, and read on whichever thread feeds haptic packets. That reader now also gates the
     * write on it, so a stale false would not merely delay the first effect, it would withhold haptics for
     * as long as the value stayed unpublished.
     */
    private volatile boolean supportsHaptic = false;

    private boolean needToClearTriggerEffect = false;

    private final SensorState sensorState = new SensorState();

    /**
     * Keyed by touchpad and finger together, see {@link #touchStateKey(int, int)}. Keying on the finger
     * alone would hand out the same instance for finger 0 of every touchpad, so a controller with more
     * than one touchpad, a Steam Deck for instance, would see its pads overwrite each other.
     */
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
            vendorId = 0;
            productId = 0;
            supportsTouchpad = false;
            numTouchpads = 0;
            supportsSensors = false;
            hasAccelerometer = false;
            hasGyroscope = false;
            supportsHaptic = false;
            return;
        }
        controllerGuid = nativeGetDeviceGuid(controllerPtr);
        vendorId = nativeGetVendorId(controllerPtr);
        productId = nativeGetProductId(controllerPtr);
        if(!Objects.equals(Configuration.SonyControllerFeature.NONE, sonyControllerFeature)) {
            numTouchpads = nativeGetNumTouchpads(controllerPtr);
            supportsTouchpad = numTouchpads > 0;
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

                    supportsHaptic = nativeConnectHaptics(IS_WINDOWS || IS_MAC, controllerPtr);

                    if (!supportsHaptic) {
                        if (count == 0) {
                            connectHaptics(10_000, count + 1);
                        } else if (nativeGetConnectionState(controllerPtr) != CONNECTION_STATE_WIRELESS) {
                            // A wireless controller is turned away on purpose, see nativeConnectHaptics: the
                            // interface that carries haptics is a USB audio one, and claiming it for a
                            // controller that is not on the cable would play one player's effects in
                            // another's hands. Reporting the designed outcome of a supported setup as a
                            // failure only sends whoever reads this log chasing it.
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
        jamepad_drain_polled_events();
    */

    /**
     * Polls without asking SDL to re-read every device first, for callers that have already called
     * {@link ControllerManager#update()} this cycle.
     * <p>
     * {@link ControllerManager#update()} calls SDL_UpdateGamepads() itself, so a loop that updates
     * once and then polls each controller repeats that work per controller: with four pads, five
     * full device walks where one would do, each one iterating every joystick and running the HIDAPI
     * device updates. State read after this is the state that update() fetched, microseconds earlier
     * in the same cycle, and every pad then reports the same instant rather than each being sampled
     * as it is reached.
     * <p>
     * The event drain is kept, because that is the part which has to happen: SDL queues events for
     * every sample it gathers and nothing here reads them, so leaving them would reach
     * SDL_MAX_QUEUED_EVENTS and from then on SDL would refuse the device added and removed events
     * hot plug needs. Draining once per controller against one update per cycle keeps up with the
     * queue more easily than the two updates per drain that calling {@link #poll()} after
     * update() produces.
     * <p>
     * Use {@link #poll()} instead when nothing else is refreshing SDL, which is the safe default.
     */
    public void pollNoUpdate() throws ControllerUnpluggedException {
        ensureConnected();
        nativePollNoUpdate(controllerPtr);
    }

    private native void nativePollNoUpdate(long controllerPtr); /*
        jamepad_drain_polled_events();
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

    private native int nativeGetNumTouchpads(long controllerPtr); /*{
        return (jint) SDL_GetNumGamepadTouchpads(jamepad_pad(controllerPtr));
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

    // Which controller the single output above belongs to, as an SDL joystick id, or 0 for none.
    //
    // There is one audio handle for the whole process and no way to tell two DualSense playback devices
    // apart, since they carry the same name. So the handle has an owner, and only the owner is told it has
    // haptics: a second DualSense answered yes would be feeding its player's effects into the first
    // player's controller, because the stream leads there and nowhere else. It falls back to rumble
    // instead, and over Bluetooth to whatever route the caller has for that transport.
    static SDL_JoystickID haptics_owner = 0;
    static Uint8 *haptics_remix_buf = NULL;
    static int haptics_remix_capacity = 0;

    // Flow control for that stream. The console produces packets on its own clock and the
    // controller consumes them on its own, with a jittery network in between and nothing
    // reconciling the two, so the queue drifts one way and never comes back. Every millisecond
    // of backlog is a millisecond of delay and a millisecond of amplitude the motors still owe,
    // which is why a long session ends up feeling harsher than it started.
    //
    // SDL_GetAudioStreamQueued reports bytes still waiting in the stream's *input* format, so
    // the budget is in the 3 kHz 4 channel signed 16 bit the stream is fed: 24 bytes per ms.
    #define JAMEPAD_HAPTICS_QUEUED_BYTES_PER_MS (3000 * 4 * 2 / 1000)

    // Hard ceiling. Past this the backlog is delay the player can feel, so it goes.
    #define JAMEPAD_HAPTICS_MAX_QUEUED_MS 60

    // Soft ceiling, only applied while the arriving packet is silent. Dropping during silence
    // cannot cut an effect short, so the usual case resyncs without an audible seam.
    #define JAMEPAD_HAPTICS_SILENT_QUEUED_MS 20

    // Peak sample below which a packet counts as silence, the same threshold the Valve actuator
    // path uses in HapticAnalyzerSettings.DEFAULT_SILENCE_PEAK: about -38 dBFS, well under any
    // deliberate effect and above the noise floor of the console's own mix.
    #define JAMEPAD_HAPTICS_SILENCE_PEAK 400

    static bool jamepad_haptics_is_silent(const Uint8 *packet, int size) {
        for (int i = 0; i + 2 <= size; i += 2) {
            const Sint16 sample = (Sint16) ((Uint16) packet[i] | ((Uint16) packet[i + 1] << 8));
            const int magnitude = sample < 0 ? -(int) sample : (int) sample;
            if (magnitude >= JAMEPAD_HAPTICS_SILENCE_PEAK) {
                return false;
            }
        }
        return true;
    }

    static void jamepad_close_haptics() {
        if (haptics_stream != NULL) {
            SDL_DestroyAudioStream(haptics_stream);
            haptics_stream = NULL;
        }
        if (haptics_output != 0) {
            SDL_CloseAudioDevice(haptics_output);
            haptics_output = 0;
        }
        haptics_owner = 0;
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

    private native boolean nativeConnectHaptics(boolean isWindowsOrMac, long controllerPtr); /*
        SDL_Joystick *joystick = jamepad_joystick(controllerPtr);
        const SDL_JoystickID id = SDL_GetJoystickID(joystick);
        if(id == 0) {
            //Closed or replaced between the connect and this call, which runs on a timer a second later.
            //Zero is also the "nobody owns it" marker below, so claiming under it would both call a gone
            //controller haptics-capable and leave the handle looking free to the next one along.
            return JNI_FALSE;
        }

        //The audio interface these haptics travel over belongs to the controller's USB descriptor and does
        //not exist over Bluetooth. So a wireless controller matching a device by name is matching some
        //other controller's device: with a wired and a wireless DualSense in one session, whichever ran
        //this first would win the handle, and had it been the wireless one it would have spent the whole
        //session playing its haptics into the wired player's hands. Wireless DualSense haptics are the
        //HID writer's job instead, and it needs this to answer no so it knows to take over.
        //Only an explicit wireless answer is refused; a backend that cannot tell keeps its old behaviour.
        if(SDL_GetJoystickConnectionState(joystick) == SDL_JOYSTICK_CONNECTION_WIRELESS) {
            return JNI_FALSE;
        }

        if(haptics_output != 0) {
            if(haptics_owner == id) {
                return JNI_TRUE; // already initialized, for this controller
            }
            //Somebody else holds it. If that controller is still attached the answer is simply no, see
            //haptics_owner. If it is gone without having closed the handle, the handle is stranded:
            //nothing could ever claim it again and the DualSense still in the session would be left on
            //rumble for the rest of the process, so take it over.
            if(haptics_owner != 0 && SDL_GetJoystickFromID(haptics_owner) != NULL) {
                return JNI_FALSE;
            }
            jamepad_close_haptics();
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

            //Only a four channel device can carry haptics: they are channels 3 and 4, and anything
            //narrower means SDL downmixes them into the speaker instead. So a device that opened with
            //fewer is not a haptic output, and claiming it would be worse than not finding one, because
            //the caller would stop looking for a path that works.
            //
            //This is also what tells a DualSense apart from a DualShock 4, whose playback device carries
            //the same "Wireless Controller" name on Windows and is not four channel. Matching on the name
            //alone would open the wrong controller's speaker and report haptics support for it.
            SDL_AudioSpec actual;
            SDL_zero(actual);
            if (SDL_GetAudioDeviceFormat(opened, &actual, NULL) && actual.channels != 4) {
                printf("NATIVE METHOD: playback device \"%s\" opened with %d channels instead of 4, "
                       "so it is not a DualSense haptic output\n", device_name, actual.channels);
                SDL_CloseAudioDevice(opened);
                continue;
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
            haptics_owner = id;
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
        //Only the controller the audio handle belongs to may close it. Closing it for any gamepad would
        //have one player leaving a session take the haptics of the player still holding a DualSense, since
        //the handle is process wide. Read the id before the gamepad goes, because it comes from the
        //gamepad.
        const bool owns_haptics = haptics_owner != 0
                && haptics_owner == SDL_GetJoystickID(jamepad_joystick(controllerPtr));

        SDL_Gamepad* pad = jamepad_pad(controllerPtr);
        if(pad) {
            SDL_CloseGamepad(pad);
        }
        if(owns_haptics) {
            jamepad_close_haptics();
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

    /**
     * @return how many touchpads this controller has, 0 when it has none or when Sony controller
     * features are off. A DualSense reports one, a Steam Deck reports two.
     */
    public int getNumTouchpads() {
        return numTouchpads;
    }

    /**
     * @param touchpad the index of the touchpad of interest, below {@link #getNumTouchpads()}
     * @return how many fingers that touchpad can track at once, 0 when the index is out of range or
     * when Sony controller features are off
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public int getNumTouchpadFingers(int touchpad) throws ControllerUnpluggedException {
        ensureConnected();
        if (!supportsTouchpad) {
            return 0;
        }
        return nativeGetNumTouchpadFingers(controllerPtr, touchpad);
    }

    private native int nativeGetNumTouchpadFingers(long controllerPtr, int touchpad); /*{
        return (jint) SDL_GetNumGamepadTouchpadFingers(jamepad_pad(controllerPtr), touchpad);
    }*/

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
     * @return true if the controller has an RGB LED that {@link #setLedColor} can drive
     * (e.g. the light bar of a DualSense or DualShock 4)
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean isSupportingLedColor() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeIsSupportingLedColor(controllerPtr);
    }

    private native boolean nativeIsSupportingLedColor(long controllerPtr); /*
        SDL_PropertiesID props = SDL_GetGamepadProperties(jamepad_pad(controllerPtr));
        return SDL_GetBooleanProperty(props, SDL_PROP_GAMEPAD_CAP_RGB_LED_BOOLEAN, false) ? JNI_TRUE : JNI_FALSE;
    */

    /**
     * Sets the color of the controller RGB LED (e.g. the light bar of a DualSense or DualShock 4).
     * Calling this on a controller without an RGB LED has no effect and returns false.
     *
     * @param red   the red intensity (0 to 255, passed as an unsigned byte)
     * @param green the green intensity (0 to 255, passed as an unsigned byte)
     * @param blue  the blue intensity (0 to 255, passed as an unsigned byte)
     * @return true if the LED color was set successfully, false otherwise
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public boolean setLedColor(byte red, byte green, byte blue) throws ControllerUnpluggedException {
        ensureConnected();
        return nativeSetLedColor(controllerPtr, red, green, blue);
    }

    private native boolean nativeSetLedColor(long controllerPtr, byte red, byte green, byte blue); /*
        return SDL_SetGamepadLED(jamepad_pad(controllerPtr), (Uint8) red, (Uint8) green, (Uint8) blue) ? JNI_TRUE : JNI_FALSE;
    */

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
     * Returns the serial number this controller reports, which for a Sony pad is its Bluetooth address.
     * <p>
     * This is the only thing SDL exposes that identifies a physical controller rather than a model, so it
     * is what lets a caller match a gamepad to the same device found through another API. Two controllers
     * of one model are otherwise indistinguishable here: the vendor and product ids are equal, the names
     * are equal, and the instance id and player index are SDL's own numbering, which nothing outside SDL
     * knows about.
     * <p>
     * Not every controller has one. SDL fills it in for the devices its own HID drivers handle, which
     * covers the DualSense and the DualShock 4 on either transport, and leaves it null for the rest.
     *
     * @return the serial number, or null when this controller does not report one
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public String getSerial() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetSerial(controllerPtr);
    }

    private native String nativeGetSerial(long controllerPtr); /*
        const char* serial = SDL_GetGamepadSerial(jamepad_pad(controllerPtr));
        return serial == NULL ? NULL : env->NewStringUTF(serial);
    */

    /** The controller could not be asked about its transport at all, so nothing is known. */
    public static final int CONNECTION_STATE_INVALID = -1;

    /** The backend driving this controller does not report which transport it arrived over. */
    public static final int CONNECTION_STATE_UNKNOWN = 0;

    /** Attached by cable. */
    public static final int CONNECTION_STATE_WIRED = 1;

    /** Attached wirelessly, which for the controllers SDL has its own drivers for means Bluetooth. */
    public static final int CONNECTION_STATE_WIRELESS = 2;

    /**
     * Which transport this controller arrived over, as one of the {@code CONNECTION_STATE_} values.
     * <p>
     * Worth having because several controller features exist on one transport and not the other, and until
     * now the only way to guess at the transport was to watch which of those features turned up. A
     * DualSense's haptics are the case in point: they travel over an audio interface belonging to its USB
     * descriptor, so a controller reporting no haptics support was taken to be wireless. That reads wrong
     * for the first second of a wired session, because the interface is opened on a delay, and it is the
     * kind of guess that gets a caller writing to the wrong device.
     * <p>
     * Not every backend fills this in, so {@link #CONNECTION_STATE_UNKNOWN} is a normal answer and means
     * only that the caller has to fall back to whatever it did before it could ask.
     *
     * @return one of the {@code CONNECTION_STATE_} values
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public int getConnectionState() throws ControllerUnpluggedException {
        ensureConnected();
        return nativeGetConnectionState(controllerPtr);
    }

    private native int nativeGetConnectionState(long controllerPtr); /*
        SDL_Joystick *joystick = jamepad_joystick(controllerPtr);
        if(joystick == NULL) {
            return (jint) SDL_JOYSTICK_CONNECTION_INVALID;
        }
        return (jint) SDL_GetJoystickConnectionState(joystick);
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
        return getTouchpadFinger(0, finger);
    }

    /**
     * To use this function Sony controller features must be enabled in configuration of the
     * {@link com.studiohartman.jamepad.ControllerManager}.
     * @param touchpad the index of the touchpad of interest, below {@link #getNumTouchpads()}
     * @param finger the index of the finger of interest
     * @return a TouchState object containing the touch information of the finger.
     * If the operation was not successful e.g. because the controller doesn't have
     * a touchpad then a default TouchState object is returned.
     * @throws ControllerUnpluggedException If the controller is not connected
     */
    public TouchState getTouchpadFinger(int touchpad, int finger) throws ControllerUnpluggedException {
        ensureConnected();

        TouchState touchState = touchStateFor(touchpad, finger);
        if(!supportsTouchpad){
            return touchState;
        }
        nativeGetTouchpadFinger(controllerPtr, touchpad, finger, touchState);

        return touchState;
    }

    private native void nativeGetTouchpadFinger(long controllerPtr, int touchpad, int finger, Object touchState); /*
        SDL_UpdateGamepads();

        bool down = false;
        float x, y, pressure;
        if(SDL_GetGamepadTouchpadFinger(jamepad_pad(controllerPtr), touchpad, finger, &down, &x, &y, &pressure)) {
            jclass clazz = env->GetObjectClass(touchState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(ZFFF)V");

            env->CallVoidMethod(touchState, update_method, down ? JNI_TRUE : JNI_FALSE, x, y, pressure);
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

        // There is one audio stream for the whole process and it leads to whichever controller opened it,
        // so a controller that does not hold it would be playing its packets in another player's hands.
        // supportsHaptic is only true for the holder, see nativeConnectHaptics.
        if(!supportsHaptic) {
            return false;
        }

        return nativeSendHapticFeedback(hapticFeedback, hapticFeedback.length);
    }

    private native boolean nativeSendHapticFeedback(byte[] hapticFeedback, int hapticFeedbackSize); /*
        if(haptics_stream == NULL) {
            return JNI_FALSE;
        }

        //Retire a backlog before adding to it, see the flow control notes above. A healthy stream
        //pays for one integer comparison per packet: the depth read is a cheap lock on the stream
        //and the silence scan only runs once the queue has already grown past the soft ceiling.
        const int queued = SDL_GetAudioStreamQueued(haptics_stream);
        if (queued > JAMEPAD_HAPTICS_MAX_QUEUED_MS * JAMEPAD_HAPTICS_QUEUED_BYTES_PER_MS) {
            SDL_ClearAudioStream(haptics_stream);
        } else if (queued > JAMEPAD_HAPTICS_SILENT_QUEUED_MS * JAMEPAD_HAPTICS_QUEUED_BYTES_PER_MS
                   && jamepad_haptics_is_silent((const Uint8 *) hapticFeedback, hapticFeedbackSize)) {
            SDL_ClearAudioStream(haptics_stream);
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
     * Works out what SDL already takes each of this controller's raw buttons to mean.
     * <p>
     * A raw button index says nothing on its own, being however the device happens to number its buttons.
     * The mapping SDL holds for the device is what turns one of them into, say, the east face button, or
     * into a trigger on a device that reports its triggers as either fully pressed or not at all. This
     * hands that correspondence back, so code holding a raw index can tell what pressing that button
     * already means to {@link #isButtonPressed(ControllerButton)} and {@link #getAxisState(ControllerAxis)}.
     * <p>
     * Both arrays are indexed by raw button index and are filled for their whole length, so they can be
     * sized with {@link #getNumRawButtons()} and read without checking anything else first. A raw button
     * SDL has no meaning for, an extra paddle among them, reads as -1 in both.
     *
     * @param buttonForRawButton filled with the {@link ControllerButton#getSdlValue()} each raw button
     *                           backs, or -1 where it backs no button
     * @param axisForRawButton   filled with the {@link ControllerAxis#getSdlValue()} each raw button backs,
     *                           or -1 where it backs no axis
     * @return how many raw buttons SDL has a meaning for, which is none at all for a device it holds no
     *         mapping for
     * @throws ControllerUnpluggedException If the controller is not connected.
     */
    public int getRawButtonOutputs(int[] buttonForRawButton, int[] axisForRawButton) throws ControllerUnpluggedException {
        ensureConnected();
        Arrays.fill(buttonForRawButton, -1);
        Arrays.fill(axisForRawButton, -1);
        return nativeGetRawButtonOutputs(controllerPtr, buttonForRawButton, buttonForRawButton.length,
                axisForRawButton, axisForRawButton.length);
    }

    private native int nativeGetRawButtonOutputs(long controllerPtr, int[] buttonForRawButton, int buttonLength,
                                                 int[] axisForRawButton, int axisLength); /*
        int bindingCount = 0;
        SDL_GamepadBinding **bindings = SDL_GetGamepadBindings(jamepad_pad(controllerPtr), &bindingCount);
        if (bindings == NULL) {
            return 0;
        }

        int found = 0;
        for (int i = 0; i < bindingCount; i++) {
            const SDL_GamepadBinding *binding = bindings[i];
            if (binding == NULL || binding->input_type != SDL_GAMEPAD_BINDTYPE_BUTTON) {
                continue; //a hat or an axis is not a raw button, so it has nothing to say here
            }

            const int rawButton = binding->input.button;
            if (binding->output_type == SDL_GAMEPAD_BINDTYPE_BUTTON) {
                if (rawButton >= 0 && rawButton < buttonLength) {
                    buttonForRawButton[rawButton] = (int) binding->output.button;
                    ++found;
                }
            } else if (binding->output_type == SDL_GAMEPAD_BINDTYPE_AXIS) {
                if (rawButton >= 0 && rawButton < axisLength) {
                    axisForRawButton[rawButton] = (int) binding->output.axis.axis;
                    ++found;
                }
            }
        }

        SDL_free(bindings);
        return found;
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
     * @return The USB Vendor ID (VID) of the controller, or 0 if nothing is connected at this index.
     * @throws ControllerUnpluggedException never; kept so that existing callers, which catch it
     *         because this used to reach the device on every call, continue to compile unchanged
     */
    public int getVendorId() throws ControllerUnpluggedException {
        return vendorId;
    }

    private native int nativeGetVendorId(long controllerPtr); /*
        return SDL_GetJoystickVendor(jamepad_joystick(controllerPtr));
    */

    /**
     * @return The USB Product ID (PID) of the controller, or 0 if nothing is connected at this index.
     * @throws ControllerUnpluggedException never; see {@link #getVendorId()}
     */
    public int getProductId() throws ControllerUnpluggedException {
        return productId;
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
        return getTouchpadFingerFast(0, finger);
    }

    public TouchState getTouchpadFingerFast(int touchpad, int finger) throws ControllerUnpluggedException {
        ensureConnected();

        TouchState touchState = touchStateFor(touchpad, finger);
        if (!supportsTouchpad) {
            return touchState;
        }

        nativeGetTouchpadFingerNoUpdate(controllerPtr, touchpad, finger, touchState);
        return touchState;
    }

    private native void nativeGetTouchpadFingerNoUpdate(long controllerPtr, int touchpad, int finger, Object touchState); /*
        bool down = false;
        float x, y, pressure;
        if(SDL_GetGamepadTouchpadFinger(jamepad_pad(controllerPtr), touchpad, finger, &down, &x, &y, &pressure)) {
            jclass clazz = env->GetObjectClass(touchState);
            jmethodID update_method = env->GetMethodID(clazz, "update", "(ZFFF)V");
            env->CallVoidMethod(touchState, update_method, down ? JNI_TRUE : JNI_FALSE, x, y, pressure);
        }
    */

    /**
     * Returns this controller's reusable TouchState for a touchpad and finger pair, creating it on first
     * use. Reusing the instances is what keeps the fast getters allocation free.
     */
    private TouchState touchStateFor(int touchpad, int finger) {
        int key = touchStateKey(touchpad, finger);
        TouchState touchState = touchStates.get(key);
        if (touchState == null) {
            touchState = new TouchState();
            touchStates.put(key, touchState);
        }
        return touchState;
    }

    private static int touchStateKey(int touchpad, int finger) {
        return (touchpad << 16) | (finger & 0xFFFF);
    }

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

    /***********************************/
    /*** Bulk read (ONE JNI CALL) ***/
    /***********************************/

    /** Offset of the six axes in the float array, in {@link ControllerAxis} SDL order. */
    public static final int STATE_AXES = 0;

    /**
     * Offset of touchpad 0's first two fingers, four floats each: whether the finger is down as
     * 1 or 0, then x, y and pressure. Finger 1 therefore starts at {@code STATE_TOUCH + 4}.
     */
    public static final int STATE_TOUCH = 6;

    /** Offset of the accelerometer x, y and z readings. */
    public static final int STATE_ACCEL = 14;

    /** Offset of the gyroscope x, y and z readings. */
    public static final int STATE_GYRO = 17;

    /**
     * Offset of touchpad 1's first finger, in the same four floats as {@link #STATE_TOUCH}. Only a
     * device with two separate pads, a Steam Deck or Steam Controller, has one: everything else
     * reports it untouched. Only the first finger, because these are single finger pads.
     */
    public static final int STATE_TOUCH_SECOND_PAD = 20;

    /** Required length of the float array passed to {@link #readStateFast(float[], long[])}. */
    public static final int STATE_FLOATS = 24;

    /**
     * Required length of the timestamp array: the accelerometer sample time then the gyroscope
     * one, in nanoseconds, matching {@link SensorState#getAccelTimestamp()} and
     * {@link SensorState#getGyroTimestamp()}.
     */
    public static final int STATE_TIMESTAMPS = 2;

    /**
     * How many bits of the {@link #readStateFast(float[], long[])} mask are meaningful. Bit n
     * reports the button whose {@link ControllerButton#getSdlValue()} is n, so this is one past the
     * highest value any constant carries.
     */
    public static final int STATE_BUTTON_COUNT = buttonMaskWidth();

    /**
     * Derives the mask width from {@link ControllerButton} rather than stating it, so a button
     * added upstream widens the mask on its own instead of going quietly missing from every read.
     * Deliberately total: it cannot throw, because it runs during class initialisation, where an
     * exception would be wrapped in an ExceptionInInitializerError and leave ControllerIndex
     * permanently unusable for the life of the process.
     */
    private static int buttonMaskWidth() {
        int widest = 0;
        for (ControllerButton button : ControllerButton.values()) {
            widest = Math.max(widest, button.getSdlValue() + 1);
        }
        // The mask is a long, so a button SDL numbers 64 or above has nowhere to go. Clamping keeps
        // the shift defined and costs only that button, rather than risking the whole read.
        return Math.min(widest, Long.SIZE);
    }

    /**
     * Reads the whole controller in a single JNI call.
     * <p>
     * The per-field getters above cost one crossing each, so a streaming client that wants
     * buttons, axes, touch and motion pays around twenty five per controller per poll, and every
     * one of them takes SDL's global recursive joystick mutex that the rumble, LED and trigger
     * writes on other threads also want. Worse, the touch and sensor getters each call back *into*
     * Java to populate their result object, so the real crossing count is higher than the number of
     * native methods suggests. Filling caller-owned primitive arrays removes both directions at
     * once: one call down, nothing back up, and no allocation.
     * <p>
     * Both arrays belong to the caller and should be allocated once per controller and reused.
     * Offsets are given by the {@code STATE_} constants. Values match what the individual getters
     * return, axes included, which are normalised to -1..1 the same way
     * {@link #getAxisStateFast(ControllerAxis)} normalises them.
     * <p>
     * Unlike {@link #isButtonPressedFast(ControllerButton)} this keeps no state of its own, so it
     * does not disturb the held and just-pressed bookkeeping those getters share, and either style
     * can be used without regard for the other. A caller that wants edge detection keeps the
     * previous mask and takes {@code mask & ~previousMask}, which is cheaper than the per-button
     * tracking anyway because it does every button at once.
     * <p>
     * Touch covers the first two fingers of touchpad 0, which is what a DualSense reports and what a
     * streaming client sends, plus the first finger of touchpad 1 at {@link #STATE_TOUCH_SECOND_PAD}
     * for the two pad devices. A device with a third touchpad, or one that tracks more fingers than
     * this, still needs {@link #getTouchpadFingerFast(int, int)} for the rest.
     * <p>
     * Like the fast getters this samples whatever the last {@link ControllerManager#update()} read
     * from the devices and does not refresh them itself, so a caller still updates once per cycle.
     * Reading every pad from a single device walk is the more correct arrangement anyway, since all
     * of them then describe the same instant rather than drifting apart mid-cycle. It does not drain
     * SDL's event queue either, which {@link #pollNoUpdate()} is for.
     *
     * @param floatsOut     array of at least {@link #STATE_FLOATS}, filled with the analog state
     * @param timestampsOut array of at least {@link #STATE_TIMESTAMPS}, filled with sensor sample times
     * @return a bitmask where bit n is set when the button with SDL value n is held
     * @throws ControllerUnpluggedException if the controller is not connected
     * @throws IllegalArgumentException     if either array is too short
     */
    public long readStateFast(float[] floatsOut, long[] timestampsOut) throws ControllerUnpluggedException {
        // Checked here as well as natively because the arrays are pinned for the duration of the
        // call, so a short one would be written straight past the end of a live heap object. Checked
        // before the connection, deliberately: a wrongly sized array is a permanent mistake in the
        // caller and should surface the same way whether or not a pad happens to be plugged in,
        // rather than hiding behind ControllerUnpluggedException until one is.
        if (floatsOut.length < STATE_FLOATS) {
            throw new IllegalArgumentException(
                    "floatsOut must hold at least " + STATE_FLOATS + " floats, got " + floatsOut.length);
        }
        if (timestampsOut.length < STATE_TIMESTAMPS) {
            throw new IllegalArgumentException(
                    "timestampsOut must hold at least " + STATE_TIMESTAMPS + " longs, got " + timestampsOut.length);
        }

        ensureConnected();

        return nativeReadStateFast(controllerPtr,
                numTouchpads, supportsSensors, STATE_BUTTON_COUNT,
                floatsOut, floatsOut.length,
                timestampsOut, timestampsOut.length);
    }

    private native long nativeReadStateFast(long controllerPtr,
                                            int touchpadCount,
                                            boolean readSensors,
                                            int buttonCount,
                                            float[] floatsOut, int floatsLength,
                                            long[] timestampsOut, int timestampsLength); /*
        if (floatsLength < 20 || timestampsLength < 2) {
            return 0; //the Java side rejects this first; this only keeps a pinned array safe
        }

        SDL_Gamepad* pad = jamepad_pad(controllerPtr);

        //One bit per SDL_GamepadButton value. The width comes from ControllerButton, so the two
        //cannot drift; re-clamped here because a shift of 64 or more is undefined behaviour and
        //this runs with the caller's arrays pinned.
        int buttons_to_read = buttonCount;
        if (buttons_to_read < 0) {
            buttons_to_read = 0;
        } else if (buttons_to_read > 64) {
            buttons_to_read = 64;
        }

        jlong buttons = 0;
        for (int i = 0; i < buttons_to_read; i++) {
            if (SDL_GetGamepadButton(pad, (SDL_GamepadButton) i)) {
                buttons |= ((jlong) 1) << i;
            }
        }

        for (int i = 0; i < 6; i++) {
            //Same division as getAxisStateFast, in the same precision, so the two agree bit for bit.
            floatsOut[i] = (float) SDL_GetGamepadAxis(pad, (SDL_GamepadAxis) i) / 32767.0f;
        }

        //Everything past the axes is zeroed first, so a pad without touch or motion reports zeroes
        //rather than stale values, and so a read SDL declines leaves zeroes behind too.
        int floats_to_fill = floatsLength < 24 ? floatsLength : 24;
        for (int i = 6; i < floats_to_fill; i++) {
            floatsOut[i] = 0.0f;
        }

        if (touchpadCount > 0) {
            for (int finger = 0; finger < 2; finger++) {
                bool down = false;
                float x = 0.0f, y = 0.0f, pressure = 0.0f;
                if (SDL_GetGamepadTouchpadFinger(pad, 0, finger, &down, &x, &y, &pressure)) {
                    int base = 6 + (finger * 4);
                    floatsOut[base] = down ? 1.0f : 0.0f;
                    floatsOut[base + 1] = x;
                    floatsOut[base + 2] = y;
                    floatsOut[base + 3] = pressure;
                }
            }

            //Touchpad 1 finger 0, the right pad of a Steam Deck or Steam Controller. Asked of the
            //device count rather than of SDL, so a one pad controller does not spend a declined call
            //and an error string on it every poll. The length check is the other half: a caller
            //compiled against the 20 float layout passes a shorter array than this needs, and must
            //keep working rather than losing the whole read.
            if (touchpadCount >= 2 && floatsLength >= 24) {
                bool down = false;
                float x = 0.0f, y = 0.0f, pressure = 0.0f;
                if (SDL_GetGamepadTouchpadFinger(pad, 1, 0, &down, &x, &y, &pressure)) {
                    floatsOut[20] = down ? 1.0f : 0.0f;
                    floatsOut[21] = x;
                    floatsOut[22] = y;
                    floatsOut[23] = pressure;
                }
            }
        }

        timestampsOut[0] = 0;
        timestampsOut[1] = 0;

        if (readSensors) {
            float accel[3] = { 0.0f, 0.0f, 0.0f };
            float gyro[3] = { 0.0f, 0.0f, 0.0f };
            SDL_GetGamepadSensorData(pad, SDL_SENSOR_ACCEL, accel, 3);
            SDL_GetGamepadSensorData(pad, SDL_SENSOR_GYRO, gyro, 3);

            floatsOut[14] = accel[0];
            floatsOut[15] = accel[1];
            floatsOut[16] = accel[2];
            floatsOut[17] = gyro[0];
            floatsOut[18] = gyro[1];
            floatsOut[19] = gyro[2];

            //Sample times only arrive on events, the same way jamepad_read_sensor_state collects
            //them. Safe inside the pinned region because it touches SDL's queue and not the JVM.
            jamepad_take_sensor_events();
            JamepadSensorClock *clock =
                jamepad_sensor_clock(SDL_GetJoystickID(SDL_GetGamepadJoystick(pad)), false);
            if (clock != NULL) {
                timestampsOut[0] = (jlong) clock->accelTimestamp;
                timestampsOut[1] = (jlong) clock->gyroTimestamp;
            }
        }

        return buttons;
    */
}
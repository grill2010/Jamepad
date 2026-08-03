package com.studiohartman.jamepad;

/**
 * Class defining the configuration of a {@link ControllerManager}.
 *
 * @author Benjamin Schulte
 */
public class Configuration {
    /**
     * The max number of controllers the ControllerManager should deal with
     */
    public int maxNumControllers = 4;

    /**
     * Use RawInput implementation instead of XInput on Windows, if applicable. Enable this if you
     * need to use more than four XInput controllers at once. Comes with drawbacks.
     */
    public boolean useRawInput = false;

    /**
     * Disable this to skip loading of the native library. Can be useful if an application wants
     * to use a loader other than {@link com.badlogic.gdx.jnigen.loader.SharedLibraryLoader}.
     */
    public boolean loadNativeLibrary = true;

    /**
     * Disable this to return to legacy temporary file loading of database file.
     */
    public boolean loadDatabaseInMemory = true;

    /**
     * Enable Sony controller features like touchpad and adaptive triggers.
     * DualSense also offers haptic feedback support.
     *
     * <p>Since 3.0.0.0 this no longer enables motion sensors. Use
     * {@link #useControllerMotionSensors} instead, which works for every controller SDL
     * reports a gyroscope or accelerometer for, not just Sony pads.
     */
    public SonyControllerFeature useSonyControllerFeatures = SonyControllerFeature.NONE;

    /**
     * Enable gyroscope and accelerometer reporting for every connected controller that
     * exposes them. This covers DualShock 4 and DualSense pads, Nintendo Switch Pro
     * controllers and Joy-Cons, and the Steam Deck's built-in controller, among others.
     *
     * <p>Readings are delivered in SDL's units: accelerometer values are in m/s&sup2;
     * (including gravity) and gyroscope values are in radians per second. Read them with
     * {@link ControllerIndex#getSensorState()}.
     *
     * <p>Motion reporting increases the USB or Bluetooth traffic of a controller
     * noticeably, so it is disabled by default.
     *
     * <p>SDL discards controller input, motion included, while the application does not
     * have focus. If you need motion in the background, call
     * {@code setSdlHint("SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS", "1")} on the
     * {@link ControllerManager} before initialising it. Enabling
     * {@link #useSonyControllerFeatures} already sets that hint for you.
     */
    public boolean useControllerMotionSensors = false;

    /**
     * Open the machine's own motion sensors in addition to any controller sensors. This
     * targets handhelds with a built-in IMU, such as the ROG Ally or the Legion Go, where
     * the motion hardware is a system sensor rather than part of a gamepad.
     *
     * <p>SDL only implements this on Windows (through the Windows Sensor API), Android and
     * a few consoles; on Linux and macOS no system sensors are reported. A Steam Deck
     * running Linux is covered by {@link #useControllerMotionSensors} instead, because SDL
     * exposes its IMU through the Steam Deck controller.
     *
     * <p>Access the readings with {@link ControllerManager#getSystemMotionSensors()}.
     */
    public boolean useSystemMotionSensors = false;

    public enum SonyControllerFeature {
        /**
         * Do not use any advanced Sony controller features
         */
        NONE(0),
        /**
         * Activate advanced DualSense features like touchpad and motion sensors
         */
        DUALSHOCK_FEATURES(1),

        /**
         * Activate advanced DualSense features like touchpad, motion sensors, adaptive triggers
         */
        DUALSENSE_FEATURES(2),

        /**
         * Activate advanced DualSense features like touchpad, motion sensors, adaptive triggers and haptic feedback
         */
        DUALSENSE_FEATURES_AND_HAPTICS(3);

        private final int value;
        private SonyControllerFeature(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

    }
}

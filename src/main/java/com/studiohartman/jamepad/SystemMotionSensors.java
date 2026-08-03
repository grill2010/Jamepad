package com.studiohartman.jamepad;

/**
 * The motion sensors built into the machine itself, rather than into a controller.
 *
 * <p>This exists for handhelds whose IMU is not part of a gamepad, such as the ROG Ally or
 * the Legion Go. Devices that report motion through their controller, including the Steam
 * Deck, are covered by {@link ControllerIndex#getSensorState()} instead.
 *
 * <p>SDL only implements system sensors on Windows, Android and a few consoles. Everywhere
 * else {@link #isAvailable()} returns false and the readings stay at zero.
 *
 * <p>Obtain an instance from {@link ControllerManager#getSystemMotionSensors()} after
 * enabling {@link Configuration#useSystemMotionSensors}. Readings use the same units and
 * axes as {@link SensorState}, but in the frame of the device chassis, which is not
 * necessarily the frame a controller would report. Handhelds vary in how their IMU is
 * mounted, so a per-device axis correction on the caller's side is usually still needed.
 */
public final class SystemMotionSensors {
    /*JNI
    #include <SDL3/SDL.h>

    static SDL_Sensor *jamepad_system_accel = NULL;
    static SDL_Sensor *jamepad_system_gyro = NULL;
    static SDL_SensorID jamepad_system_accel_id = 0;
    static SDL_SensorID jamepad_system_gyro_id = 0;
    static Uint64 jamepad_system_accel_timestamp = 0;
    static Uint64 jamepad_system_gyro_timestamp = 0;

    // SDL_GetSensorData has no timestamp, so pick the sample times off the event queue.
    static void jamepad_take_system_sensor_events() {
        SDL_Event events[32];
        int count;

        while ((count = SDL_PeepEvents(events, 32, SDL_GETEVENT,
                                       SDL_EVENT_SENSOR_UPDATE, SDL_EVENT_SENSOR_UPDATE)) > 0) {
            for (int i = 0; i < count; i++) {
                const SDL_SensorEvent *event = &events[i].sensor;

                if (event->which == jamepad_system_accel_id) {
                    jamepad_system_accel_timestamp = event->sensor_timestamp;
                } else if (event->which == jamepad_system_gyro_id) {
                    jamepad_system_gyro_timestamp = event->sensor_timestamp;
                }
            }
        }
    }
    */

    private static final int SENSOR_ACCEL = 1;

    private static final int SENSOR_GYRO = 2;

    private final SensorState sensorState = new SensorState();

    private final boolean hasAccelerometer;

    private final boolean hasGyroscope;

    private boolean closed;

    SystemMotionSensors() {
        int opened = nativeOpen();
        hasAccelerometer = (opened & SENSOR_ACCEL) != 0;
        hasGyroscope = (opened & SENSOR_GYRO) != 0;
    }

    /**
     * @return true if the machine reported at least one usable motion sensor
     */
    public boolean isAvailable() {
        return !closed && (hasAccelerometer || hasGyroscope);
    }

    /**
     * @return true if accelerometer readings are live. When false those axes stay at zero.
     */
    public boolean isSupportingAccelerometer() {
        return hasAccelerometer;
    }

    /**
     * @return true if gyroscope readings are live. When false those axes stay at zero.
     */
    public boolean isSupportingGyroscope() {
        return hasGyroscope;
    }

    /**
     * @return the driver's name for the accelerometer, useful for telling handheld models
     * apart when deciding on an axis correction, or null if there is no accelerometer
     */
    public String getAccelerometerName() {
        return hasAccelerometer ? nativeGetSensorName(SENSOR_ACCEL) : null;
    }

    /**
     * @return the driver's name for the gyroscope, or null if there is no gyroscope
     */
    public String getGyroscopeName() {
        return hasGyroscope ? nativeGetSensorName(SENSOR_GYRO) : null;
    }

    /**
     * Read the current motion state. The returned object is reused on every call, so copy
     * it if you need to keep a sample around.
     *
     * @return the latest readings, or an all-zero state if no system sensors are available
     */
    public SensorState getSensorState() {
        if (isAvailable()) {
            nativeUpdate(sensorState);
        }
        return sensorState;
    }

    /**
     * Release the sensors. Called for you by {@link ControllerManager#quitSDLGamepad()}.
     */
    public void close() {
        if (!closed) {
            closed = true;
            nativeClose();
        }
    }

    private native int nativeOpen(); /*
        if (SDL_WasInit(SDL_INIT_SENSOR) == 0) {
            return 0;
        }

        int count = 0;
        SDL_SensorID *ids = SDL_GetSensors(&count);
        if (ids == NULL) {
            return 0;
        }

        jint opened = 0;
        for (int i = 0; i < count; i++) {
            SDL_SensorType type = SDL_GetSensorTypeForID(ids[i]);

            if (type == SDL_SENSOR_ACCEL && jamepad_system_accel == NULL) {
                jamepad_system_accel = SDL_OpenSensor(ids[i]);
                if (jamepad_system_accel != NULL) {
                    jamepad_system_accel_id = ids[i];
                    opened |= 1;
                }
            } else if (type == SDL_SENSOR_GYRO && jamepad_system_gyro == NULL) {
                jamepad_system_gyro = SDL_OpenSensor(ids[i]);
                if (jamepad_system_gyro != NULL) {
                    jamepad_system_gyro_id = ids[i];
                    opened |= 2;
                }
            }
        }

        SDL_free(ids);
        return opened;
    */

    private native String nativeGetSensorName(int sensor); /*
        SDL_Sensor *handle = (sensor == 2) ? jamepad_system_gyro : jamepad_system_accel;
        if (handle == NULL) {
            return NULL;
        }

        const char *name = SDL_GetSensorName(handle);
        return name == NULL ? NULL : env->NewStringUTF(name);
    */

    private native void nativeUpdate(Object sensorState); /*
        SDL_UpdateSensors();

        float accel[3] = { 0.0f, 0.0f, 0.0f };
        float gyro[3] = { 0.0f, 0.0f, 0.0f };

        if (jamepad_system_accel != NULL) {
            SDL_GetSensorData(jamepad_system_accel, accel, 3);
        }
        if (jamepad_system_gyro != NULL) {
            SDL_GetSensorData(jamepad_system_gyro, gyro, 3);
        }

        jamepad_take_system_sensor_events();

        jclass clazz = env->GetObjectClass(sensorState);
        jmethodID update_method = env->GetMethodID(clazz, "update", "(FFFFFFJJ)V");
        env->CallVoidMethod(sensorState, update_method,
                            accel[0], accel[1], accel[2],
                            gyro[0], gyro[1], gyro[2],
                            (jlong) jamepad_system_accel_timestamp,
                            (jlong) jamepad_system_gyro_timestamp);
    */

    private native void nativeClose(); /*
        if (jamepad_system_accel != NULL) {
            SDL_CloseSensor(jamepad_system_accel);
            jamepad_system_accel = NULL;
        }
        if (jamepad_system_gyro != NULL) {
            SDL_CloseSensor(jamepad_system_gyro);
            jamepad_system_gyro = NULL;
        }

        jamepad_system_accel_id = 0;
        jamepad_system_gyro_id = 0;
        jamepad_system_accel_timestamp = 0;
        jamepad_system_gyro_timestamp = 0;
    */
}

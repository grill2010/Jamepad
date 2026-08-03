package com.studiohartman.jamepad;

/**
 * A snapshot of the accelerometer and gyroscope readings of a motion source.
 *
 * <p>Values use SDL's units. The accelerometer reports metres per second squared including
 * gravity, so a device lying flat and still reads roughly 9.81 on one axis. The gyroscope
 * reports radians per second, positive in the direction of a right-handed rotation around
 * the axis.
 *
 * <p>Axes follow SDL's convention, as seen from someone holding the device: +X points
 * right, +Y points up, and +Z points towards the holder.
 */
public class SensorState {

    /*** accel data ***/

    private float accelX;

    private float accelY;

    private float accelZ;

    /*** gyro data ***/

    private float gyroX;

    private float gyroY;

    private float gyroZ;

    private long accelTimestamp;

    private long gyroTimestamp;

    SensorState() {
    }

    SensorState(float accelX, float accelY, float accelZ, float gyroX, float gyroY, float gyroZ,
                long accelTimestamp, long gyroTimestamp) {
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.gyroX = gyroX;
        this.gyroY = gyroY;
        this.gyroZ = gyroZ;
        this.accelTimestamp = accelTimestamp;
        this.gyroTimestamp = gyroTimestamp;
    }

    public float getAccelX() {
        return accelX;
    }

    public float getAccelY() {
        return accelY;
    }

    public float getAccelZ() {
        return accelZ;
    }

    public float getGyroX() {
        return gyroX;
    }

    public float getGyroY() {
        return gyroY;
    }

    public float getGyroZ() {
        return gyroZ;
    }

    /**
     * The time the most recent of the two readings was taken, in nanoseconds.
     *
     * <p>This is the hardware timestamp SDL received with the sample, so it tracks the real
     * sampling interval rather than the moment the application happened to poll. Its origin
     * is driver defined and is not synchronised with the system clock, so only compare it
     * against other timestamps from the same source. A reading is 0 until the first sample
     * of that kind arrives.
     *
     * @return the newer of {@link #getAccelTimestamp()} and {@link #getGyroTimestamp()}
     */
    public long getTimestamp() {
        return Math.max(accelTimestamp, gyroTimestamp);
    }

    /**
     * The time the accelerometer reading was taken, in nanoseconds, or 0 if no accelerometer
     * sample has arrived yet. See {@link #getTimestamp()} for how to interpret the value.
     */
    public long getAccelTimestamp() {
        return accelTimestamp;
    }

    /**
     * The time the gyroscope reading was taken, in nanoseconds, or 0 if no gyroscope sample
     * has arrived yet. See {@link #getTimestamp()} for how to interpret the value.
     */
    public long getGyroTimestamp() {
        return gyroTimestamp;
    }

    void update(float accelX, float accelY, float accelZ, float gyroX, float gyroY, float gyroZ,
                long accelTimestamp, long gyroTimestamp) {
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.gyroX = gyroX;
        this.gyroY = gyroY;
        this.gyroZ = gyroZ;
        this.accelTimestamp = accelTimestamp;
        this.gyroTimestamp = gyroTimestamp;
    }

    void update(SensorState sensorState) {
        this.accelX = sensorState.accelX;
        this.accelY = sensorState.accelY;
        this.accelZ = sensorState.accelZ;
        this.gyroX = sensorState.gyroX;
        this.gyroY = sensorState.gyroY;
        this.gyroZ = sensorState.gyroZ;
        this.accelTimestamp = sensorState.accelTimestamp;
        this.gyroTimestamp = sensorState.gyroTimestamp;
    }
}

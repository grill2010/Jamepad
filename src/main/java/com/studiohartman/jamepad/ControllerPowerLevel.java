package com.studiohartman.jamepad;

/**
 * The power state of a controller, derived from enum SDL_PowerState in SDL_power.h.
 * <p>
 * SDL 3 dropped the bucketed {@code SDL_JoystickPowerLevel} in favour of a power
 * state plus a battery percentage, so the buckets this enum used to expose
 * (empty/low/medium/full) are gone. Use
 * {@link ControllerIndex#getBatteryPercentage()} for the charge level.
 *
 * @author Benjamin Schulte
 */
public enum ControllerPowerLevel {
    /**
     * The power state could not be determined
     */
    POWER_ERROR(-1),
    /**
     * Power state unknown
     */
    POWER_UNKNOWN(0),
    /**
     * Running on battery and not plugged in
     */
    POWER_ON_BATTERY(1),
    /**
     * Plugged in, no battery available
     */
    POWER_NO_BATTERY(2),
    /**
     * Plugged in and charging
     */
    POWER_CHARGING(3),
    /**
     * Plugged in and fully charged
     */
    POWER_CHARGED(4);

    private final int sdlValue;

    ControllerPowerLevel(int sdlValue) {
        this.sdlValue = sdlValue;
    }

    /**
     * @return the SDL_PowerState value this constant corresponds to
     */
    public int getSdlValue() {
        return sdlValue;
    }

    /**
     * @param sdlValue an SDL_PowerState value
     * @return the matching constant, or {@link #POWER_UNKNOWN} for values this
     * version of Jamepad does not know about
     */
    public static ControllerPowerLevel fromSdlValue(int sdlValue) {
        for (ControllerPowerLevel level : values()) {
            if (level.sdlValue == sdlValue) {
                return level;
            }
        }
        return POWER_UNKNOWN;
    }
}

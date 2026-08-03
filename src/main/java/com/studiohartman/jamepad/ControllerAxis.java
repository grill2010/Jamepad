package com.studiohartman.jamepad;

/**
 * This is an enumerated type for controller axes.
 * <p>
 * Each constant carries the SDL_GamepadAxis value it maps to, so the Java
 * declaration order is free to differ from SDL's.
 *
 * @author William Hartman
 */
public enum ControllerAxis {
    LEFTX(0),
    LEFTY(1),
    RIGHTX(2),
    RIGHTY(3),
    TRIGGERLEFT(4),
    TRIGGERRIGHT(5);

    private final int sdlValue;

    ControllerAxis(int sdlValue) {
        this.sdlValue = sdlValue;
    }

    /**
     * @return the SDL_GamepadAxis value this constant corresponds to
     */
    public int getSdlValue() {
        return sdlValue;
    }
}

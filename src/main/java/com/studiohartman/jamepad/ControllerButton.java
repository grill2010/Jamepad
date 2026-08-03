package com.studiohartman.jamepad;

/**
 * This is an enumerated type for controller buttons.
 * <p>
 * Each constant carries the SDL_GamepadButton value it maps to, so the Java
 * declaration order is free to differ from SDL's.
 * <p>
 * SDL 3 renamed the four face buttons to positional names. The historical
 * {@code A}/{@code B}/{@code X}/{@code Y} names are kept here because they are
 * what callers already use; they mean SOUTH/EAST/WEST/NORTH respectively, which
 * is the physical position on an Xbox-style pad.
 *
 * @author William Hartman
 */
public enum ControllerButton {
    /** SDL_GAMEPAD_BUTTON_SOUTH */
    A(0),
    /** SDL_GAMEPAD_BUTTON_EAST */
    B(1),
    /** SDL_GAMEPAD_BUTTON_WEST */
    X(2),
    /** SDL_GAMEPAD_BUTTON_NORTH */
    Y(3),
    BACK(4),
    GUIDE(5),
    START(6),
    LEFTSTICK(7),
    RIGHTSTICK(8),
    LEFTBUMPER(9),
    RIGHTBUMPER(10),
    DPAD_UP(11),
    DPAD_DOWN(12),
    DPAD_LEFT(13),
    DPAD_RIGHT(14),
    /** Xbox Series X share button, PS5 microphone button, Nintendo Switch Pro capture button */
    BUTTON_MISC1(15),
    /** Xbox Elite paddle P1 (upper right) */
    BUTTON_PADDLE1(16),
    /** Xbox Elite paddle P3 (upper left) */
    BUTTON_PADDLE2(17),
    /** Xbox Elite paddle P2 (lower right) */
    BUTTON_PADDLE3(18),
    /** Xbox Elite paddle P4 (lower left) */
    BUTTON_PADDLE4(19),
    /** PS4/PS5 touchpad button */
    BUTTON_TOUCHPAD(20),
    /** Additional button, controller specific */
    BUTTON_MISC2(21),
    /** Additional button, controller specific */
    BUTTON_MISC3(22),
    /** Additional button, controller specific */
    BUTTON_MISC4(23),
    /** Additional button, controller specific */
    BUTTON_MISC5(24),
    /** Additional button, controller specific */
    BUTTON_MISC6(25);

    private final int sdlValue;

    ControllerButton(int sdlValue) {
        this.sdlValue = sdlValue;
    }

    /**
     * @return the SDL_GamepadButton value this constant corresponds to
     */
    public int getSdlValue() {
        return sdlValue;
    }
}

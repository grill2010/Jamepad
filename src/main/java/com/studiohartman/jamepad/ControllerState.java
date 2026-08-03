package com.studiohartman.jamepad;

import java.io.Serializable;

/**
 * This class represents the state of a gamepad at a given moment. This includes
 * the state of the axes and the buttons.
 *
 * This is probably how most people should deal with gamepads.
 *
 * The isConnected field is pretty important. This is how you determine if the controller
 * you asked for is plugged in. If isConnected is false, all other fields will be zero or false.
 * For some applications, you might not need to even bother checking isConnected.
 *
 * All fields are public, but immutable.
 *
 * @author William Hartman
 */
public final class ControllerState implements Serializable {
    private static final ControllerState DISCONNECTED_CONTROLLER = new ControllerState();
    private static final long serialVersionUID = -3068755590868647792L;

    /**
     * Whether or not the controller is currently connected.
     *
     * If the controller is disconnected, all other fields will be 0 or false.
     */
    public final boolean isConnected;

    /**
     * A string describing the type of controller (i.e. "PS4 Controller" or "XInput Controller")
     */
    public final String controllerType;

    /**
     * The x position of the left stick between -1 and 1
     */
    public final float leftStickX;

    /**
     * The y position of the left stick between -1 and 1
     */
    public final float leftStickY;

    /**
     * The x position of the right stick between -1 and 1
     */
    public final float rightStickX;

    /**
     * The y position of the right stick between -1 and 1
     */
    public final float rightStickY;

    /**
     * The angle of the left stick (for reference, 0 is right, 90 is up, 180 is left, 270 is down)
     */
    public final float leftStickAngle;

    /**
     * The amount the left stick is pushed in the current direction. This probably between 0 and 1,
     * but this can't be guaranteed due to weird gamepads (like the square holes on a Logitech Dual Action)
     */
    public final float leftStickMagnitude;

    /**
     * The angle of the right stick (for reference, 0 is right, 90 is up, 180 is left, 270 is down)
     */
    public final float rightStickAngle;

    /**
     * The amount the right stick is pushed in the current direction. This probably between 0 and 1,
     * but this can't be guaranteed due to weird gamepads (like the square holes on a Logitech Dual Action)
     */
    public final float rightStickMagnitude;

    /**
     * Whether or not the left stick is clicked in
     */
    public final boolean leftStickClick;

    /**
     * Whether or not the right stick is clicked in
     */
    public final boolean rightStickClick;

    /**
     * The position of the left trigger between 0 and 1
     */
    public final float leftTrigger;

    /**
     * The position of the right trigger between 0 and 1
     */
    public final float rightTrigger;

    /**
     * Whether or not the left stick was just is clicked in
     */
    public final boolean leftStickJustClicked;

    /**
     * Whether or not the right stick was just is clicked in
     */
    public final boolean rightStickJustClicked;

    /**
     * Whether or not the a button is pressed
     */
    public final boolean a;

    /**
     * Whether or not the b button is pressed
     */
    public final boolean b;

    /**
     * Whether or not the x button is pressed
     */
    public final boolean x;

    /**
     * Whether or not the y button is pressed
     */
    public final boolean y;

    /**
     * Whether or not the left bumper is pressed
     */
    public final boolean lb;

    /**
     * Whether or not the right bumper is pressed
     */
    public final boolean rb;

    /**
     * Whether or not the start button is pressed
     */
    public final boolean start;

    /**
     * Whether or not the back button is pressed
     */
    public final boolean back;

    /**
     * Whether or not the guide button is pressed. For some controller/platform combinations this
     * doesn't work. You probably shouldn't use this.
     */
    public final boolean guide;

    /**
     * Whether or not the up button on the dpad is pushed
     */
    public final boolean dpadUp;

    /**
     * Whether or not the down button on the dpad is pushed
     */
    public final boolean dpadDown;

    /**
     * Whether or not the left button on the dpad is pushed
     */
    public final boolean dpadLeft;

    /**
     * Whether or not the right button on the dpad is pushed
     */
    public final boolean dpadRight;

    /**
     * Whether or not the a button was just pressed
     */
    public final boolean aJustPressed;


    /**
     * Whether or not the b button was just pressed
     */
    public final boolean bJustPressed;

    /**
     * Whether or not the x button was just pressed
     */
    public final boolean xJustPressed;

    /**
     * Whether or not the y button was just pressed
     */
    public final boolean yJustPressed;

    /**
     * Whether or not the left bumper was just pressed
     */
    public final boolean lbJustPressed;

    /**
     * Whether or not the right bumper was just pressed
     */
    public final boolean rbJustPressed;

    /**
     * Whether or not the start button was just pressed
     */
    public final boolean startJustPressed;

    /**
     * Whether or not the back button was just pressed
     */
    public final boolean backJustPressed;

    /**
     * Whether or not the guide button was just pressed
     */
    public final boolean guideJustPressed;

    /**
     * Whether or not the up button on the dpad was just pressed
     */
    public final boolean dpadUpJustPressed;

    /**
     * Whether or not the down button on the dpad was just pressed
     */
    public final boolean dpadDownJustPressed;

    /**
     * Whether or not the left button on the dpad was just pressed
     */
    public final boolean dpadLeftJustPressed;

    /**
     * Whether or not the right button on the dpad was just pressed
     */
    public final boolean dpadRightJustPressed;

    /**
     * Xbox Series X share button, PS5 microphone button, Nintendo Switch Pro capture button
     */
    public final boolean misc1;
    public final boolean misc1JustPressed;
    /**
     * Xbox Elite paddle P1
     */
    public final boolean paddle1;
    public final boolean paddle1JustPressed;
    /**
     * Xbox Elite paddle P3
     */
    public final boolean paddle2;
    public final boolean paddle2JustPressed;
    /**
     * Xbox Elite paddle P2
     */
    public final boolean paddle3;
    public final boolean paddle3JustPressed;
    /**
     * Xbox Elite paddle P4
     */
    public final boolean paddle4;
    public final boolean paddle4JustPressed;
    /**
     * PS4/PS5 touchpad button
     */
    public final boolean touchpadButton;
    public final boolean touchpadButtonJustPressed;

    /**
     * PS4/PS5 touchpad information for the finger with index 0.
     * If enhanced Sony controller features are
     * not enabled via the {@link com.studiohartman.jamepad.Configuration} or
     * if the controller reports that this feature is not supported this value
     * will be null.
     */
    public TouchState touchStateFinger0 = null;

    /**
     * PS4/PS5 touchpad information for the finger with index 1.
     * If enhanced Sony controller features are
     * not enabled via the {@link com.studiohartman.jamepad.Configuration} or
     * if the controller reports that this feature is not supported this value
     * will be null.
     */
    public TouchState touchStateFinger1 = null;

    /**
     * PS4/PS5 sensor information.
     * If enhanced Sony controller features are
     * not enabled via the {@link com.studiohartman.jamepad.Configuration} or
     * if the controller reports that this feature is not supported this value
     * will be null.
     */
    public SensorState sensorState = null;

    /**
     * Return a controller state based on the current state of the passed controller.
     *
     * If the controller a disconnected mid-read, the disconnected controller is returned, and the
     * pre-disconnection read data is ignored.
     *
     * @param c The ControllerIndex object whose state should be read.
     */
    static ControllerState getInstanceFromController(ControllerIndex c) {
        try {
            return new ControllerState(c);
        } catch (ControllerUnpluggedException e) {
            return DISCONNECTED_CONTROLLER;
        }
    }

    /**
     * Return a ControllerState that represents a disconnected controller. This object is shared.
     *
     * @return The ControllerState representing the disconnected controller.
     */
    static ControllerState getDisconnectedControllerInstance() {
        return DISCONNECTED_CONTROLLER;
    }

    private ControllerState(ControllerIndex c) throws ControllerUnpluggedException {
        isConnected = true;
        c.poll();
        controllerType = c.getName();
        leftStickX = c.getAxisStateFast(ControllerAxis.LEFTX);
        leftStickY = c.getAxisStateFast(ControllerAxis.LEFTY);
        rightStickX = c.getAxisStateFast(ControllerAxis.RIGHTX);
        rightStickY = c.getAxisStateFast(ControllerAxis.RIGHTY);
        leftStickAngle = (float) Math.toDegrees(Math.atan2(leftStickY, leftStickX));
        leftStickMagnitude = (float) Math.sqrt((leftStickX * leftStickX) + (leftStickY * leftStickY));
        rightStickAngle = (float) Math.toDegrees(Math.atan2(rightStickY, rightStickX));
        rightStickMagnitude = (float) Math.sqrt((rightStickX * rightStickX) + (rightStickY * rightStickY));
        leftTrigger = c.getAxisStateFast(ControllerAxis.TRIGGERLEFT);
        rightTrigger = c.getAxisStateFast(ControllerAxis.TRIGGERRIGHT);

        leftStickJustClicked = c.isButtonJustPressedFast(ControllerButton.LEFTSTICK);
        rightStickJustClicked = c.isButtonJustPressedFast(ControllerButton.RIGHTSTICK);
        leftStickClick = c.isButtonPressedFast(ControllerButton.LEFTSTICK);
        rightStickClick = c.isButtonPressedFast(ControllerButton.RIGHTSTICK);

        aJustPressed = c.isButtonJustPressedFast(ControllerButton.A);
        bJustPressed = c.isButtonJustPressedFast(ControllerButton.B);
        xJustPressed = c.isButtonJustPressedFast(ControllerButton.X);
        yJustPressed = c.isButtonJustPressedFast(ControllerButton.Y);
        lbJustPressed = c.isButtonJustPressedFast(ControllerButton.LEFTBUMPER);
        rbJustPressed = c.isButtonJustPressedFast(ControllerButton.RIGHTBUMPER);
        startJustPressed = c.isButtonJustPressedFast(ControllerButton.START);
        backJustPressed = c.isButtonJustPressedFast(ControllerButton.BACK);
        guideJustPressed = c.isButtonJustPressedFast(ControllerButton.GUIDE);
        dpadUpJustPressed = c.isButtonJustPressedFast(ControllerButton.DPAD_UP);
        dpadDownJustPressed = c.isButtonJustPressedFast(ControllerButton.DPAD_DOWN);
        dpadLeftJustPressed = c.isButtonJustPressedFast(ControllerButton.DPAD_LEFT);
        dpadRightJustPressed = c.isButtonJustPressedFast(ControllerButton.DPAD_RIGHT);
        misc1JustPressed = c.isButtonJustPressedFast(ControllerButton.BUTTON_MISC1);
        paddle1JustPressed = c.isButtonJustPressedFast(ControllerButton.BUTTON_PADDLE1);
        paddle2JustPressed = c.isButtonJustPressedFast(ControllerButton.BUTTON_PADDLE2);
        paddle3JustPressed = c.isButtonJustPressedFast(ControllerButton.BUTTON_PADDLE3);
        paddle4JustPressed = c.isButtonJustPressedFast(ControllerButton.BUTTON_PADDLE4);
        touchpadButtonJustPressed = c.isButtonJustPressedFast(ControllerButton.BUTTON_TOUCHPAD);

        a = c.isButtonPressedFast(ControllerButton.A);
        b = c.isButtonPressedFast(ControllerButton.B);
        x = c.isButtonPressedFast(ControllerButton.X);
        y = c.isButtonPressedFast(ControllerButton.Y);
        lb = c.isButtonPressedFast(ControllerButton.LEFTBUMPER);
        rb = c.isButtonPressedFast(ControllerButton.RIGHTBUMPER);
        start = c.isButtonPressedFast(ControllerButton.START);
        back = c.isButtonPressedFast(ControllerButton.BACK);
        guide = c.isButtonPressedFast(ControllerButton.GUIDE);
        dpadUp = c.isButtonPressedFast(ControllerButton.DPAD_UP);
        dpadDown = c.isButtonPressedFast(ControllerButton.DPAD_DOWN);
        dpadLeft = c.isButtonPressedFast(ControllerButton.DPAD_LEFT);
        dpadRight = c.isButtonPressedFast(ControllerButton.DPAD_RIGHT);
        misc1 = c.isButtonPressedFast(ControllerButton.BUTTON_MISC1);
        paddle1 = c.isButtonPressedFast(ControllerButton.BUTTON_PADDLE1);
        paddle2 = c.isButtonPressedFast(ControllerButton.BUTTON_PADDLE2);
        paddle3 = c.isButtonPressedFast(ControllerButton.BUTTON_PADDLE3);
        paddle4 = c.isButtonPressedFast(ControllerButton.BUTTON_PADDLE4);
        touchpadButton = c.isButtonPressedFast(ControllerButton.BUTTON_TOUCHPAD);

        if(c.isUsingSonyControllerFeatures()) {
            if(c.isSupportingTouchpadData()) {
                touchStateFinger0 = new TouchState();
                touchStateFinger0.update(c.getTouchpadFingerFast(0));
                touchStateFinger1 = new TouchState();
                touchStateFinger1.update(c.getTouchpadFingerFast(0));
            }

            if(c.isSupportingSensorData()) {
                sensorState = new SensorState();
                sensorState.update(c.getSensorStateFast());
            }
        }
    }

    private ControllerState() {
        isConnected = false;
        controllerType = "Not Connected";
        leftStickX = 0;
        leftStickY = 0;
        rightStickX = 0;
        rightStickY = 0;
        leftStickAngle = 0;
        leftStickMagnitude = 0;
        rightStickAngle = 0;
        rightStickMagnitude = 0;
        leftTrigger = 0;
        rightTrigger = 0;

        leftStickJustClicked = false;
        rightStickJustClicked = false;
        leftStickClick = false;
        rightStickClick = false;

        aJustPressed = false;
        bJustPressed = false;
        xJustPressed = false;
        yJustPressed = false;
        lbJustPressed = false;
        rbJustPressed = false;
        startJustPressed = false;
        backJustPressed = false;
        guideJustPressed = false;
        dpadUpJustPressed = false;
        dpadDownJustPressed = false;
        dpadLeftJustPressed = false;
        dpadRightJustPressed = false;
        misc1JustPressed = false;
        paddle1JustPressed = false;
        paddle2JustPressed = false;
        paddle3JustPressed = false;
        paddle4JustPressed = false;
        touchpadButtonJustPressed = false;

        a = false;
        b = false;
        x = false;
        y = false;
        lb = false;
        rb = false;
        start = false;
        back = false;
        guide = false;
        dpadUp = false;
        dpadDown = false;
        dpadLeft = false;
        dpadRight = false;
        misc1 = false;
        paddle1 = false;
        paddle2 = false;
        paddle3 = false;
        paddle4 = false;
        touchpadButton = false;

        touchStateFinger0 = null;
        touchStateFinger1 = null;
        sensorState = null;
    }
}

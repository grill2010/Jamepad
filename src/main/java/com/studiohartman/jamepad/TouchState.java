package com.studiohartman.jamepad;

/**
 * Contains information about the position of the finger on the touchpad of the controller.
 */
public class TouchState {
    private boolean state;

    private float x;

    private float y;

    private float pressure;

    TouchState() {
        this.state = false;
        this.x = 0;
        this.y = 0;
        this.pressure = 0;
    }

    TouchState(boolean state, float x, float y) {
        this(state, x, y, state ? 1 : 0);
    }

    TouchState(boolean state, float x, float y, float pressure) {
        this.state = state;
        this.x = x;
        this.y = y;
        this.pressure = pressure;
    }

    public boolean getState() {
        return state;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    /**
     * @return how hard the finger presses, from 0 to 1. Controllers that only report whether a finger
     * is down report 1 while it is down and 0 once it is lifted.
     */
    public float getPressure() {
        return pressure;
    }

    void update(boolean state, float x, float y){
        update(state, x, y, state ? 1 : 0);
    }

    void update(boolean state, float x, float y, float pressure){
        this.state = state;
        this.x = x;
        this.y = y;
        this.pressure = pressure;
    }

    void update(TouchState touchState) {
        this.state = touchState.state;
        this.x = touchState.x;
        this.y = touchState.y;
        this.pressure = touchState.pressure;
    }
}

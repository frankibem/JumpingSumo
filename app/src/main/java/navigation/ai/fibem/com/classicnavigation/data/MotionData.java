package navigation.ai.fibem.com.classicnavigation.data;

import java.util.ArrayList;

/**
 * Stores data related to the motion of the Jumping Sumo
 */
public class MotionData implements Observable {
    private byte prevTurnSpeed;
    private byte prevForwardSpeed;
    private byte turnSpeed;
    private byte forwardSpeed;

    private ArrayList<Observer> observers = new ArrayList<>();

    public byte getForwardSpeed() {
        return forwardSpeed;
    }

    public byte getPrevForwardSpeed() {
        return prevForwardSpeed;
    }

    public byte getPrevTurnSpeed() {
        return prevTurnSpeed;
    }

    public byte getTurnSpeed() {
        return turnSpeed;
    }

    public void setForwardSpeed(byte forwardSpeed) {
        updateMotion(forwardSpeed, turnSpeed);
    }

    public void setTurnSpeed(byte turnSpeed) {
        updateMotion(forwardSpeed, turnSpeed);
    }

    public void updateMotion(byte forwardSpeed, byte turnSpeed) {
        // Save the previous values
        prevForwardSpeed = this.forwardSpeed;
        prevTurnSpeed = this.turnSpeed;

        // Update
        this.forwardSpeed = forwardSpeed;
        this.turnSpeed = turnSpeed;

        // Notify observers
        notifyObservers();
    }

    @Override
    public void notifyObservers() {
        for (Observer obs : observers) {
            obs.update(this);
        }
    }

    @Override
    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public MotionData copy() {
        MotionData copy = new MotionData();
        copy.prevTurnSpeed = this.prevTurnSpeed;
        copy.prevForwardSpeed = this.prevForwardSpeed;
        copy.turnSpeed = this.turnSpeed;
        copy.forwardSpeed = this.forwardSpeed;

        return copy;
    }
}
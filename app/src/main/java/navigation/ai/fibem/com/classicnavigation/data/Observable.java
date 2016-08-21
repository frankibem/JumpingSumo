package navigation.ai.fibem.com.classicnavigation.data;

public interface Observable {
    /**
     * Update any observer of state changes
     */
    void notifyObservers();

    /**
     * Add an observer that can listen for changes
     *
     * @param observer
     */
    void addObserver(Observer observer);

    /**
     * Remove an observer (stop listening for changes)
     *
     * @param observer
     */
    void removeObserver(Observer observer);
}
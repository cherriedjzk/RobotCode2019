package framework;

public interface IMechanism<TMechanism extends IMechanism, TState> {
    //this method should essentially just be a switch statement between all of the states of the mechanism
    public abstract void TransitionTo(State<TMechanism,TState> state);
    public abstract TState GetCurrentState();
}
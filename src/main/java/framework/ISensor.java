package framework;

public interface ISensor<TState> {
    public TState GetState();
}
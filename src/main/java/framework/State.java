package framework;

public class State<M extends IMechanism, TState> {
    public final String Name;
    public final TState Value;

    public State(String name, TState value) {
        Name = name;
        Value = value;
    }
}
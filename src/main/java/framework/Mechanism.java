package framework;
import frc.robot.Robot;

public abstract class Mechanism<M extends Mechanism, TState> implements IMechanism<M, TState>{
    //classes that extend this should also make all their states as public static final state objects
    //mechanisms should define their child mechanisms as public so that they can be accessed from any other mechanism
    //all constants for a mechanism should be included in the smae class as the mechanism

    //each mechanism needs a reference to the root robot object so changes can be affected from any mechanism
    public Robot Robot; 
}
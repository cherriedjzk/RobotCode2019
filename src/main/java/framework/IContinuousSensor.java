package framework;

import edu.wpi.first.wpilibj.PIDSource;

public interface IContinuousSensor<TState> extends ISensor<TState>, PIDSource { 

}
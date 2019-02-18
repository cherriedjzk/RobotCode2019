/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package robotcode;

import edu.wpi.first.wpilibj.Joystick;

/**
 * Add your docs here.
 */
public class CustomJoystick extends Joystick{

    public static final int Profiles = 3;
    private int CurrentProfile = 0;

    public CustomJoystick(int port){
        super(port);
    }

    public boolean getRawButtonReleased(int button) {
        int realButton = button - CurrentProfile * 10;
        if(realButton <= 1 || realButton > 11){
            return false;
        }
        return super.getRawButtonReleased(realButton);
    }

    public boolean getRawButtonPressed(int button){
        int realButton = button - CurrentProfile * 10;
        if(realButton <= 1 || realButton > 11){
            return false;
        }
        return super.getRawButtonPressed(realButton);
    }

    public boolean getRawButton(int button){
        int realButton = button - CurrentProfile * 10;
        if(realButton <= 1 || realButton > 11){
            return false;
        }
        return super.getRawButton(realButton);
    }

    public double getX(int profile) {
        if (profile != CurrentProfile) {
            return 0;
        } else {
            return super.getX();
        }
    }

    public double getY(int profile) {
        if (profile != CurrentProfile) {
            return 0;
        } else {
            return super.getY();
        }
    }

    public double getZ(int profile){
        if (profile != CurrentProfile) {
            return 0;
        } else {
            return super.getZ();
        }
    }

    public void updateProfile() {
        if (super.getRawButtonReleased(1)){
            CurrentProfile = (CurrentProfile + 1) % Profiles;
        }
    }

    public int getProfile() {
        return CurrentProfile;
    }

}

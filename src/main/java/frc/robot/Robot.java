//testing second laptop

package frc.robot;

import java.awt.SecondaryLoop;
import java.util.ArrayList;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import com.kauailabs.navx.frc.AHRS;

import autonomous.AutonomousRoutineType;
import autonomous.commands.AutonomousCommand;
import autonomous.routines.DefaultRoutine;
import autonomous.routines.DoNothingRoutine;
import constants.DriveConstants;
import constants.HatchIntakeConstants;
import constants.Ports;
import constants.RunConstants;
import constants.RobotState;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.GenericHID.Hand;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import resource.ResourceFunctions;
import robotcode.driving.DriveTrain;
import robotcode.driving.Wheel;
import robotcode.pneumatics.SingleSolenoidReal;
import robotcode.systems.HatchIntake;
import sensors.LeadscrewEncoder;
import sensors.RobotAngle;
import sensors.TalonAbsoluteEncoder;

@SuppressWarnings("deprecation")
public class Robot extends SampleRobot {

	// *************//
	// VARIABLES //
	// *************//

	// controllers
	private XboxController mController;
	private Joystick mJoystick;

	// drive train
	private DriveTrain mDriveTrain;
	private Wheel[] mWheel = new Wheel[4];
	private WPI_TalonSRX[] mTurn = new WPI_TalonSRX[4];
	private WPI_TalonSRX[] mDrive = new WPI_TalonSRX[4];
	private TalonAbsoluteEncoder[] mEncoder = new TalonAbsoluteEncoder[4];

	// gyro
	private AHRS mNavX;

	// hatch intake
	private HatchIntake mHatchIntake;
	private WPI_TalonSRX mLeadscrewTalon;
	private LeadscrewEncoder mLeadscrewEncoder;
	private SingleSolenoidReal mHatchRotaryPiston;

	private RobotAngle mRobotAngle;

	// PDP and compressor
	private PowerDistributionPanel mPDP;
	private Compressor mCompressor;

	// autonomous setup
	private AutonomousRoutineType mAutonomousRoutine = AutonomousRoutineType.DEFAULT;

	// game setup
	private boolean mInGame = false;

	private long mGameStartMillis;
	RobotState mCurrentState = RobotState.DEFAULT;

	// ****************//
	// GENERAL CODE //
	// ****************//
	public Robot() {
	}

	@Override
	public void test() {
	}

	public void endGame() {
	}

	@Override
	public void robotInit() {

		mController = new XboxController(Ports.XBOX);
		//mNavX = new AHRS(Ports.NAVX);
		mPDP = new PowerDistributionPanel();

		if (RunConstants.RUNNING_DRIVE) {
			driveInit();
		}

		if (RunConstants.RUNNING_HATCH) {
			HatchIntakeInit();
		}
		
		if (RunConstants.SECONDARY_JOYSTICK) {
			mJoystick = new Joystick(Ports.JOYSTICK);
		}
		
		if (RunConstants.RUNNING_CAMERA) {
			UsbCamera camera = CameraServer.getInstance().startAutomaticCapture();
			camera.setResolution(240, 180);
			camera.setFPS(30);
		}
		
		if (RunConstants.RUNNING_PNEUMATICS) {
			mCompressor = new Compressor(Ports.COMPRESSOR);
		}
	}

	@Override
	public void autonomous() {
		// select auto commands
		ArrayList<AutonomousCommand> autonomousCommands;

		if (mAutonomousRoutine == AutonomousRoutineType.DEFAULT) {
			autonomousCommands = (new DefaultRoutine(this)).getAutonomousCommands();
		}
		else {
			autonomousCommands = (new DoNothingRoutine()).getAutonomousCommands();
		}

		// start game
		startGame();

		// initialize step variables
		int currentStep = 0;
		int previousStep = -1;

		while (isAutonomous() && isEnabled()) {
			SmartDashboard.putNumber("Autonomous step", currentStep);

			if (currentStep < autonomousCommands.size()) {
				AutonomousCommand command = autonomousCommands.get(currentStep);

				if (currentStep != previousStep) {
					command.startup();
					previousStep = currentStep;
				}

				boolean moveToNextStep = command.runCommand();
				if (moveToNextStep) {
					currentStep++;
				}
			} // else we're done with auto

			Timer.delay(0.005);
		}
	}

	public void operatorControl() {
		// start game, again
		startGame();

		while (isOperatorControl() && isEnabled()) {

			if (RunConstants.RUNNING_DRIVE) {
				swerveDrive();
			}

			if (RunConstants.RUNNING_HATCH) {
				if(mController.getBumper(Hand.kLeft)){
					mHatchIntake.setSpeed(-0.2);
				}
				else if(mController.getBumper(Hand.kRight)){
					mHatchIntake.setSpeed(0.2);
				}
				else if(mController.getStartButtonReleased()){
					mHatchIntake.zero();
				}
				else if(mController.getAButton()){
					mHatchIntake.setPosition(5);
				}
				else if(mController.getBButton()){
					mHatchIntake.setPosition(10);
				}
				else if(mController.getXButton()){
					mHatchIntake.setPosition(15);
				}
				else if(mController.getYButton()){
					mHatchIntake.setPosition(20);
				}
				else{
					mHatchIntake.setSpeed(0);
				}
				
				SmartDashboard.putNumber("Leadscrew raw ticks", mLeadscrewEncoder.getRawTicks());
				SmartDashboard.putNumber("Leadscrew inches", mLeadscrewEncoder.getDistanceInInchesFromEnd());
			}

			if (RunConstants.RUNNING_EVERYTHING) {
				doWork();
			}

			// put info on SmartDashboard
			SmartDashboard.putString("Current State", mCurrentState.toString());
			if (RunConstants.RUNNING_DRIVE) {
				for (int i = 0; i < 4; i++) {
					SmartDashboard.putNumber("Motor Current " + i, mDrive[i].getMotorOutputPercent());
				}
			}

			Timer.delay(0.005); // wait for a motor update time
		}
	}

	private void doWork() {
		switch (mCurrentState) {
			case DEFAULT:
				doSomeAction();
				break;
			default:
				throw new RuntimeException("Unknown state");
		}

		SmartDashboard.putString("Current State", mCurrentState.name());
	}

	private void doSomeAction() {
		// Do some action... move to a different state?
	}

	public void startGame() {
		if (!mInGame) {
			mGameStartMillis = System.currentTimeMillis();

			if (RunConstants.RUNNING_PNEUMATICS) {
				mCompressor.start();
			}
			else {
				mCompressor.stop();
			}

			mInGame = true;
		}
	}

	@Override
	public void disabled() {
		endGame();

		while (this.isDisabled()) {
			if (mJoystick.getTriggerPressed()) {
				// rotate autonomous routines to select which one to start with:
				if (mAutonomousRoutine == AutonomousRoutineType.DEFAULT) {
					mAutonomousRoutine = AutonomousRoutineType.DO_NOTHING;
				}
				else if (mAutonomousRoutine == AutonomousRoutineType.DO_NOTHING) {
					mAutonomousRoutine = AutonomousRoutineType.DEFAULT;
				}
			}

			SmartDashboard.putString("AUTO ROUTINE:", mAutonomousRoutine.toString());

			Timer.delay(0.005); // wait for a motor update time
		}
	}

	private void tankDrive() {
		if (RunConstants.RUNNING_DRIVE) {
			mDriveTrain.driveTank();
		}
	}

	private void crabDrive() {
		if (RunConstants.RUNNING_DRIVE) {
			mDriveTrain.driveCrab();
		}
	}

	private void swerveDrive() {
		if (RunConstants.RUNNING_DRIVE) {
			mDriveTrain.driveSwerve();
		}
	}

	public void driveInit() {
		int turnPort, turnOffset, drivePort, iZone, rotTol;
		double P_PID, I_PID, D_PID;
		boolean turnEncoderReversed, turnReversed, driveReversed;

		for (int i = 0; i < 4; i++) {
			if (RunConstants.IS_PROTOTYPE) { // determine values based on if prototype or real robot being used
				turnPort = Ports.PrototypeRobot.TURN[i];
				turnEncoderReversed = DriveConstants.PrototypeRobot.ENCODER_REVERSED[i];
				turnReversed = DriveConstants.PrototypeRobot.TURN_INVERTED[i];
				turnOffset = DriveConstants.PrototypeRobot.OFFSETS[i];
				driveReversed = DriveConstants.PrototypeRobot.DRIVE_INVERTED[i];
				drivePort = Ports.PrototypeRobot.DRIVE[i];
				P_PID = DriveConstants.PrototypeRobot.ROTATION_P[i];
				I_PID = DriveConstants.PrototypeRobot.ROTATION_I[i];
				D_PID = DriveConstants.PrototypeRobot.ROTATION_D[i];
				iZone = DriveConstants.PrototypeRobot.ROTATION_IZONE[i];
				rotTol = DriveConstants.PrototypeRobot.ROTATION_TOLERANCE[i];
			}
			else {
				turnPort = Ports.ActualRobot.TURN[i];
				turnEncoderReversed = DriveConstants.ActualRobot.ENCODER_REVERSED[i];
				turnReversed = DriveConstants.ActualRobot.TURN_INVERTED[i];
				turnOffset = DriveConstants.ActualRobot.OFFSETS[i];
				driveReversed = DriveConstants.ActualRobot.DRIVE_INVERTED[i];
				drivePort = Ports.ActualRobot.DRIVE[i];
				P_PID = DriveConstants.ActualRobot.ROTATION_P[i];
				I_PID = DriveConstants.ActualRobot.ROTATION_I[i];
				D_PID = DriveConstants.ActualRobot.ROTATION_D[i];
				iZone = DriveConstants.ActualRobot.ROTATION_IZONE[i];
				rotTol = DriveConstants.ActualRobot.ROTATION_TOLERANCE[i];
			}

			// initialize turn motors and set values:
			mTurn[i] = new WPI_TalonSRX(turnPort);
			mTurn[i].configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, 0, 10);
			mTurn[i].setNeutralMode(NeutralMode.Brake);
			mTurn[i].setSensorPhase(turnEncoderReversed);
			mTurn[i].setInverted(turnReversed);
			mTurn[i].config_kP(0, P_PID, 10);
			mTurn[i].config_kI(0, I_PID, 10);
			mTurn[i].config_kD(0, D_PID, 10);
			mTurn[i].config_IntegralZone(0, iZone, 10);
			mTurn[i].configAllowableClosedloopError(0, rotTol, 10);
			mTurn[i].configPeakOutputForward(1, 10);
			mTurn[i].configPeakOutputReverse(-1, 10);

			// initialize drive motors and set values:
			mDrive[i] = new WPI_TalonSRX(drivePort);
			mDrive[i].setInverted(driveReversed);
			mDrive[i].setNeutralMode(NeutralMode.Brake);
			mDrive[i].configPeakOutputForward(1, 10);
			mDrive[i].configPeakOutputReverse(-1, 10);
			mDrive[i].configPeakCurrentDuration(1000, 10);
			mDrive[i].configPeakCurrentLimit(150, 10);
			mDrive[i].configContinuousCurrentLimit(80, 10);
			mDrive[i].enableCurrentLimit(true);

			// initialize turn motors' encoders, as well as wheels:
			mEncoder[i] = new TalonAbsoluteEncoder(mTurn[i], ResourceFunctions.tickToAngle(turnOffset));
			mWheel[i] = new Wheel(mTurn[i], mDrive[i], mEncoder[i]);
		}

		mRobotAngle = new RobotAngle(mNavX, false, 0);
		mDriveTrain = new DriveTrain(mWheel, mController, mRobotAngle);
	}

	public DriveTrain getDriveTrain() {
		return mDriveTrain;
	}

	public void HatchIntakeInit(){
		mLeadscrewTalon = new WPI_TalonSRX(Ports.ActualRobot.LEADSCREW);

        mLeadscrewTalon.setInverted(HatchIntakeConstants.LeadScrew.REVERSED);
		mLeadscrewTalon.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, 0, 10);
		mLeadscrewTalon.setSensorPhase(HatchIntakeConstants.LeadScrew.ENCODER_REVERSED);

		mLeadscrewTalon.setNeutralMode(NeutralMode.Brake);
        mLeadscrewTalon.config_kP(0, HatchIntakeConstants.LeadScrew.PID.LEADSCREW_P, 10);
        mLeadscrewTalon.config_kI(0, HatchIntakeConstants.LeadScrew.PID.LEADSCREW_I, 10);
        mLeadscrewTalon.config_kD(0, HatchIntakeConstants.LeadScrew.PID.LEADSCREW_D, 10);
        mLeadscrewTalon.config_IntegralZone(0, HatchIntakeConstants.LeadScrew.PID.LEADSCREW_IZONE, 10);
		mLeadscrewTalon.configAllowableClosedloopError(0, HatchIntakeConstants.LeadScrew.PID.LEADSCREW_TOLERANCE, 10);
		
		mLeadscrewEncoder = new LeadscrewEncoder(mLeadscrewTalon, HatchIntakeConstants.LeadScrew.OFFSET);

		mHatchRotaryPiston = new SingleSolenoidReal(Ports.ActualRobot.HATCH_ROTARY_SOLENOID);

		mHatchIntake = new HatchIntake(mHatchRotaryPiston, mLeadscrewTalon, mLeadscrewEncoder);
	}

	private void addLogValueDouble(StringBuilder pLogString, double pVal) {
		pLogString.append(pVal);
		pLogString.append(",");
	}

	private void addLogValueInt(StringBuilder pLogString, int pVal) {
		pLogString.append(pVal);
		pLogString.append(",");
	}

	private void addLogValueLong(StringBuilder pLogString, long pVal) {
		pLogString.append(pVal);
		pLogString.append(",");
	}

	private void addLogValueBoolean(StringBuilder pLogString, boolean pVal) {
		pLogString.append(pVal ? "1" : "0");
		pLogString.append(",");
	}

	private void addLogValueString(StringBuilder pLogString, String pVal) {
		pLogString.append(pVal);
		pLogString.append(",");
	}

	private void addLogValueEndDouble(StringBuilder pLogString, double pVal) {
		pLogString.append(pVal);
		pLogString.append("\n");
	}

	private void addLogValueEndInt(StringBuilder pLogString, int pVal) {
		pLogString.append(pVal);
		pLogString.append("\n");
	}

	private void addLogValueEndLong(StringBuilder pLogString, long pVal) {
		pLogString.append(pVal);
		pLogString.append("\n");
	}

	private void addLogValueEndBoolean(StringBuilder pLogString, boolean pVal) {
		pLogString.append(pVal ? "1" : "0");
		pLogString.append("\n");
	}

	private void addLogValueEndString(StringBuilder pLogString, String pVal) {
		pLogString.append(pVal);
		pLogString.append("\n");
	}

	public void log() {
		long time = System.currentTimeMillis();
		long timeElapsed = time - mGameStartMillis;

		SmartDashboard.putBoolean("Game Has Started:", mInGame);
		SmartDashboard.putNumber("Time Game Started:", mGameStartMillis);
		SmartDashboard.putNumber("Time Elapsed:", timeElapsed);

		StringBuilder logString = new StringBuilder();

		// for now it is one frame per line
		addLogValueInt(logString, (int) timeElapsed);

		addLogValueBoolean(logString, mController.getYButton());
		addLogValueBoolean(logString, mController.getBButton());
		addLogValueBoolean(logString, mController.getAButton());
		addLogValueBoolean(logString, mController.getXButton());
		addLogValueBoolean(logString, mController.getBumper(Hand.kLeft));
		addLogValueBoolean(logString, mController.getBumper(Hand.kRight));
		addLogValueDouble(logString, mController.getTriggerAxis(Hand.kLeft));
		addLogValueDouble(logString, mController.getTriggerAxis(Hand.kRight));
		addLogValueInt(logString, mController.getPOV());
		addLogValueBoolean(logString, mController.getStartButton());
		addLogValueBoolean(logString, mController.getBackButton());
		addLogValueDouble(logString, mController.getX(Hand.kLeft));
		addLogValueDouble(logString, mController.getY(Hand.kLeft));
		addLogValueDouble(logString, mController.getX(Hand.kRight));
		addLogValueDouble(logString, mController.getY(Hand.kRight));

		if (RunConstants.SECONDARY_JOYSTICK) {
			for (int i = 1; i < 12; i++) {
				addLogValueBoolean(logString, mJoystick.getRawButton(i));
			}
		}

		if (RunConstants.RUNNING_DRIVE) {
			for (int i = 0; i < 4; i++) {
				addLogValueDouble(logString, mTurn[i].getOutputCurrent());
				addLogValueDouble(logString, mDrive[i].getOutputCurrent());

				addLogValueDouble(logString, mTurn[i].getMotorOutputVoltage());
				addLogValueDouble(logString, mDrive[i].getMotorOutputVoltage());

				addLogValueDouble(logString, mEncoder[i].getAngleDegrees());
			}

			addLogValueDouble(logString, mDriveTrain.getDesiredRobotVel().getMagnitude());
			addLogValueDouble(logString, mDriveTrain.getDesiredRobotVel().getAngle());
			addLogValueDouble(logString, mDriveTrain.getDesiredAngularVel());
		}

		if (RunConstants.RUNNING_PNEUMATICS) {
			addLogValueDouble(logString, mCompressor.getCompressorCurrent());
		}
		addLogValueDouble(logString, mPDP.getTotalCurrent());
		addLogValueDouble(logString, mPDP.getVoltage());

		addLogValueString(logString, mCurrentState.toString());

		addLogValueEndDouble(logString, mRobotAngle.getAngleDegrees());

		SmartDashboard.putString("LogString", logString.toString());
	}
}
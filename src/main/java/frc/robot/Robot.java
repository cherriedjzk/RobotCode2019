package frc.robot;

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
import constants.JoystickConstants;
import constants.LeadscrewConstants;
import constants.Ports;
import constants.RunConstants;
import constants.RobotState;
import constants.BallIntakeConstants;
import constants.CameraConstants;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.networktables.*;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.GenericHID.Hand;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import edu.wpi.first.wpilibj.SampleRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import resource.ResourceFunctions;
import robotcode.driving.*;
import robotcode.driving.DriveTrain.LinearVelocity;
import robotcode.driving.DriveTrain.RotationalVelocity;
import robotcode.pneumatics.*;
import robotcode.CustomJoystick;
import robotcode.camera.*;
import robotcode.systems.BallIntakeMotor;
import robotcode.systems.HatchIntake;
import robotcode.systems.Intake;
import robotcode.systems.Leadscrew;
import sensors.LeadscrewEncoder;
import sensors.RobotAngle;
import sensors.TalonAbsoluteEncoder;
import framework.*;

@SuppressWarnings("deprecation")
public class Robot extends SampleRobot implements IMechanism<Robot, RobotState>{
	
	public XboxController PrimaryController;
	public CustomJoystick SecondaryController;

	public Mechanism<DriveTrain, DriveTrainState> Drivetrain;
	public Mechanism<HatchIntake, HatchIntakeState> HatchIntake;
	public Mechanism<BallIntake, BallIntakeState> BallIntake;
	public Mechanism<Leadscrew, LeadScrewState> Leadscrew;
	public Mechanism<Intake, IntakeState> Intake;
	
	public RobotAngle RobotAngle;
	public Limelight HatchCamera;
	// PDP and compressor
	public PowerDistributionPanel PDP;
	public Compressor Compressor;

	// autonomous setup
	private AutonomousRoutineType mAutonomousRoutine = AutonomousRoutineType.DEFAULT;

	// game setup
	private boolean mInGame = false;
	private long mGameStartMillis;
	RobotState mCurrentState = RobotState.INITIAL_HOLDING_HATCH;

	
	// *************//
	// GENERAL CODE //
	// *************//
	public Robot() {
	}

	public void test() {
	}

	public void endGame() {
	}

	public void startGame() {
		if (!mInGame) {
			mGameStartMillis = System.currentTimeMillis();

			if (RunConstants.RUNNING_PNEUMATICS) {
				mCompressor.start();
			}

			if (RunConstants.RUNNING_HATCH){
				mHatchIntake.contract();
			}

			if(RunConstants.RUNNING_LEADSCREW){
				mLeadscrew.leadscrewInitialZero();
				//Timer.delay(0.5);
				mLeadscrew.setPosition(LeadscrewConstants.MIDDLE);
				while (dummy()){
				// 	System.out.println("IN THE LOOP");
				}
			}

			mInGame = true;
		}
	}

	public boolean dummy() {
		int error = mLeadscrewEncoder.getError((int) mLeadscrewTalon.getClosedLoopTarget());
		SmartDashboard.putNumber("in dummy", System.currentTimeMillis());
		SmartDashboard.putNumber("dummy closed loop error", error);
		return error > LeadscrewConstants.PID.LEADSCREW_TOLERANCE;
	}

	public void robotInit() {
		PrimaryController = new XboxController(Ports.XBOX);
		NavX = new AHRS(Ports.NAVX);
		PDP = new PowerDistributionPanel();
		Compressor = new Compressor(Ports.COMPRESSOR);

		if (RunConstants.RUNNING_DRIVE) {
			Drivetrain = new Mechanism<DriveTrain, DriveTrainState>();
		}

		if (RunConstants.SECONDARY_JOYSTICK) {
			SecondaryController = new CustomJoystick(Ports.JOYSTICK);
		}

		if (RunConstants.RUNNING_HATCH) {
			hatchIntakeInit();
		}

		if (RunConstants.RUNNING_LEADSCREW) {
			leadscrewInit();
		}

		if (RunConstants.RUNNING_BALL){
			ballInit();
		}

		if (RunConstants.RUNNING_CAMERA) {
			UsbCamera camera = CameraServer.getInstance().startAutomaticCapture();
			camera.setResolution(240, 180);
			camera.setFPS(30);
		}

		if (RunConstants.RUNNING_LEADSCREW && RunConstants.RUNNING_HATCH) {
			intakeInit();
		}

		if(RunConstants.RUNNING_CLIMBER){
			climberInit();
		}

	}

	public void autonomous() {
		// select auto commands
		ArrayList<AutonomousCommand> autonomousCommands;

		if (mAutonomousRoutine == AutonomousRoutineType.DEFAULT) {
			autonomousCommands = (new DefaultRoutine(this)).getAutonomousCommands();
		} else {
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
			if (RUNNING_DRIVE) { 
				swerveDrive();
			}

			// hatch intake without leadscrew
			if (RunConstants.RUNNING_HATCH && !RunConstants.RUNNING_LEADSCREW && !RunConstants.RUNNING_EVERYTHING) { 
				mHatchIntake.enactMovement();
			}

			// ball
			if (RunConstants.RUNNING_BALL && !RunConstants.RUNNING_EVERYTHING) {
				mBallIntake.enactMovement();
			}

			// leadscrew without hatch intake or ball
			if (RunConstants.RUNNING_LEADSCREW && !RunConstants.RUNNING_HATCH && !RunConstants.RUNNING_BALL && !RunConstants.RUNNING_EVERYTHING) { 
				mLeadscrew.enactMovement();
			}
			
			// all intake things but not states -- for testing
			if (RunConstants.RUNNING_HATCH && RunConstants.RUNNING_LEADSCREW && RunConstants.RUNNING_BALL && !RunConstants.RUNNING_EVERYTHING){
				mIntake.enactMovement();
				if (SecondaryController.getRawButtonReleased(16)){
					mHatchIntake.setRotaryOpposite();
				}
			}

			if (RunConstants.RUNNING_EVERYTHING) {
				SmartDashboard.putNumber("z value", SecondaryController.getZ());
				doWork(); 
			}

			// put info on SmartDashboard
			SmartDashboard.putString("Current State", mCurrentState.toString());
			if (RunConstants.RUNNING_DRIVE) {
				for (int i = 0; i < 4; i++) {
					SmartDashboard.putNumber("Motor Output Percent " + i, mDrive[i].getMotorOutputPercent());
				}
			}

			SecondaryController.updateProfile();
			SmartDashboard.putNumber("JOYSTICK PROFILE NUMBER", SecondaryController.getProfile());
			SmartDashboard.putString("JOYSTICK PROFILE", (SecondaryController.getProfile() == 0) ? "HATCH/LEADSCREW" : "BALL");
			Timer.delay(0.005); // wait for a motor update time
		}
	}

	
	// SmartDashboard.putNumber("is enacting movement", System.currentTimeMillis());
	// SmartDashboard.putBoolean("Forward Limit Switch Closed", mLeadscrewTalon.getSensorCollection().isFwdLimitSwitchClosed());
	// SmartDashboard.putBoolean("Reverse Limit Switch Closed", mLeadscrewTalon.getSensorCollection().isRevLimitSwitchClosed());
	// SmartDashboard.putNumber("Leadscrew raw ticks", mLeadscrewEncoder.getRawTicks());
	// SmartDashboard.putNumber("leadscrew cooked ticks", mLeadscrewEncoder.getTicksFromEnd());
	// SmartDashboard.putNumber("Leadscrew inches", mLeadscrewEncoder.getDistanceInInchesFromEnd());
	// SmartDashboard.putNumber("Limelight angle",	NetworkTableInstance.getDefault().getTable("limelight").getEntry("tx").getDouble(0));
	// SmartDashboard.putNumber("Limelight error", CameraConstants.LimelightConstants.HEIGHT * Math.tan(Math.toRadians(NetworkTableInstance.getDefault().getTable("limelight").getEntry("tx").getDouble(0))));
	// SmartDashboard.putNumber("Leadscrew motor goal ticks", mLeadscrewTalon.getClosedLoopTarget());
	// SmartDashboard.putNumber("Leadscrew motor goal inches", LeadscrewEncoder.leadscrewTickToInch(mLeadscrewTalon.getClosedLoopTarget()));
	// SmartDashboard.putNumber("Leadscrew motor output", mLeadscrewTalon.getMotorOutputPercent());
	// SmartDashboard.putNumber("Leadscrew error", mLeadscrewTalon.getClosedLoopError());

	public void TransitionTo(State<Robot, RobotState> state) {
		switch (state.Value) {
			case INITIAL_HOLDING_HATCH:
				initialHoldingHatch();
				break;
			case HATCH_SCORE:
				hatchScore();
				break;
			case WAITING_TO_LOAD:
				waitingToLoad();
				break;
			case LOADING_HATCH:
				loadingHatch();
				break;
			case HATCH_PRESCORE:
				hatchPrescore();
				break;
			case BALL_PRESCORE:
				ballPrescore();
				break;
			case BALL_FRONT_SCORE:
				ballFrontScore();
				break;
			case BALL_BACK_SCORE:
				ballBackScore();
				break;
			case DEFENSE:
				defense();
				break;
			case CLIMB:
				climb();
				break;
			case DEFAULT:
				defaultState();
				break;
			default:
				throw new RuntimeException("Unknown state");
			}

		SmartDashboard.putString("Current State", mCurrentState.name());
	}

	/**
	 * initial robot state
	 * goes to HATCH_SCORE when we want to score or
	 * WAITING_TO_LOAD if we drop it
	 */
	private void initialHoldingHatch() {
		//mClimber.up();

		// when the robot wants to score...
		if (mIntake.holdingHatch() && SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.SCORE) && SecondaryController.getZ() < 0) {
			mCurrentState = RobotState.HATCH_SCORE;
		}

		// if we accidentally drop the panel...
		else if (mIntake.holdingHatch() && SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.LOAD)){
			mCurrentState = RobotState.WAITING_TO_LOAD;
		}

		//ball methods
	}

	/**
	 * Score the hatch. when it's done go to WAITING_TO_LOAD state
	 */
	private void hatchScore() {
		// if robot or driver says scoring is done...
		if (mIntake.scorePanel() || SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.HAS_SCORED)) {
			mCurrentState = RobotState.WAITING_TO_LOAD;
		}
		// dc look this over i thought smth ws wrong then forgot wwhat i thought
	}

	/**
	 * keep the intake in an idle kinda mode
	 * when you get the ball, go to BALL_PRESCORE
	 * when you wanna get the panel, go to LOADING_HATCH
	 */
	private void waitingToLoad() {
		// has_loaded button is pressed and thingy is flipped to ball side
		if (mIntake.idle() && SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.HAS_LOADED) && SecondaryController.getZ() > 0) {
			mCurrentState = RobotState.BALL_PRESCORE;
		}

		// dc implement limit switch here
		// load button is pressed and thingy is flipped to hatch side 
		if (mIntake.idle() && SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.LOAD) && SecondaryController.getZ() < 0) {
			mCurrentState = RobotState.LOADING_HATCH;
		}
		
		//dc in the case scoring fails might wanna be able to score again?
	}

	/**
	 * intake the hatch
	 * when either the robot or driver says it's done, go to HATCH_PRESCORE
	 */
	private void loadingHatch() {
		Drivetrain.enactMovement(0, 90, LinearVelocity.ANGLE_ONLY, 0, RotationalVelocity.NONE);
		// either robot or person says the thing has been intaken
		if (mIntake.intakePanel() || (SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.HAS_LOADED) && SecondaryController.getZ() < 0)){
			mCurrentState = RobotState.HATCH_PRESCORE;
		}
	}

	/**
	 * 
	 */
	private void hatchPrescore() {
		// when the robot wants to score...
		if (mIntake.holdingHatch() && SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.SCORE) && SecondaryController.getZ() < 0) {
			mCurrentState = RobotState.HATCH_SCORE;
		}

		// if we accidentally drop the panel...
		else if (mIntake.holdingHatch() && SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.LOAD)){
			mCurrentState = RobotState.WAITING_TO_LOAD;
		}
	}

	/**
	 * 
	 */
	private void ballPrescore() {
		// Do some action... move to a different state?
	}

	private void ballFrontScore() {
		// if robot or driver says scoring is done...
		if (mIntake.scoreBallHigh() || SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.HAS_SCORED)) {
			mCurrentState = RobotState.WAITING_TO_LOAD;
		}
	}

	private void ballBackScore() {
		// if robot or driver says scoring is done...
		if (mIntake.scoreBallLow() || SecondaryController.getRawButtonReleased(JoystickConstants.FinalRobotButtons.HAS_SCORED)) {
			mCurrentState = RobotState.WAITING_TO_LOAD;
		}
	}

	private void defense() {
		// Do some action... move to a different state?
	}

	private void climb() {
		// Do some action... move to a different state?
	}

	private void defaultState() {

	}


	public void disabled() {

		while (this.isDisabled()) {
			if (RunConstants.SECONDARY_JOYSTICK) {

				if (SecondaryController.getTriggerPressed()) {
					// rotate autonomous routines to select which one to start with:
					if (mAutonomousRoutine == AutonomousRoutineType.DEFAULT) {
						mAutonomousRoutine = AutonomousRoutineType.DO_NOTHING;
					} 
					else if (mAutonomousRoutine == AutonomousRoutineType.DO_NOTHING) {
						mAutonomousRoutine = AutonomousRoutineType.DEFAULT;
					}
				}
			}
		}

		// SmartDashboard.putString("AUTO ROUTINE:", mAutonomousRoutine.toString());
		Timer.delay(0.005); // wait for a motor update time
	}


	// ***************//
	// INITIALIZATION //
	// ***************//
	private void driveInit() {
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
			} else {
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
		Drivetrain = new DriveTrain(mWheel, PrimaryController, mRobotAngle);
	}

	private void hatchIntakeInit() {
		mHatchRotaryPiston = new DoubleSolenoidReal(Ports.ActualRobot.HATCH_ROTARY_SOLENOID_IN,
				Ports.ActualRobot.HATCH_ROTARY_SOLENOID_OUT);
		mHatchLinearPiston = new DoubleSolenoidReal(Ports.ActualRobot.HATCH_LINEAR_SOLENOID_IN,
				Ports.ActualRobot.HATCH_LINEAR_SOLENOID_OUT);

		mHatchIntake = new HatchIntake(mHatchRotaryPiston, mHatchLinearPiston, SecondaryController);
	}

	private void ballInit() {
		mBallHolder = new WPI_TalonSRX(Ports.ActualRobot.BALL_HOLDER);
		
		mBallHolder.setInverted(BallIntakeConstants.REVERSED);
		mBallHolder.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, 0, 10);
		mBallHolder.setSensorPhase(BallIntakeConstants.ENCODER_REVERSED);
		mBallHolder.setNeutralMode(NeutralMode.Brake);

		mBallHolder.config_kP(0, BallIntakeConstants.PID.HOLDER_P,10);
		mBallHolder.config_kI(0, BallIntakeConstants.PID.HOLDER_I, 10);
		mBallHolder.config_kD(0, BallIntakeConstants.PID.HOLDER_D, 10);
		mBallHolder.config_IntegralZone(0, BallIntakeConstants.PID.HOLDER_IZONE, 10);
		mBallHolder.configAllowableClosedloopError(0, BallIntakeConstants.PID.HOLDER_TOLERANCE, 10);

		mBallIntake = new BallIntakeMotor(mBallHolder, SecondaryController);
	}

	private void leadscrewInit() {
		mLeadscrewTalon = new WPI_TalonSRX(Ports.ActualRobot.LEADSCREW);

		mLeadscrewTalon.setInverted(LeadscrewConstants.REVERSED);
		mLeadscrewTalon.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, 0, 10);
		mLeadscrewTalon.setSensorPhase(LeadscrewConstants.ENCODER_REVERSED);
		mLeadscrewTalon.setNeutralMode(NeutralMode.Brake);

		mLeadscrewTalon.config_kP(0, LeadscrewConstants.PID.LEADSCREW_P, 10);
		mLeadscrewTalon.config_kI(0, LeadscrewConstants.PID.LEADSCREW_I, 10);
		mLeadscrewTalon.config_kD(0, LeadscrewConstants.PID.LEADSCREW_D, 10);
		mLeadscrewTalon.config_IntegralZone(0, LeadscrewConstants.PID.LEADSCREW_IZONE, 10);
		mLeadscrewTalon.configAllowableClosedloopError(0, LeadscrewConstants.PID.LEADSCREW_TOLERANCE, 10);

		mLeadscrewEncoder = new LeadscrewEncoder(mLeadscrewTalon);
		
		mHatchCamera = new Limelight();
		mHatchCamera.setPipeline(1);

		mLeadscrew = new Leadscrew(mLeadscrewTalon, mLeadscrewEncoder, mHatchCamera, SecondaryController);
		
	}

	private void intakeInit() {
		mIntake = new Intake(mHatchIntake, mLeadscrew, mHatchCamera, SecondaryController);
	}

	private void climberInit(){

	}


	// ******//
	// DRIVE //
	// ******//
	private void tankDrive() {
		if (RunConstants.RUNNING_DRIVE) {
			Drivetrain.driveTank();
		}
	}

	private void crabDrive() {
		if (RunConstants.RUNNING_DRIVE) {
			Drivetrain.driveCrab();
		}
	}

	private void swerveDrive() {
		if (RunConstants.RUNNING_DRIVE) {
			Drivetrain.driveSwerve();
		}
	}

	// ********//
	// LOGGING //
	// ********//
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

		addLogValueBoolean(logString, PrimaryController.getYButton());
		addLogValueBoolean(logString, PrimaryController.getBButton());
		addLogValueBoolean(logString, PrimaryController.getAButton());
		addLogValueBoolean(logString, PrimaryController.getXButton());
		addLogValueBoolean(logString, PrimaryController.getBumper(Hand.kLeft));
		addLogValueBoolean(logString, PrimaryController.getBumper(Hand.kRight));
		addLogValueDouble(logString, PrimaryController.getTriggerAxis(Hand.kLeft));
		addLogValueDouble(logString, PrimaryController.getTriggerAxis(Hand.kRight));
		addLogValueInt(logString, PrimaryController.getPOV());
		addLogValueBoolean(logString, PrimaryController.getStartButton());
		addLogValueBoolean(logString, PrimaryController.getBackButton());
		addLogValueDouble(logString, PrimaryController.getX(Hand.kLeft));
		addLogValueDouble(logString, PrimaryController.getY(Hand.kLeft));
		addLogValueDouble(logString, PrimaryController.getX(Hand.kRight));
		addLogValueDouble(logString, PrimaryController.getY(Hand.kRight));

		if (RunConstants.SECONDARY_JOYSTICK) {
			for (int i = 1; i < 12; i++) {
				addLogValueBoolean(logString, SecondaryController.getRawButton(i));
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

			addLogValueDouble(logString, Drivetrain.getDesiredRobotVel().getMagnitude());
			addLogValueDouble(logString, Drivetrain.getDesiredRobotVel().getAngle());
			addLogValueDouble(logString, Drivetrain.getDesiredAngularVel());
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

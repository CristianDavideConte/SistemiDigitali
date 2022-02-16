package com.example.sistemidigitali.model;

import static com.example.sistemidigitali.debugUtility.Debug.println;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class CustomSensorManager implements SensorEventListener {
    private static final float ACC_X_NOISE = 4.0184113E-6F;
    private static final float ACC_Y_NOISE = 1.6587680E-6F;
    private static final float ACC_Z_NOISE = 2.9210950E-6F;

    private static final float METER_TO_PIXEL_CONVERSION_FACTOR = 3779.5275590551F;
    private static final float NANO_TO_SECONDS = 1F / 1000000000F;
    private static final long NO_TIME_SET = -1;

    private static final long CALIBRATION_TOTAL_TIME = 20; //in seconds

    //Sensors variables
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    //Accelerometer variables
    private float[] startingAccelerations;
    private float[] currentAccelerations;
    private float[] deltaPositions;

    //Magnetometer variables
    private float[] currentMagneticFields;

    //Control variables
    private long lastMeasurementsTime;
    private boolean shouldMonitor;

    //Calibration variables
    private long startingCalibrationTime = -1;
    private float[] currentCalibrationAccelerations;
    private long currentCalibrationNumberOfMeasurements;
    private boolean calibrated;

    public CustomSensorManager(Context context) {
        //Monitoring is disabled by default
        this.shouldMonitor = false;

        //Sensors
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = this.sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        this.magnetometer = this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        //Register listeners for sensors
        this.sensorManager.registerListener(this, this.accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        this.sensorManager.registerListener(this, this.magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public boolean isMonitoring() {
        return this.shouldMonitor;
    }

    public void startMonitoring() {
        this.deltaPositions = new float[]{0,0,0};
        this.currentMagneticFields = new float[]{0,0,0};
        this.lastMeasurementsTime = NO_TIME_SET;
        this.shouldMonitor = true;
    }

    public void stopMonitoring() {
        this.shouldMonitor = false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(!this.shouldMonitor) return;
        if(event.sensor == this.accelerometer) this.updateAccelerometer(event);
        if(event.sensor == this.magnetometer) this.updateMagnetometer(event);
    }

    private void updateAccelerometer(SensorEvent event) {
        //Update the acceleration measurements
        if(this.lastMeasurementsTime == NO_TIME_SET) {
            this.startingAccelerations = event.values.clone();
        }
        this.currentAccelerations = event.values.clone(); //acceleration in m/s^2

        //Calculate elapsed time since last measurement
        final long currentMeasurementTime = event.timestamp;
        final float elapsed = (currentMeasurementTime - this.lastMeasurementsTime) * NANO_TO_SECONDS; //delta time in seconds
        final float timeSqr = elapsed * elapsed;

        //Calculate the phone orientation using the geomagnetic field sensor
        final float signX = Math.signum(this.currentMagneticFields[0]);
        final float signY = Math.signum(this.currentMagneticFields[1]);
        final float signZ = Math.signum(this.currentMagneticFields[2]);

        //Calculate the deltaX, deltaY, deltaZ since the last measurement
        final float deltaX = (this.currentAccelerations[0] - ACC_X_NOISE) * timeSqr; //deltaX in m
        final float deltaY = (this.currentAccelerations[1] - ACC_Y_NOISE) * timeSqr; //deltaY in m
        final float deltaZ = (this.currentAccelerations[2] - ACC_Z_NOISE) * timeSqr; //deltaZ in m

        //Update the previous values to the new values
        this.lastMeasurementsTime = currentMeasurementTime;
        //this.previousAccelerations = this.currentAccelerations.clone();
        this.deltaPositions = new float[]{
                this.deltaPositions[0] + deltaX,
                this.deltaPositions[1] + deltaY,
                this.deltaPositions[2] + deltaZ,
        };
        println(signX, signY, signZ);
        println(deltaX * 100, deltaY * 100, deltaZ * 100);
        println(this.deltaPositions[0],
                  this.deltaPositions[1],
                this.deltaPositions[2]);
        calibrate(event);
    }

    private void updateMagnetometer(SensorEvent event) {
        this.currentMagneticFields = event.values.clone();
        //println(event.values[0], event.values[1], event.values[2]);
    }

    private void calibrate(SensorEvent event) {
        if(this.startingCalibrationTime == -1) {
            this.calibrated = false;
            this.startingCalibrationTime = event.timestamp;
            this.currentCalibrationAccelerations = new float[]{0,0,0};
            this.currentCalibrationNumberOfMeasurements = 1;
        }
        if(this.calibrated) return;

        println((event.timestamp - this.startingCalibrationTime) * NANO_TO_SECONDS, CALIBRATION_TOTAL_TIME);
        if((event.timestamp - this.startingCalibrationTime) * NANO_TO_SECONDS < CALIBRATION_TOTAL_TIME) {
            float[] accelerations = event.values.clone();
            this.currentCalibrationAccelerations[0] += accelerations[0];
            this.currentCalibrationAccelerations[1] += accelerations[1];
            this.currentCalibrationAccelerations[2] += accelerations[2];
            this.currentCalibrationNumberOfMeasurements++;
        } else {
            this.calibrated = true;
            println("CALIBRATION DONE");
            println("NOISE_X:", this.currentAccelerations[0] / this.currentCalibrationNumberOfMeasurements);
            println("NOISE_Y:", this.currentAccelerations[1] / this.currentCalibrationNumberOfMeasurements);
            println("NOISE_Z:", this.currentAccelerations[2] / this.currentCalibrationNumberOfMeasurements);
        }

    }


    /**
     * FOR THE BACK_FACING_CAMERA:
     * deltaX > 0 -> movement from left to right
     * deltaX < 0 -> movement from right to left
     *
     * deltaY > 0 -> movement from far to close (from the photographer)
     * deltaY < 0 -> movement from close to far (from the photographer)
     *
     * deltaZ > 0 -> movement from bottom to top
     * deltaZ < 0 -> movement from top to bottom
     *
     * VALUES ARE REVERSED FOR THE FRONT_FACING_CAMERA
     *
     * @return the [deltaX, deltaY, deltaZ] from the beginning of the monitoring
     */
    public float[] getDeltaPositionsInPx(boolean shouldStartMonitoring) {
        if(shouldStartMonitoring) this.startMonitoring();
        return new float[] {
            this.deltaPositions[0] * METER_TO_PIXEL_CONVERSION_FACTOR,
            this.deltaPositions[1] * METER_TO_PIXEL_CONVERSION_FACTOR,
            this.deltaPositions[2] * METER_TO_PIXEL_CONVERSION_FACTOR,
        };
    }
}

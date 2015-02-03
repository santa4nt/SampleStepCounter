package com.swijaya.samplestepcounter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class StepCounterService extends Service {

    private static final String TAG = StepCounterService.class.getSimpleName();

    private SensorManager mSensorManager;
    private Sensor mStepCounter;
    private SensorEventListener mStepCounterListener;
    private PendingIntent mWakeupIntent;

    private int mStepsSinceReboot;

    public StepCounterService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mStepCounter == null) {
            // might be the first time this service is started; check sensor feature and initialize
            if (!initializeSensor()) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        else {
            // non-initialization service start
            // TODO
        }

        assert (mSensorManager != null);
        assert (mStepCounter != null);
        assert (mStepCounterListener != null);

        if (intent != null) {
            // we might have been started by a wakeful receiver; if so, release its wake lock
            if (WakeStepCounterReceiver.completeWakefulIntent(intent)) {
                Log.d(TAG, "Started by a wakeful receiver. Released wake lock.");
            }
            else {
                Log.d(TAG, "Was not started by a wakeful receiver.");
            }

            String action = intent.getAction();
            if (action != null) {
                if (action.equals(Constants.ACTION_FLUSH)) {
                    Log.i(TAG, "Flushing step counter sensor data.");
                    mSensorManager.flush(mStepCounterListener);
                }
            }
        }

        // regardless of how we got started, send a broadcast intent containing the
        // steps count we have so far
        broadcastSteps(mStepsSinceReboot);

        return START_STICKY;
    }

    private boolean initializeSensor() {
        Log.i(TAG, "Initializing step counter sensor.");

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
            mStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }

        if (mStepCounter == null) {
            Toast toast = Toast.makeText(this, R.string.toast_no_step_counter, Toast.LENGTH_SHORT);
            toast.show();
            return false;
        }

        // initialize a sensor event listener
        mStepCounterListener = this.new StepCounterListener();

        // register a listener for the step counter sensor
        if (!mSensorManager.registerListener(mStepCounterListener, mStepCounter,
                Constants.SENSOR_DELAY, Constants.MAX_REPORT_LATENCY)) {
            Toast toast = Toast.makeText(this, R.string.toast_err_step_counter_listener, Toast.LENGTH_SHORT);
            toast.show();
            mStepCounterListener = null;
            return false;
        }

        // approximate the maximum report latency, enough to take advantage of the hardware FIFO queue
        // but not so much that old events get lost
        int maxEvents = (int)(0.9 * mStepCounter.getFifoMaxEventCount());       // to be conservative, take 90% of the reported max FIFO event count
        // then estimate the time delta between which wake up the system to flush sensor data
        long wakeupDelay = (maxEvents / 10) * 1000;   // in milliseconds
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, WakeStepCounterReceiver.class);
        intent.setAction(Constants.ACTION_FLUSH);
        mWakeupIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        Log.d(TAG, "Setting a repeating alarm with delay hint of " + wakeupDelay + " milliseconds.");
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + wakeupDelay,
                wakeupDelay,
                mWakeupIntent);

        // all system go!
        return true;
    }

    @Override
    public void onDestroy() {
        if (mStepCounterListener != null) {
            mSensorManager.unregisterListener(mStepCounterListener, mStepCounter);
            mStepCounterListener = null;
            mStepCounter = null;
        }

        if (mWakeupIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(mWakeupIntent);
            mWakeupIntent = null;
        }
    }

    public class StepCounterListener implements SensorEventListener, SensorEventListener2 {

        @Override
        public void onSensorChanged(SensorEvent event) {
            long timestamp = event.timestamp;
            mStepsSinceReboot = (int) event.values[0];

            if (timestamp == 0 || mStepsSinceReboot == 0) {
                // ignore the activation event
                return;
            }

            Log.d(TAG, "Timestamp: " + timestamp + "; steps: " + mStepsSinceReboot);

            broadcastSteps(mStepsSinceReboot);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // pass?
        }

        @Override
        public void onFlushCompleted(Sensor sensor) {
            Log.d(TAG, "Explicit flush request completed.");
        }

    }

    private void broadcastSteps(int stepsSinceReboot) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(Constants.ACTION_STEPS_SINCE_REBOOT);
        broadcastIntent.putExtra(Constants.EXTRA_STEPS, stepsSinceReboot);
        sendBroadcast(broadcastIntent);
    }

}

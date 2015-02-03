package com.swijaya.samplestepcounter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class StepCounterService extends Service {

    private static final String TAG = StepCounterService.class.getSimpleName();

    private StepCounterSensor mStepCounter;
    private StepCounterSensor.StepCountListener mStepCounterListener;
    private PendingIntent mWakeupIntent;

    public StepCounterService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mStepCounterListener = this.new StepEventListener();
        mStepCounter = new StepCounterSensor(this,
                Constants.SENSOR_DELAY, Constants.MAX_REPORT_LATENCY,
                mStepCounterListener);
    }

    private int showErrorToastAndStopSelf(int resId) {
        Toast toast = Toast.makeText(this, resId, Toast.LENGTH_SHORT);
        toast.show();
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mStepCounter.isInitialized()) {
            // this might be the first time this service is started, do initialization routine
            try {
                mStepCounter.initialize();
            }
            catch (StepCounterSensor.StepCounterSensorException e) {
                return showErrorToastAndStopSelf(e.resId);
            }

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            if (mWakeupIntent != null) {
                alarmManager.cancel(mWakeupIntent);
            }

            // approximate the maximum report latency, enough to take advantage of the hardware FIFO queue
            // but not so much that old events get lost
            int maxEvents = (int)(0.9 * mStepCounter.getFifoMaxEventCount());       // to be conservative, take 90% of the reported max FIFO event count
            // then estimate the time delta between which wake up the system to flush sensor data
            long wakeupDelay = (maxEvents / 10) * 1000;   // in milliseconds
            Intent flushIntent = new Intent(this, WakeStepCounterReceiver.class);
            flushIntent.setAction(Constants.ACTION_FLUSH);
            mWakeupIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            Log.d(TAG, "Setting a repeating alarm with delay hint of " + wakeupDelay + " milliseconds.");
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + wakeupDelay,
                    wakeupDelay,
                    mWakeupIntent);
        }

        assert (mStepCounter.isInitialized());

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
                    mStepCounter.flush();
                }
                else if (action.equals(Constants.ACTION_RESET)) {
                    Log.i(TAG, "Resetting step counter relative anchor.");
                    mStepCounter.reset();
                }
            }
        }

        // regardless of how we got started, send a broadcast intent containing the
        // last seen (relative) step event
        broadcastStepEvent(mStepCounter.getLastSeenRelativeStepEvent());

        return START_STICKY;
    }

    private void broadcastStepEvent(StepCounterSensor.StepEvent stepEvent) {
        if (stepEvent != null) {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(Constants.ACTION_STEP_EVENT_RECEIVED);
            broadcastIntent.putExtra(Constants.EXTRA_STEP_EVENT, stepEvent);
            sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void onDestroy() {
        if (mStepCounter != null) {
            mStepCounter.deinitialize();
        }

        if (mWakeupIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(mWakeupIntent);
            mWakeupIntent = null;
        }
    }

    private class StepEventListener implements StepCounterSensor.StepCountListener {
        @Override
        public void onStepCountEvent(StepCounterSensor.StepEvent stepEvent) {
            Log.d(TAG, "Got a relative step event with timestamp: " + stepEvent.timestamp + " steps: " + stepEvent.steps);
            broadcastStepEvent(stepEvent);
        }
    }

}

package com.swijaya.samplestepcounter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class StepCounterSensor {

    public static class StepCounterSensorException extends Exception {
        public int resId;
        public StepCounterSensorException(int resId) {
            this.resId = resId;
        }
    }

    public static class StepEvent implements Parcelable {
        public long timestamp;
        public int steps;

        public StepEvent(long timestamp, int steps) {
            this.timestamp = timestamp;
            this.steps = steps;
        }

        private StepEvent(Parcel in) {
            timestamp = in.readLong();
            steps = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeLong(timestamp);
            out.writeInt(steps);
        }

        public static final Parcelable.Creator<StepEvent> CREATOR = new Parcelable.Creator<StepEvent>() {
            @Override
            public StepEvent createFromParcel(Parcel source) {
                return new StepEvent(source);
            }

            @Override
            public StepEvent[] newArray(int size) {
                return new StepEvent[size];
            }
        };
    }

    public interface StepCountListener {
        public void onStepCountEvent(StepEvent stepEvent);
    }

    private static final String TAG = StepCounterSensor.class.getSimpleName();

    private Context mContext;
    private int mSensorDelay;       // in microseconds
    private int mMaxReportLatency;  // in microseconds
    private boolean mInitialized;

    private SensorManager mSensorManager;
    private Sensor mStepCounter;
    private SensorEventListener mStepCounterListener;

    private StepCountListener mUiListener;
    private StepEvent mAnchorStepEvent;             // relative to the first time the sensor was activated
    private StepEvent mLastSeenStepEvent;           // relative to the first time the sensor was activated
    private StepEvent mLastSeenRelativeStepEvent;   // the vector difference between the former two

    public StepCounterSensor(Context context,
                             int sensorDelayU, int maxReportLatencyU,
                             StepCountListener uiListener) {
        assert (context != null);
        assert (uiListener != null);

        mContext = context;
        mSensorDelay = sensorDelayU;
        mMaxReportLatency = maxReportLatencyU;
        mUiListener = uiListener;

        mLastSeenRelativeStepEvent = new StepEvent(0, 0);
    }

    public void initialize() throws StepCounterSensorException {
        Log.i(TAG, "Initializing step counter sensor.");

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
            mStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }

        if (mStepCounter == null) {
            throw new StepCounterSensorException(R.string.toast_no_step_counter);
        }

        // initialize a sensor event listener
        mStepCounterListener = this.new StepCounterListener();

        // register a listener for the step counter sensor
        if (!mSensorManager.registerListener(mStepCounterListener, mStepCounter,
                mSensorDelay, mMaxReportLatency)) {
            throw new StepCounterSensorException(R.string.toast_err_step_counter_listener);
        }

        // all system go!
        mInitialized = true;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public StepEvent getLastSeenRelativeStepEvent() {
        return mLastSeenRelativeStepEvent;
    }

    public void flush() {
        mSensorManager.flush(mStepCounterListener);
    }

    public int getFifoMaxEventCount() {
        return mStepCounter.getFifoMaxEventCount();
    }

    /**
     * Reset the "anchor" step count to the one last seen. Future step events will be relative
     * to the latter.
     */
    public void reset() {
        if (mLastSeenStepEvent == null) {
            Log.w(TAG, "We have not seen any sensor event!");
            return;
        }
        mAnchorStepEvent = mLastSeenStepEvent;
        mLastSeenRelativeStepEvent = new StepEvent(0, 0);
    }

    public void deinitialize() {
        if (mStepCounterListener != null) {
            assert (mInitialized);
            mSensorManager.unregisterListener(mStepCounterListener, mStepCounter);
            mStepCounterListener = null;
            mStepCounter = null;
            mSensorManager = null;
        }
        mInitialized = false;
    }

    private class StepCounterListener implements SensorEventListener, SensorEventListener2 {

        @Override
        public void onSensorChanged(SensorEvent event) {
            long timestamp = event.timestamp;
            int steps = (int) event.values[0];

            if (timestamp == 0 || steps == 0) {
                // ignore the activation event
                return;
            }

            Log.d(TAG, "Timestamp: " + timestamp + "; steps: " + steps);

            if (mAnchorStepEvent == null) {
                // anchor the first ever step event we see
                mAnchorStepEvent = new StepEvent(timestamp, steps);
            }

            // keep updating the last step event we've seen
            mLastSeenStepEvent = new StepEvent(timestamp, steps);

            // in order to compute the vector difference between the former two,
            // which can give us relative timestamp and step count since we first "anchored" or reset
            mLastSeenRelativeStepEvent = new StepEvent(
                    mLastSeenStepEvent.timestamp - mAnchorStepEvent.timestamp,
                    mLastSeenStepEvent.steps - mAnchorStepEvent.steps
            );

            // fire a step count event with this event data relative to the first time we "anchored"
            if (mUiListener != null) {
                mUiListener.onStepCountEvent(mLastSeenRelativeStepEvent);
            }
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

}

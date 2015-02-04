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

/**
 * A wrapper class around the Step Count Sensor.
 */
public class StepCounterSensor {

    /**
     * A custom exception that can be configured with a String resource ID
     * (so that its catcher can use it to pass into a Toast, for instance).
     */
    public static class StepCounterSensorException extends Exception {
        public int resId;
        public StepCounterSensorException(int resId) {
            this.resId = resId;
        }
    }

    /**
     * A parcelable object that wraps sensor event data given to us by
     * the Step Count API (namely, timestamp and step count). This wrapper
     * is used mainly to be able to be passed into intents as extra.
     */
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

    /**
     * Consumer of this wrapper will be notified of sanitized sensor events (re-wrapped into
     * a StepEvent wrapper object) via this callback interface.
     */
    public interface StepCountListener {
        /**
         * The callback through which owner of this sensor wrapper can be notified with sanitized
         * step count event data that is relative(!) to the last tiem this wrapper was initialized.
         *
         * @param stepEvent a sanitized sensor event data representing relative step count since
         *                  the last time the wrapper StepCounterSensor object was reset or initialized
         */
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
    private StepEvent mOffset;                      // added to the latter

    /**
     */
    public StepCounterSensor(Context context,
                             int sensorDelayU, int maxReportLatencyU,
                             StepCountListener uiListener) {
        this(context, sensorDelayU, maxReportLatencyU, uiListener, 0, 0);
    }

    /**
     * This Step Count Sensor wrapper needs to be configured with two main values:
     * sensor delay and maximum report latency (both in microseconds). These are passed to--
     * and have the same meaning as--their respective parameters in
     * {@link SensorManager#registerListener(android.hardware.SensorEventListener, android.hardware.Sensor, int, int)}.
     *
     * @param context the context that owns this wrapper (e.g. a Service)
     * @param sensorDelayU the desired sensor delay in microseconds
     * @param maxReportLatencyU the desired maximum report latency in microseconds
     * @param uiListener (optional) callback object that will be passed sanitized sensor (step count)
     *                   event data when this wrapper itself is notified by its wrapped sensor
     * @param timestampOffset this offset value is added to all returned relative StepEvent value
     * @param stepcountOffset this offset value is added to all returned relative StepEvent value
     */
    public StepCounterSensor(Context context,
                             int sensorDelayU, int maxReportLatencyU,
                             StepCountListener uiListener,
                             long timestampOffset, int stepcountOffset) {
        assert (context != null);
        assert (uiListener != null);

        mContext = context;
        mSensorDelay = sensorDelayU;
        mMaxReportLatency = maxReportLatencyU;
        mUiListener = uiListener;

        mAnchorStepEvent = mLastSeenStepEvent = null;
        mLastSeenRelativeStepEvent = new StepEvent(0, 0);
        mOffset = new StepEvent(timestampOffset, stepcountOffset);
    }

    /**
     * Initialize this wrapper by initializing the sensor objects and resources internally.
     *
     * @throws StepCounterSensorException if critical sensor resources cannot be obtained; use
     *      StepCounterSensorException#resId as String resource ID pointing to a human-readable
     *      error message
     */
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

        mAnchorStepEvent = mLastSeenStepEvent = null;
        mLastSeenRelativeStepEvent = new StepEvent(0, 0);

        // all system go!
        mInitialized = true;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * Poll this wrapper to get the step count event data it last saw, relative to the first
     * time this wrapper was initialized or the last time this wrapper was reset.
     *
     * @return relative step count event data
     */
    public StepEvent getLastSeenRelativeStepEvent() {
        return new StepEvent(
                mLastSeenRelativeStepEvent.timestamp + mOffset.timestamp,
                mLastSeenRelativeStepEvent.steps + mOffset.steps
        );
    }

    /**
     * Flush the internal step count sensor's FIFO queue
     */
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
        mOffset = new StepEvent(0, 0);
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
                mUiListener.onStepCountEvent(new StepEvent(
                        mLastSeenRelativeStepEvent.timestamp + mOffset.timestamp,
                        mLastSeenRelativeStepEvent.steps + mOffset.steps
                ));
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

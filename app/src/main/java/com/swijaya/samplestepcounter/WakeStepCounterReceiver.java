package com.swijaya.samplestepcounter;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

public class WakeStepCounterReceiver extends WakefulBroadcastReceiver {

    private static final String TAG = WakeStepCounterReceiver.class.getSimpleName();

    public WakeStepCounterReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent wakeServiceIntent = new Intent(context, StepCounterService.class);

        String action = intent.getAction();
        if (action.equals(Constants.ACTION_STEPS_SINCE_REBOOT)) {
            Log.d(TAG, "Dispatched on reboot.");
        }
        else if (action.equals(Constants.ACTION_FLUSH)) {
            Log.d(TAG, "Dispatched by alarm manager for sensor flush.");
            wakeServiceIntent.setAction(action);
        }

        Log.d(TAG, "Starting wakeful service for StepCounterService.");
        startWakefulService(context, wakeServiceIntent);
    }

}

package com.swijaya.samplestepcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    // UI elements
    private TextView mTextSteps;

    private StepsReceiver mStepsReceiver;
    private boolean mActivityRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextSteps = (TextView) findViewById(R.id.textSteps);
    }

    @Override
    protected void onStart() {
        // register a broadcast receiver to handle update events from the service
        mStepsReceiver = this.new StepsReceiver();
        IntentFilter broadcastIntentFilter = new IntentFilter();
        broadcastIntentFilter.addAction(Constants.ACTION_STEPS_SINCE_REBOOT);
        registerReceiver(mStepsReceiver, broadcastIntentFilter);

        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityRunning = true;

        // start the semi-persistent background service that interfaces with the step counter sensor API
        // this can be the first time we start this service, or (statistically) not; either way,
        // the service's onStartIntent() callback will send us data back via a broadcast intent
        Intent serviceIntent = new Intent(this, StepCounterService.class);
        startService(serviceIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityRunning = false;
    }

    @Override
    protected void onStop() {
        // unregister the broadcast receiver
        unregisterReceiver(mStepsReceiver);
        mStepsReceiver = null;

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public class StepsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int stepsSinceReboot = intent.getIntExtra(Constants.EXTRA_STEPS, 0);
            Log.d(TAG, "Received a broadcast intent with step count: " + stepsSinceReboot);
            if (mActivityRunning) {
                mTextSteps.setText(String.valueOf(stepsSinceReboot));
            }
        }

    }

}

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

        // start the semi-persistent background service that interfaces with the step counter sensor API
        Intent serviceIntent = new Intent(this, StepCounterService.class);
        startService(serviceIntent);
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class StepsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int stepsSinceReboot = intent.getIntExtra(Constants.EXTRA_STEPS, 0);
            Log.d(TAG, "Steps since reboot: " + stepsSinceReboot);
            if (mActivityRunning) {
                mTextSteps.setText(String.valueOf(stepsSinceReboot));
            }
        }

    }

}

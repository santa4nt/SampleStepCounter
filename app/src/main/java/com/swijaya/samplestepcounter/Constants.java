package com.swijaya.samplestepcounter;

import android.hardware.SensorManager;

public class Constants {

    public static final String ACTION_STEPS_SINCE_REBOOT = "action.steps_since_reboot";
    public static final String ACTION_FLUSH = "action.flush";

    public static final String EXTRA_STEPS = "extra.steps";

    // for sampling period
    public static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL;
    public static final int MAX_REPORT_LATENCY = 10 * 1000000;  // 10 seconds

}

package com.swijaya.samplestepcounter;

import android.hardware.SensorManager;

public class Constants {

    public static final String ACTION_START_SERVICE_ON_REBOOT = "action.start_service_on_reboot";
    public static final String ACTION_STEP_EVENT_RECEIVED = "action.step_event_received";
    public static final String ACTION_FLUSH = "action.flush";
    public static final String ACTION_RESET = "action.reset";

    public static final String EXTRA_STEP_EVENT = "extra.step_event";

    // for sampling period
    public static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL;
    public static final int MAX_REPORT_LATENCY = 10 * 1000000;  // 10 seconds

}

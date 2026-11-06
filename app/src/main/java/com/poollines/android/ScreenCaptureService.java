package com.poollines.android;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public class ScreenCaptureService extends Service {
    public static final String ACTION_DETECTION = "com.poollines.DETECTION";
    public static final String EXTRA_BALL_FOUND = "ball_found";
    public static final String EXTRA_BALL_X = "ball_x";
    public static final String EXTRA_BALL_Y = "ball_y";
    public static final String EXTRA_AIM_FOUND = "aim_found";
    public static final String EXTRA_AIM_X = "aim_x";
    public static final String EXTRA_AIM_Y = "aim_y";
    public static final String EXTRA_TABLE_DETECTED = "table_detected";
    public static final String EXTRA_TABLE_LEFT = "left";
    public static final String EXTRA_TABLE_TOP = "top";
    public static final String EXTRA_TABLE_RIGHT = "right";
    public static final String EXTRA_TABLE_BOTTOM = "bottom";
    public static final String EXTRA_SCREEN_WIDTH = "sw";
    public static final String EXTRA_SCREEN_HEIGHT = "sh";
    @Override public IBinder onBind(Intent i){ return null; }
}

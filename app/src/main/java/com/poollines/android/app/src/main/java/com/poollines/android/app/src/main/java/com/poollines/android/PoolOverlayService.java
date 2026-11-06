package com.poollines.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class PoolOverlayService extends Service {
    private WindowManager wm;
    private WindowManager.LayoutParams overlayParams, panelParams;
    private TrajectoryOverlay overlay;
    private LinearLayout panel;
    private TextView statusText, restoreButton;
    private Button adjustButton, toggleButton;
    private boolean drawingEnabled = true, adjustMode = false, manualFrame = false, panelVisible = true, receiverRegistered = false;
    private boolean ballFound = false, aimFound = false, tableDetected = false;
    private float cueX, cueY, aimX, aimY;
    private int tableLeft, tableTop, tableRight, tableBottom, screenWidth, screenHeight;
    private final ArrayList<BallData> balls = new ArrayList<>();
    private static final int NONE=0, TL=1, TR=2, BL=3, BR=4;
    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int c,int r){ GradientDrawable d=new GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(r)); return d; }
    private static class BallData{ float x,y,radius; int color; boolean white; BallData(float x,float y,float r,int c,boolean w){this.x=x;this.y=y;radius=r;color=c;white=w;} }

    private final BroadcastReceiver receiver = new BroadcastReceiver(){
        @Override public void onReceive(Context c, Intent intent){
            if(intent==null) return;
            ballFound=intent.getBooleanExtra(ScreenCaptureService.EXTRA_BALL_FOUND,false);
            cueX=intent.getFloatExtra(ScreenCaptureService.EXTRA_BALL_X,0);
            cueY=intent.getFloatExtra(ScreenCaptureService.EXTRA_BALL_Y,0);
            aimFound=intent.getBooleanExtra(ScreenCaptureService.EXTRA_AIM_FOUND,false);
            aimX=intent.getFloatExtra(ScreenCaptureService.EXTRA_AIM_X,0);
            aimY=intent.getFloatExtra(ScreenCaptureService.EXTRA_AIM_Y,0);
            tableDetected=intent.getBooleanExtra(ScreenCaptureService.EXTRA_TABLE_DETECTED,false);
            int nw=intent.getIntExtra(ScreenCaptureService.EXTRA_SCREEN_WIDTH,0);
            int nh=intent.getIntExtra(ScreenCaptureService.EXTRA_SCREEN_HEIGHT,0);
            if(nw>0) screenWidth=nw; if(nh>0) screenHeight=nh;
            if(!manualFrame && tableDetected){
                tableLeft=intent.getIntExtra(ScreenCaptureService.EXTRA_TABLE_LEFT,0);
                tableTop=intent.getIntExtra(ScreenCaptureService.EXTRA_TABLE_TOP,0);
                tableRight=intent.getIntExtra(ScreenCaptureService.EXTRA_TABLE_RIGHT,0);
                tableBottom=intent.getIntExtra(ScreenCaptureService.EXTRA_TABLE_BOTTOM,0);
            }
            if(overlay!=null) overlay.invalidate();
            updateStatus();
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel("pool","Pool Lines",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            Notification n=new Notification.Builder(this,"pool").setContentTitle("Pool Lines Activo").setSmallIcon(android.R.drawable.btn_star).build();
            startForeground(1,n);
        }
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        screenWidth=m.widthPixels; screenHeight=m.heightPixels;
        loadSavedCalibration(); createOverlay(); createPanel(); registerReceiverSafe();
    }
    @Override public void onDestroy(){
        super.onDestroy();
        if(receiverRegistered){ try{unregisterReceiver(receiver);}catch(Exception e){} receiverRegistered=false; }
        try{if(overlay!=null) wm.removeView(overlay);}catch(Exception e){}
        try{if(panel!=null) wm.removeView(panel);}catch(Exception e){}
        try{if(restoreButton!=null) wm.removeView(restoreButton);}catch(Exception e){}
    }
    @Override public IBinder onBind(Intent i){ return null; }
    private void loadSavedCalibration(){
        SharedPreferences prefs=getSharedPreferences(PoolDetector.PREFS_NAME,MODE_PRIVATE);
        manualFrame=prefs.getBoolean(PoolDetector.KEY_ENABLED,false);
        if(manualFrame){
            int ow=prefs.getInt(PoolDetector.KEY_WIDTH,screenWidth);
            int oh=prefs.getInt(PoolDetector.KEY_HEIGHT,screenHeight);
            if(ow<=0||oh<=0){ manualFrame=false; setDefaultFrame(); return; }
            float sx=(float)screenWidth/ow, sy=(float)screenHeight/oh

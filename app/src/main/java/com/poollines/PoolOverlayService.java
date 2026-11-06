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
    private static final int NONE=0, TL=1, TR=2, BL=3, BR=4;
    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int c,int r){ GradientDrawable d=new GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(r)); return d; }

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
        if(receiverRegistered){ try{unregisterReceiver(receiver);}catch(Exception e){} }
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
            float sx=(float)screenWidth/ow, sy=(float)screenHeight/oh;
            tableLeft=Math.round(prefs.getInt(PoolDetector.KEY_LEFT,0)*sx);
            tableTop=Math.round(prefs.getInt(PoolDetector.KEY_TOP,0)*sy);
            tableRight=Math.round(prefs.getInt(PoolDetector.KEY_RIGHT,screenWidth)*sx);
            tableBottom=Math.round(prefs.getInt(PoolDetector.KEY_BOTTOM,screenHeight)*sy);
        } else setDefaultFrame();
    }
    private void setDefaultFrame(){ tableLeft=screenWidth/10; tableRight=screenWidth*9/10; tableTop=screenHeight/4; tableBottom=screenHeight*3/4; }
    private void createOverlay(){
        overlay=new TrajectoryOverlay(this);
        overlayParams=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        overlayParams.gravity=Gravity.TOP|Gravity.START;
        wm.addView(overlay,overlayParams);
    }
    private void registerReceiverSafe(){
        IntentFilter f=new IntentFilter(ScreenCaptureService.ACTION_DETECTION);
        try{ if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f); receiverRegistered=true; }catch(Exception e){}
    }
    private void setAdjustMode(boolean en){
        adjustMode=en;
        if(overlayParams!=null&&overlay!=null){
            overlayParams.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            if(!en) overlayParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try{ wm.updateViewLayout(overlay,overlayParams);}catch(Exception e){}
        }
        if(adjustButton!=null){ adjustButton.setText(en?"GUARDAR MESA":"CALIBRAR MESA"); adjustButton.setBackground(bg(en?Color.rgb(25,145,75):Color.rgb(35,105,170),9)); }
        if(overlay!=null) overlay.invalidate(); updateStatus();
    }
    private void startCalibration(){ if(!manualFrame&&!tableDetected) setDefaultFrame(); manualFrame=true; setAdjustMode(true); }
    private void saveCalibration(){
        if(tableRight-tableLeft<dp(100)||tableBottom-tableTop<dp(100)) return;
        getSharedPreferences(PoolDetector.PREFS_NAME,MODE_PRIVATE).edit().putBoolean(PoolDetector.KEY_ENABLED,true).putInt(PoolDetector.KEY_LEFT,tableLeft).putInt(PoolDetector.KEY_TOP,tableTop).putInt(PoolDetector.KEY_RIGHT,tableRight).putInt(PoolDetector.KEY_BOTTOM,tableBottom).putInt(PoolDetector.KEY_WIDTH,screenWidth).putInt(PoolDetector.KEY_HEIGHT,screenHeight).apply();
        manualFrame=true; setAdjustMode(false);
    }
    private void createPanel(){
        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable bgPanel=new GradientDrawable(); bgPanel.setColor(Color.argb(240,18,20,23)); bgPanel.setCornerRadius(dp(16)); panel.setBackground(bgPanel);
        TextView title=new TextView(this); title.setText("8 BALL LINES"); title.setTextColor(Color.WHITE); title.setTextSize(13); title.setTypeface(null,Typeface.BOLD); title.setGravity(Gravity.CENTER); panel.addView(title);
        statusText=new TextView(this); statusText.setTextColor(Color.argb(180,255,255,255)); statusText.setTextSize(10); statusText.setGravity(Gravity.CENTER); statusText.setPadding(0,dp(8),0,dp(10)); panel.addView(statusText);
        toggleButton=new Button(this); toggleButton.setAllCaps(false); toggleButton.setTextColor(Color.WHITE); toggleButton.setTextSize(12); toggleButton.setTypeface(null,Typeface.BOLD); updateToggleUI();
        toggleButton.setOnClickListener(v->{ drawingEnabled=!drawingEnabled; updateToggleUI(); overlay.invalidate(); });
        panel.addView(toggleButton,new LinearLayout.LayoutParams(-1,dp(46)));
        adjustButton=new Button(this); adjustButton.setText("CALIBRAR MESA"); adjustButton.setTextSize(10); adjustButton.setAllCaps(false); adjustButton.setTextColor(Color.WHITE); adjustButton.setBackground(bg(Color.rgb(35,105,170),10));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(38)); ap.topMargin=dp(8); panel.addView(adjustButton,ap);
        adjustButton.setOnClickListener(v->{ if(adjustMode) saveCalibration(); else startCalibration(); });
        panelParams=new WindowManager.LayoutParams(dp(180),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        panelParams.gravity=Gravity.TOP|Gravity.LEFT; panelParams.x=dp(8); panelParams.y=dp(100); wm.addView(panel,panelParams); updateStatus();
    }
    private void updateToggleUI(){ if(toggleButton==null) return; toggleButton.setText(drawingEnabled?"✓ LONG LINE ON":"○ LONG LINE OFF"); toggleButton.setBackground(bg(drawingEnabled?Color.rgb(22,163,74):Color.rgb(100,100,100),10)); }
    private void updateStatus(){ if(statusText==null) return; if(adjustMode) statusText.setText("Mueve las esquinas\nPulsa GUARDAR"); else if(!ballFound) statusText.setText("Buscando blanca..."); else statusText.setText("Listo - Linea larga activa"); }

    private class TrajectoryOverlay extends View {
        private final Paint linePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circlePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selectedCorner=NONE; private float downX,downY; private int startLeft,startTop,startRight,startBottom;
        TrajectoryOverlay(Context c){ super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null); linePaint.setColor(Color.WHITE); linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeWidth(dp(2.5f)); circlePaint.setColor(Color.WHITE); circlePaint.setStyle(Paint.Style.STROKE); circlePaint.setStrokeWidth(dp(2.5f)); }
        private float scaleX(){ return getWidth()/(float)Math.max(1,screenWidth); }
        private float scaleY(){ return getHeight()/(float)Math.max(1,screenHeight); }
        private void drawHandle(Canvas c,float x,float y){ linePaint.setStyle(Paint.Style.FILL); linePaint.setColor(Color.YELLOW); c.drawCircle(x,y,dp(10),linePaint); linePaint.setStyle(Paint.Style.STROKE); linePaint.setColor(Color.BLACK); linePaint.setStrokeWidth(dp(2)); c.drawCircle(x,y,dp(10),linePaint); linePaint.setStyle(Paint.Style.STROKE); linePaint.setColor(Color.WHITE); linePaint.setStrokeWidth(dp(2.5f)); }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!adjustMode) return false; float x=e.getX()/scaleX(); float y=e.getY()/scaleY(); float h=dp(40);
            switch(e.getAction()){ case MotionEvent.ACTION_DOWN: downX=x; downY=y; startLeft=tableLeft; startTop=tableTop; startRight=tableRight; startBottom=tableBottom; if(Math.abs(x-tableLeft)<h&&Math.abs(y-tableTop)<h) selectedCorner=TL; else if(Math.abs(x-tableRight)<h&&Math.abs(y-tableTop)<h) selectedCorner=TR; else if(Math.abs(x-tableLeft)<h&&Math.abs(y-tableBottom)<h) selectedCorner=BL; else if(Math.abs(x-tableRight)<h&&Math.abs(y-tableBottom)<h) selectedCorner=BR; else selectedCorner=NONE; return true; case MotionEvent.ACTION_MOVE: if(selectedCorner==NONE) return true; float dx=x-downX, dy=y-downY; if(selectedCorner==TL){ tableLeft=(int)(startLeft+dx); tableTop=(int)(startTop+dy);} if(selectedCorner==TR){ tableRight=(int)(startRight+dx); tableTop=(int)(startTop+dy);} if(selectedCorner==BL){ tableLeft=(int)(startLeft+dx); tableBottom=(int)(startBottom+dy);} if(selectedCorner==BR){ tableRight=(int)(startRight+dx); tableBottom=(int)(startBottom+dy);} invalidate(); return true; case MotionEvent.ACTION_UP: selectedCorner=NONE; return true; } return false;
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float sx=scaleX(), sy=scaleY(); canvas.save(); canvas.scale(sx,sy);
            if(adjustMode){ Paint p=new Paint(); p.setColor(0x3328A8FF); p.setStyle(Paint.Style.FILL); canvas.drawRect(tableLeft,tableTop,tableRight,tableBottom,p); p.setColor(Color.YELLOW); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); canvas.drawRect(tableLeft,tableTop,tableRight,tableBottom,p); drawHandle(canvas,tableLeft,tableTop); drawHandle(canvas,tableRight,tableTop); drawHandle(canvas,tableLeft,tableBottom); drawHandle(canvas,tableRight,tableBottom); canvas.restore(); return; }
            if(!drawingEnabled||!ballFound||!aimFound){ canvas.restore(); return; }
            float dx=aimX-cueX, dy=aimY-cueY; float len=(float)Math.hypot(dx,dy); if(len<10){ canvas.restore(); return; } dx/=len; dy/=len;
            canvas.drawLine(cueX,cueY,cueX+dx*4000,cueY+dy*4000,linePaint);
            canvas.drawCircle(cueX,cueY,dp(9),circlePaint);
            canvas.drawCircle(cueX+dx*120,cueY+dy*120,dp(7),circlePaint);
            canvas.restore();
        }
    }
                                                                                                       }

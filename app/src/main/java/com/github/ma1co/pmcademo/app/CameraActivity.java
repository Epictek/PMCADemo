package com.github.ma1co.pmcademo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.sony.scalar.hardware.CameraEx;

import java.io.IOException;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class CameraActivity extends BaseActivity implements SurfaceHolder.Callback {
    private static class MyHttpServer extends NanoHTTPD {
        public static final int PORT = 8080;
        private CameraEx mCamera = null;

        public MyHttpServer() {
            super(PORT);
        }

        @Override
        public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
            if(session.getUri().endsWith("/shoot")) {
                if(mCamera!=null)
                    mCamera.getNormalCamera().takePicture(null,null,null);
            }
            return new NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, MIME_HTML,
                    "<body><button type='button' onclick='window.location.href=\"/shoot\"'>Shoot</button></body>");
        }

        public void setCamera(CameraEx camera) {
            this.mCamera = camera;
        }
    }
    private MyHttpServer myHttpServer;
    private SurfaceHolder surfaceHolder;
    private CameraEx camera;
    private ImageView mImagePictureWipe;
    private WifiManager wifiManager;
    private TextView textView;

    private BroadcastReceiver wifiStateReceiver;
    private BroadcastReceiver supplicantStateReceiver;
    private BroadcastReceiver networkStateReceiver;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);


        textView = (TextView) findViewById(R.id.logView);

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mImagePictureWipe = (ImageView) findViewById(R.id.image_picture_wipe);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                wifiStateChanged(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
            }
        };

        supplicantStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                networkStateChanged(WifiInfo.getDetailedStateOf((SupplicantState) intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE)));
            }
        };

        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                networkStateChanged(((NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)).getDetailedState());
            }
        };
        wifiManager.setWifiEnabled(true);


        myHttpServer = new MyHttpServer();

    }

    protected boolean onEnterKeyUp() {
        wipePreviewPicture();
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera = CameraEx.open(0, null);
        surfaceHolder.addCallback(this);
        myHttpServer.setCamera(camera);

        registerReceiver(wifiStateReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
        registerReceiver(supplicantStateReceiver, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
        registerReceiver(networkStateReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        wifiManager.setWifiEnabled(true);
        try {
            myHttpServer.start();
        } catch (IOException e) {}
        setAutoPowerOffMode(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera.release();
        camera = null;
        surfaceHolder.removeCallback(this);
        unregisterReceiver(wifiStateReceiver);
        unregisterReceiver(supplicantStateReceiver);
        unregisterReceiver(networkStateReceiver);
        wifiManager.setWifiEnabled(false);
        myHttpServer.stop();
        setAutoPowerOffMode(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.getNormalCamera().setPreviewDisplay(holder);
            camera.getNormalCamera().startPreview();
        } catch (IOException e) {}
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected boolean onFocusKeyDown() {
        camera.getNormalCamera().autoFocus(null);
        return true;
    }

    @Override
    protected boolean onFocusKeyUp() {
        camera.getNormalCamera().cancelAutoFocus();
        return true;
    }

    @Override
    protected boolean onShutterKeyDown() {
        camera.getNormalCamera().takePicture(null, null, new Camera.PictureCallback(){

            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1; // compressed (1 not compressed)
                final Bitmap rawBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);


                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mImagePictureWipe.setVisibility(View.VISIBLE);
                        mImagePictureWipe.setImageBitmap(rawBmp);
                    }
                });
            }
        });
        return true;
    }

    @Override
    protected boolean onShutterKeyUp() {
        camera.cancelTakePicture();
        return true;
    }

    @Override
    protected void setColorDepth(boolean highQuality) {
        super.setColorDepth(false);
    }

    private void wipePreviewPicture(){
        mImagePictureWipe.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiManager.setWifiEnabled(false);
    }

    protected void wifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                log("Enabling wifi");
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                log("Wifi enabled");
                break;
        }
    }

    protected void networkStateChanged(NetworkInfo.DetailedState state) {
        String ssid = wifiManager.getConnectionInfo().getSSID();
        switch (state) {
            case CONNECTING:
                if (ssid != null)
                    log(ssid + ": Connecting");
                break;
            case AUTHENTICATING:
                log(ssid + ": Authenticating");
                break;
            case OBTAINING_IPADDR:
                log(ssid + ": Obtaining IP");
                break;
            case CONNECTED:
                wifiConnected();
                break;
            case DISCONNECTED:
                log("Disconnected");
                break;
            case FAILED:
                log("Connection failed");
                break;
        }
    }

    protected void wifiConnected() {
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = info.getSSID();
        String ip = Formatter.formatIpAddress(info.getIpAddress());
        log(ssid + ": Connected. Server URL: http://" + ip + ":" + HttpServer.PORT + "/");
    }

    protected void log(String msg) {
        textView.setText(msg);
    }
}

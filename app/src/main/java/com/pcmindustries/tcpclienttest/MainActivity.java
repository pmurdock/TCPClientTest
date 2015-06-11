package com.pcmindustries.tcpclienttest;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.client.HttpClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    private static final String DEBUG_TAG = "PCM";
    // TimerTask handler
    final Handler handler = new Handler();
    private ImageView imgView;
    private TextView txtLog;
    private TextView txtStatus;
    private boolean bConnected = false;
    private Timer timer;
    private TimerTask timerTask;

    public static String getCurrentTimeStamp() {
        //SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");//dd/MM/yyyy
        SimpleDateFormat sdfDate = new SimpleDateFormat("HH:mm:ss.SSS");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//        StrictMode.setThreadPolicy(policy);

        imgView = (ImageView) findViewById(R.id.imageView);
        txtLog = (TextView) findViewById(R.id.txtLog);
        txtStatus = (TextView) findViewById(R.id.txtStatus);

        imgView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/compass.jpg"), "image/jpeg");
                startActivity(intent);
            }
        });

        imgView.setOnTouchListener(
                new View.OnTouchListener() {

                    private Rect rect;

                    @Override
                    public boolean onTouch(View v, MotionEvent motionEvent) {


                        switch (motionEvent.getAction()) {
                            case MotionEvent.ACTION_DOWN: {
                                //overlay is black with transparency of 0x77 (119)
                                imgView.setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP);
                                rect = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
                                break;
                            }
                            case MotionEvent.ACTION_UP: {
                                imgView.clearColorFilter();
                                // if we release while inside the control then make a click!
                                if (rect != null && rect.contains(v.getLeft() + (int) motionEvent.getX(),
                                        v.getTop() + (int) motionEvent.getY())) {
                                    if (imgView.getDrawable() != null) {
                                        v.performClick();
                                    }
                                }

                                break;
                            }
                            case MotionEvent.ACTION_MOVE: {
                                if (rect != null && !rect.contains(v.getLeft() + (int) motionEvent.getX(),
                                        v.getTop() + (int) motionEvent.getY())) {
                                    imgView.clearColorFilter();
                                } else {
                                    imgView.setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP);
                                }
                                break;
                            }
                            case MotionEvent.ACTION_CANCEL: {
                                //clear the overlay
                                imgView.clearColorFilter();
                                break;
                            }
                        }

                        return true;
                    }
                }

        );

        logTxt("Start timer from onCreate");
        startTimer();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
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

    public void btnDownload(View view) {
        imgView.setImageDrawable(null);
        AsyncDownload runner = new AsyncDownload();
        runner.execute();

    }

    public void btnClearLog(View view) {
        txtLog.setText("");
        imgView.setImageDrawable(null);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    public void btnConnect(View view) {
        // Check if WiFi is connected to PCM-CAMERA-MAC
        // if yes - then check ip address whether functional (responds)
        // if no - bring up Wifi dialog box to connect to device then check ip address

        // check if connected to correct WiFi SSID
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean isWiFiConn = networkInfo.isConnected();


        if (networkInfo != null && networkInfo.isConnected()) {
            // Log.d(DEBUG_TAG, "WiFi is connected : " + String.valueOf(isWiFiConn));
            // We know WiFi is connected - but to the PCM-CAMERA ssid?
            // Now print out to DEBUG which WiFi we are connected to (ssid)
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifiManager.getConnectionInfo();

            if (info.getSSID().contains("pcm")) {
                Toast.makeText(getApplicationContext(), "Connected to PCM-CAMERA device",
                        Toast.LENGTH_LONG).show();
                // Change txtStatus to reflect that we are connected
                bConnected = true;
                txtStatus.setText("Connected");
                Log.d(DEBUG_TAG, "WiFi ssid is : " + info.getSSID());

            } else {
                // TODO
                // Open up the WiFi dialog box to allow them to connect to the PCM-CAMERA ssid
                Log.d(DEBUG_TAG, "Connected to wrong SSID : " + info.getSSID());

                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }

        } else {
            Log.d(DEBUG_TAG, "WiFi is not connected...");

            // If not connected to network - then help them bring up WiFi selection to
            // choose the Inventek Module - ssid: PCM-CAMERA
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }

    }

    public void logTxt(String string) {
        String temp = txtLog.getText().toString();
        temp = getCurrentTimeStamp() + " >>> " + string + "\n" + temp;
        txtLog.setText(temp);
    }

    private void startTimer() {
        // set a new timer
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 5000, 10000);

    }

    private void stopTimerTask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        logTxt("Timer Ran");
                        if (bConnected == true) {
                            txtStatus.setText("Connected");
                        } else {
                            txtStatus.setText("Not Connected");
                        }
                    }
                });
            }
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimerTask();
        logTxt("Timer Paused");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timer == null) {
            startTimer();
            logTxt("Timer was null in onresume");
        }
        logTxt("Timer Resumed");
    }

    private class AsyncDownload extends AsyncTask<Void, String, Boolean> {

        Bitmap bmp;

        @Override
        protected Boolean doInBackground(Void... params) {

            // Use an http GET request to download the binary file from the system
            URL url = null;
            HttpURLConnection urlConnection = null;
            boolean bResult = false;

            try {
                url = new URL("http://192.168.0.110:8080/");
                publishProgress("Connecting . . .");

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(2000);

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                publishProgress("SUCCESS");

                Log.d("PCM", "Finished reading in buffer...");
                publishProgress("http request completed");


                int nRead;
                int count = 0;
                int[] data = new int[480000];
                int width = 800;
                int height = 600;

                //IntBuffer intBuf = ByteBuffer.wrap(datab).asIntBuffer();
                while ((nRead = in.read()) != -1) {
                    data[count] = nRead;
                    count++;
                }
                publishProgress("buffer read into int array");

                // Now write out to SD Card

                // Create Bitmap from raw data that we received
                bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.argb(255, 255, 0, 0));
                //bmp.setDensity(Bitmap.DENSITY_NONE);

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        data[y * width + x] = ((0xff000000) | ((data[y * width + x]) << 16) | ((data[y * width + x]) << 8) | ((data[y * width + x])));
                        //bmp.setPixel(x, y, (int)(0xff000000 | ((data[y * 600 + x])<<16) | ((data[y * 600 + x])<<8) | ((data[y * 600 + x]))));
                        //Log.d("PCM","x: " + String.valueOf(x) + "y: " + String.valueOf(y));
                    }

                }

                bmp.setPixels(data, 0, 800, 0, 0, 800, 600);
//            bmp.eraseColor(Color.argb(255, 255, 0, 0));
                publishProgress("bitmap set");


                // write to SD CARD
                boolean mExternalStorageAvailable = false;
                boolean mExternalStorageWritable = false;

                String state = Environment.getExternalStorageState();
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File file = new File(path, "compass.jpg");

                Log.d("PCM", "Starting to Write to SD Card");
                publishProgress("start writing to SD card");

                path.mkdirs();

                FileOutputStream fos = new FileOutputStream(file, false);
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);

                fos.flush();
                fos.close();
                publishProgress("Wrote to SD card");
                bResult = true;


            } catch (SocketTimeoutException e) {
                publishProgress("SocketTimeoutException");
                bResult = false;
            } catch (IOException e) {
                e.printStackTrace();
                bResult = false;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            publishProgress("EXIT background task");

            return bResult;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String temp = txtLog.getText().toString();
            temp = getCurrentTimeStamp() + " >>> " + values[0] + "\n" + temp;
            txtLog.setText(temp);
        }

        @Override
        protected void onPostExecute(Boolean bSuccess) {
            if (bSuccess) {
                imgView.setImageBitmap(bmp);
            } else {
                imgView.setImageDrawable(null);
            }
        }
    }
}

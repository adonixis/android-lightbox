package ru.adonixis.lightbox;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorChangedListener;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import ru.adonixis.lightbox.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Lightbox";
    //private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mmSocket;
    private BluetoothDevice mmDevice;
    private OutputStream mmOutputStream;
    private OutputStreamWriter streamWriter;
    private InputStream mmInputStream;
    private InputStreamReader streamReader;
    private Thread workerThread;
    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;
    //private int alpha, red, green, blue;
    private int mode, currentAlpha, currentRed, currentGreen, currentBlue;
    private int countSegments;
    private int[] currentPixelsColorsArray;
    private int[] pixelsColorsArray;
    private int[] startPixelArray;
    private int[] countPixelsArray;
    private int[] alphaArray;
    private int[] redArray;
    private int[] greenArray;
    private int[] blueArray;
    private int model, startCorner, allPixelsCount, leftPixelsCount, topPixelsCount, rightPixelsCount, bottomPixelsCount;
    private boolean clockwise;
    private ActivityMainBinding mActivityMainBinding;
    private boolean isLongClick;
    private long lastTime;
    private boolean isSegment = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        setSupportActionBar(mActivityMainBinding.toolbar);

        mActivityMainBinding.imageLightbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLongClick = false;
                System.arraycopy(currentPixelsColorsArray, 0, pixelsColorsArray, 0, currentPixelsColorsArray.length);
/*                if (currentRed == 0 && currentGreen == 0 && currentBlue == 0) {
                    currentRed = 255;
                    currentGreen = 255;
                    currentBlue = 255;
                }*/
                ColorPickerDialogBuilder
                        .with(MainActivity.this)
                        .setTitle("Choose color")
                        //.initialColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue))
                        .initialColor(currentPixelsColorsArray[0])
                        .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                        .density(16)
                        .alphaSliderOnly()
                        .setPickerCount(5)
                        .setOnColorSelectedListener(new OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int selectedColor) {

                            }
                        })
                        .setOnColorChangedListener(new OnColorChangedListener() {
                            @Override
                            public void onColorChanged(int selectedColor) {
                                if ((System.currentTimeMillis() - lastTime) > 200) {
                                    lastTime = System.currentTimeMillis();

                                    int alpha = Color.alpha(selectedColor);
                                    int red = Color.red(selectedColor);
                                    int green = Color.green(selectedColor);
                                    int blue = Color.blue(selectedColor);
                                    if (alpha < 4) {
                                        alpha = 0;
                                    }

                                    isSegment = false;

                                    for (int i = 0; i < allPixelsCount; i++) {
                                        View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                                        if (led.getScaleX() == 0.8f && led.getScaleY() == 0.8f) {
                                            pixelsColorsArray[i] = Color.argb(alpha, red, green, blue);
                                            isSegment = true;
                                        }
                                    }

                                    final String command;
                                    if (isSegment) {
                                        String tempCommand = "";
                                        int countSegments = 1;
                                        int startPixel = 0;
                                        int countPixels = 1;
                                        int prevColor = pixelsColorsArray[0];
                                        for (int i = 1; i < allPixelsCount; i++) {
                                            if (prevColor == pixelsColorsArray[i]) {
                                                countPixels++;
                                            } else {
                                                tempCommand += startPixel + " " + countPixels + " " + Color.alpha(prevColor) + " " + Color.red(prevColor) + " " + Color.green(prevColor) + " " + Color.blue(prevColor) + " ";
                                                prevColor = pixelsColorsArray[i];
                                                countSegments++;
                                                startPixel = i;
                                                countPixels = 1;
                                            }
                                        }
                                        tempCommand += startPixel + " " + countPixels + " " + Color.alpha(prevColor) + " " + Color.red(prevColor) + " " + Color.green(prevColor) + " " + Color.blue(prevColor);

                                        command = "1 " + countSegments + " " + tempCommand + '\n';
                                    } else {
                                        Arrays.fill(pixelsColorsArray, Color.argb(alpha, red, green, blue));
                                        command = "1 1 0 " + allPixelsCount + " " +  alpha + " " + red + " " + green + " " + blue + '\n';
                                    }

                                    //float ratio = alpha/255f;
                                    //final String command = "0 " + (int) (red * ratio) + ' ' + (int) (green * ratio) + ' ' + (int) (blue * ratio) + '\n';
                                    //final String command = "0 " + alpha + ' ' + red + ' ' + green + ' ' + blue + '\n';

                                    Log.d(TAG, "onColorChanged: " + command);

                                    try {
                                        //mmOutputStream.write(command.getBytes());
                                        //mmOutputStream.flush();
                                        streamWriter.write(command);
                                        streamWriter.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        })
                        .setPositiveButton("Ok", new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                System.arraycopy(pixelsColorsArray, 0, currentPixelsColorsArray, 0, pixelsColorsArray.length);
                                if (isSegment) {
                                    mActivityMainBinding.imageLightbox.setBackgroundColor(Color.TRANSPARENT);
                                } else {
                                    mActivityMainBinding.imageLightbox.setBackgroundColor(currentPixelsColorsArray[0]);
                                }

                                for (int i = 0; i < allPixelsCount; i++) {
                                    View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                                    led.setScaleX(1.0f);
                                    led.setScaleY(1.0f);
                                    GradientDrawable gradientDrawable = (GradientDrawable) led.getBackground();
                                    gradientDrawable.mutate();
                                    gradientDrawable.setColor(currentPixelsColorsArray[i]);
                                }
/*
                                int alpha = Color.alpha(selectedColor);
                                int red = Color.red(selectedColor);
                                int green = Color.green(selectedColor);
                                int blue = Color.blue(selectedColor);
                                currentAlpha = alpha;
                                currentRed = red;
                                currentGreen = green;
                                currentBlue = blue;
                                mActivityMainBinding.imageLightbox.setBackgroundColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));
*/

/*
                                for (int i = 0; i < allPixelsCount; i++) {
                                    View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                                    GradientDrawable gradientDrawable = (GradientDrawable) led.getBackground();
                                    gradientDrawable.mutate();
                                    gradientDrawable.setColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));
                                }
                                */
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (isSegment) {
                                    mActivityMainBinding.imageLightbox.setBackgroundColor(Color.TRANSPARENT);
                                } else {
                                    mActivityMainBinding.imageLightbox.setBackgroundColor(currentPixelsColorsArray[0]);
                                }

                                for (int i = 0; i < allPixelsCount; i++) {
                                    View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                                    led.setScaleX(1.0f);
                                    led.setScaleY(1.0f);
                                    GradientDrawable gradientDrawable = (GradientDrawable) led.getBackground();
                                    gradientDrawable.mutate();
                                    gradientDrawable.setColor(currentPixelsColorsArray[i]);
                                }

                                final String command;
                                //if (isSegment) {
                                    String tempCommand = "";
                                    int countSegments = 1;
                                    int startPixel = 0;
                                    int countPixels = 1;
                                    int prevColor = currentPixelsColorsArray[0];
                                    for (int i = 1; i < allPixelsCount; i++) {
                                        if (prevColor == currentPixelsColorsArray[i]) {
                                            countPixels++;
                                        } else {
                                            tempCommand += startPixel + " " + countPixels + " " + Color.alpha(prevColor) + " " + Color.red(prevColor) + " " + Color.green(prevColor) + " " + Color.blue(prevColor) + " ";
                                            prevColor = currentPixelsColorsArray[i];
                                            countSegments++;
                                            startPixel = i;
                                            countPixels = 1;
                                        }
                                    }
                                    tempCommand += startPixel + " " + countPixels + " " + Color.alpha(prevColor) + " " + Color.red(prevColor) + " " + Color.green(prevColor) + " " + Color.blue(prevColor);

                                    command = "1 " + countSegments + " " + tempCommand + '\n';
                                //} else {
                                //    command = "1 1 0 " + allPixelsCount + " " +  Color.alpha(currentPixelsColorsArray[0]) + " " + Color.red(currentPixelsColorsArray[0]) + " " + Color.green(currentPixelsColorsArray[0]) + " " + Color.blue(currentPixelsColorsArray[0]) + '\n';
                                //}

                                //final String command = "0 " + currentAlpha + ' ' + currentRed + ' ' + currentGreen + ' ' + currentBlue + '\n';
                                //final String command = "1 1 0 " + allPixelsCount + ' ' + currentAlpha + ' ' + currentRed + ' ' + currentGreen + ' ' + currentBlue + '\n';
                                try {
                                    //mmOutputStream.write(command.getBytes());
                                    //mmOutputStream.flush();
                                    streamWriter.write(command);
                                    streamWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        })
                        .build()
                        .show();
            }
        });

        findBT();
        try {
            openBT();
            streamWriter.write("77\n");
            streamWriter.flush();
        } catch (IOException ex) {
            Log.e(TAG, "openBT: ", ex);
            if (BuildConfig.DEBUG) {
                Toast.makeText(MainActivity.this, "Check Bluetooth device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            closeBT();
        } catch (IOException ex) {
            Log.e(TAG, "onDestroy: ", ex);
        }
    }

    private void findBT() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "findBT: No bluetooth adapter available");
            if (BuildConfig.DEBUG) {
                Toast.makeText(MainActivity.this, "findBT: No bluetooth adapter available", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent intentEnableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                intentEnableBluetooth.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(intentEnableBluetooth, 0);
            }

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals("Lightbox")) {
                        mmDevice = device;
                        break;
                    }
                }
                if (mmDevice == null) {
                    Log.d(TAG, "findBT: No paired devices");
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(MainActivity.this, "findBT: No paired devices", Toast.LENGTH_SHORT).show();
                    }

                    Intent intentOpenBluetoothSettings = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    intentOpenBluetoothSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivityForResult(intentOpenBluetoothSettings, 1);
                } else {
                    Log.d(TAG, "findBT: Bluetooth Device Found");
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(MainActivity.this, "findBT: Bluetooth Device Found", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Log.d(TAG, "findBT: No paired devices");
                if (BuildConfig.DEBUG) {
                    Toast.makeText(MainActivity.this, "findBT: No paired devices", Toast.LENGTH_SHORT).show();
                }
/*

                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.bluetooth.BluetoothSettings");
                intent.setComponent(cn);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
*/

                Intent intentOpenBluetoothSettings = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                intentOpenBluetoothSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(intentOpenBluetoothSettings, 1);
            }
        }
    }

    private  void openBT() throws IOException {
        //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        if (mmDevice != null) {
            ParcelUuid[] supportedUuids = mmDevice.getUuids();
            UUID uuid = supportedUuids[0].getUuid();
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            //        if (mmSocket == null) {
            //            Method createMethod = mmDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class });
            //            mmSocket = (BluetoothSocket) createMethod.invoke(mmDevice, 1);
            //        }
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            streamWriter = new OutputStreamWriter(mmOutputStream);
            mmInputStream = mmSocket.getInputStream();
            streamReader = new InputStreamReader(mmInputStream);

            beginListenForData();

            Log.d(TAG, "openBT: Bluetooth Opened");
            if (BuildConfig.DEBUG) {
                Toast.makeText(MainActivity.this, "openBT: Bluetooth Opened", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    if (data.startsWith("Info:")) {
                                        String[] strParams = data.substring(6).split("\\s+");
                                        int[] params = new int[strParams.length];
                                        for (int j = 0; j < strParams.length; j++) {
                                            params[j] = Integer.parseInt(strParams[j]);
                                        }
                                        model = params[0];
                                        startCorner = params[1];
                                        clockwise = params[2] == 0;
                                        allPixelsCount = params[3];
                                        leftPixelsCount = params[4];
                                        topPixelsCount = params[5];
                                        rightPixelsCount = params[6];
                                        bottomPixelsCount = params[7];

                                        currentPixelsColorsArray = new int[allPixelsCount];
                                        pixelsColorsArray = new int[allPixelsCount];

                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                switch (model) {
                                                    case 0:
                                                        mActivityMainBinding.imageLightbox.setImageResource(R.drawable.lightbox_0_deers);
                                                        break;
                                                }

                                                drawLEDs();
                                            }
                                        });

                                        mode = params[8];
                                        switch (mode) {
                                            case 0:
                                                currentAlpha = params[9];
                                                currentRed = params[10];
                                                currentGreen = params[11];
                                                currentBlue = params[12];
                                                Arrays.fill(currentPixelsColorsArray, 0, allPixelsCount, Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mActivityMainBinding.imageLightbox.setBackgroundColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));
                                                    }
                                                });
                                                break;
                                            case 1:
                                                countSegments = params[9];
                                                for (int j = 0; j < countSegments; j++) {
//                                                    startPixelArray[j] = params[10 + j*6];
//                                                    countPixelsArray[j] = params[11 + j*6];
//                                                    alphaArray[j] = params[12 + j*6];
//                                                    redArray[j] = params[13 + j*6];
//                                                    greenArray[j] = params[14 + j*6];
//                                                    blueArray[j] = params[15 + j*6];

                                                    final int startPixel = params[10 + j*6];
                                                    final int countPixels = params[11 + j*6];
                                                    final int alpha = params[12 + j*6];
                                                    final int red = params[13 + j*6];
                                                    final int green = params[14 + j*6];
                                                    final int blue = params[15 + j*6];

                                                    Arrays.fill(currentPixelsColorsArray, startPixel, startPixel + countPixels, Color.argb(alpha, red, green, blue));

                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (countSegments == 1 && countPixels == allPixelsCount) {
                                                                currentAlpha = alpha;
                                                                currentRed = red;
                                                                currentGreen = green;
                                                                currentBlue = blue;
                                                                mActivityMainBinding.imageLightbox.setBackgroundColor(Color.argb(alpha, red, green, blue));
                                                            }
                                                            segmentLEDs(startPixel, countPixels, alpha, red, green, blue);
                                                        }
                                                    });
                                                }
                                                break;
                                        }
                                    }
                                    handler.post(new Runnable() {
                                        public void run() {
                                            Log.d(TAG, "listenData: " + data);
                                            if (BuildConfig.DEBUG) {
                                                Toast.makeText(MainActivity.this, "listenData: " + data, Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    /*
    void sendData() throws IOException {
        String msg = mActivityMainBinding.entry.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        mActivityMainBinding.label.setText("Data Sent");
    }
    */

    private void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        Log.d(TAG, "closeBT: Bluetooth Closed");
        if (BuildConfig.DEBUG) {
            Toast.makeText(MainActivity.this, "closeBT: Bluetooth Closed", Toast.LENGTH_SHORT).show();
        }
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
        final String command;
        switch (id) {
            case R.id.action_rainbow:
                command = "3\n";
                try {
                    streamWriter.write(command);
                    streamWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.action_rainbow_cycle:
                command = "2\n";
                try {
                    streamWriter.write(command);
                    streamWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }

/*
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            final String command = "1 12 " +
                    "0 5 255 255 0 0 " +
                    "5 5 255 0 255 0 " +
                    "10 5 255 0 0 255 " +
                    "15 5 255 255 0 0 " +
                    "20 5 255 0 255 0 " +
                    "25 5 255 0 0 255 " +
                    "30 5 255 255 0 0 " +
                    "35 5 255 0 255 0 " +
                    "40 5 255 0 0 255 " +
                    "45 5 255 255 0 0 " +
                    "50 5 255 0 255 0 " +
                    "55 3 255 0 0 255" +
                    '\n';
            try {
                streamWriter.write(command);
                streamWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
*/

        return super.onOptionsItemSelected(item);
    }

    private void drawLEDs() {
        int tempHeight = mActivityMainBinding.relativeLayout.getHeight();
        int leftLEDSize = tempHeight / leftPixelsCount;
        int rightLEDSize = tempHeight / rightPixelsCount;

        int width = mActivityMainBinding.relativeLayout.getWidth();
        int topLEDSize = width / topPixelsCount;
        int bottomLEDSize = width / bottomPixelsCount;

        int minLEDsize = Math.min(Math.min(leftLEDSize, rightLEDSize), Math.min(topLEDSize, bottomLEDSize)) - 20;

        int height = tempHeight - minLEDsize - minLEDsize;

        int leftLEDMargin = (height - leftPixelsCount * minLEDsize)/leftPixelsCount/2;
        int rightLEDMargin = (height - rightPixelsCount * minLEDsize)/rightPixelsCount/2;
        int topLEDMargin = (width - topPixelsCount * minLEDsize)/(topPixelsCount - 1)/2;
        int bottomLEDMargin = (width - bottomPixelsCount * minLEDsize)/(bottomPixelsCount - 1)/2;

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLongClick) {
                    if (v.getScaleX() == 0.8f && v.getScaleY() == 0.8f) {
                        v.setScaleX(1f);
                        v.setScaleY(1f);
                    } else {
                        v.setScaleX(0.8f);
                        v.setScaleY(0.8f);
                    }
                    for (int i = 0; i < allPixelsCount; i++) {
                        View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                        if (led.getScaleX() == 0.8f && led.getScaleY() == 0.8f) {
                            isLongClick = true;
                            break;
                        }
                        isLongClick = false;
                    }
                }
            }
        };

        View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isLongClick) {
                    isLongClick = true;

                    //GradientDrawable gradientDrawable = (GradientDrawable) v.getBackground();
                    //gradientDrawable.mutate();
                    //gradientDrawable.setColor(Color.GRAY);
                    v.setScaleX(0.8f);
                    v.setScaleY(0.8f);
                }
                return true;
            }
        };

//        RelativeLayout.LayoutParams paramsTop = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, minLEDsize);
//        paramsTop.addRule(RelativeLayout.ALIGN_PARENT_TOP);
//        mActivityMainBinding.layoutTop.setLayoutParams(paramsTop);
        for (int i = 0; i < topPixelsCount; i++) {
            final View circleView = new View(MainActivity.this);
            LinearLayout.LayoutParams paramsCircleView = new LinearLayout.LayoutParams(minLEDsize, minLEDsize);
            if (i == 0) {
                paramsCircleView.setMargins(0, 0, topLEDMargin, 0);
            } else if (i == topPixelsCount - 1) {
                paramsCircleView.setMargins(topLEDMargin, 0, 0, 0);
            } else {
                paramsCircleView.setMargins(topLEDMargin, 0, topLEDMargin, 0);
            }
            circleView.setLayoutParams(paramsCircleView);
            circleView.setBackgroundResource(R.drawable.round_view);
            switch (startCorner) {
                case 0:
                    if (clockwise) {
                        circleView.setTag("LED" + (leftPixelsCount + i));
                    } else {
                        circleView.setTag("LED" + (bottomPixelsCount + rightPixelsCount + topPixelsCount - 1 - i));
                    }
                    break;
                case 1:
                    if (clockwise) {
                        circleView.setTag("LED" + i);
                    } else {
                        circleView.setTag("LED" + (allPixelsCount - 1 - i));
                    }
                    break;
                case 2:
                    if (clockwise) {
                        circleView.setTag("LED" + (rightPixelsCount + bottomPixelsCount + leftPixelsCount + i));
                    } else {
                        circleView.setTag("LED" + (topPixelsCount - 1 - i));
                    }
                    break;
                case 3:
                    if (clockwise) {
                        circleView.setTag("LED" + (bottomPixelsCount + leftPixelsCount + i));
                    } else {
                        circleView.setTag("LED" + (rightPixelsCount + topPixelsCount - 1 - i));
                    }
                    break;
            }
            circleView.setOnClickListener(onClickListener);
            circleView.setOnLongClickListener(onLongClickListener);
/*
            View.OnTouchListener onTouchListener = new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        String tag = (String) v.getTag();
                        //if (isLongClick) {
                        //GradientDrawable gradientDrawable = (GradientDrawable) v.getBackground();
                        //gradientDrawable.mutate();
                        //gradientDrawable.setColor(Color.GRAY);
                        //
                        return true;
                    } else {
                        return false;
                    }
                    return true;
                }
            };
            circleView.generateViewId();
            circleView.setOnTouchListener(onTouchListener);
*/
            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();
            gradientDrawable.setColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));

            mActivityMainBinding.layoutTop.addView(circleView);
        }

//        RelativeLayout.LayoutParams paramsBottom = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, minLEDsize);
//        paramsBottom.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
//        mActivityMainBinding.layoutBottom.setLayoutParams(paramsBottom);
        for (int i = 0; i < bottomPixelsCount; i++) {
            View circleView = new View(MainActivity.this);
            LinearLayout.LayoutParams paramsCircleView = new LinearLayout.LayoutParams(minLEDsize, minLEDsize);
            if (i == 0) {
                paramsCircleView.setMargins(0, 0, bottomLEDMargin, 0);
            } else if (i == topPixelsCount - 1) {
                paramsCircleView.setMargins(bottomLEDMargin, 0, 0, 0);
            } else {
                paramsCircleView.setMargins(bottomLEDMargin, 0, bottomLEDMargin, 0);
            }
            circleView.setLayoutParams(paramsCircleView);
            circleView.setBackgroundResource(R.drawable.round_view);
            switch (startCorner) {
                case 0:
                    if (clockwise) {
                        circleView.setTag("LED" + (allPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + i);
                    }
                    break;
                case 1:
                    if (clockwise) {
                        circleView.setTag("LED" + (topPixelsCount + rightPixelsCount + bottomPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + (leftPixelsCount + i));
                    }
                    break;
                case 2:
                    if (clockwise) {
                        circleView.setTag("LED" + (rightPixelsCount + bottomPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + (topPixelsCount + leftPixelsCount + i));
                    }
                    break;
                case 3:
                    if (clockwise) {
                        circleView.setTag("LED" + (bottomPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + (rightPixelsCount + topPixelsCount + leftPixelsCount + i));
                    }
                    break;
            }
            circleView.setOnClickListener(onClickListener);
            circleView.setOnLongClickListener(onLongClickListener);

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();
            gradientDrawable.setColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));

            mActivityMainBinding.layoutBottom.addView(circleView);
        }
//
//        RelativeLayout.LayoutParams paramsLeft = new RelativeLayout.LayoutParams(minLEDsize, RelativeLayout.LayoutParams.MATCH_PARENT);
//        paramsLeft.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
//        paramsLeft.addRule(RelativeLayout.ALIGN_PARENT_START);
//        paramsLeft.addRule(RelativeLayout.BELOW, R.id.layout_top);
//        paramsLeft.addRule(RelativeLayout.ABOVE, R.id.layout_bottom);
//        mActivityMainBinding.layoutLeft.setLayoutParams(paramsLeft);
        for (int i = 0; i < leftPixelsCount; i++) {
            View circleView = new View(MainActivity.this);
            LinearLayout.LayoutParams paramsCircleView = new LinearLayout.LayoutParams(minLEDsize, minLEDsize);
            paramsCircleView.setMargins(0, leftLEDMargin, 0, leftLEDMargin);
            circleView.setLayoutParams(paramsCircleView);
            circleView.setBackgroundResource(R.drawable.round_view);
            switch (startCorner) {
                case 0:
                    if (clockwise) {
                        circleView.setTag("LED" + (leftPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + (bottomPixelsCount + rightPixelsCount + topPixelsCount + i));
                    }
                    break;
                case 1:
                    if (clockwise) {
                        circleView.setTag("LED" + (allPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + i);
                    }
                    break;
                case 2:
                    if (clockwise) {
                        circleView.setTag("LED" + (rightPixelsCount + bottomPixelsCount + leftPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + (topPixelsCount + i));
                    }
                    break;
                case 3:
                    if (clockwise) {
                        circleView.setTag("LED" + (bottomPixelsCount + leftPixelsCount - 1 - i));
                    } else {
                        circleView.setTag("LED" + (rightPixelsCount + topPixelsCount + i));
                    }
                    break;
            }
            circleView.setOnClickListener(onClickListener);
            circleView.setOnLongClickListener(onLongClickListener);

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();
            gradientDrawable.setColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));

            mActivityMainBinding.layoutLeft.addView(circleView);
        }

//        RelativeLayout.LayoutParams paramsRight = new RelativeLayout.LayoutParams(minLEDsize, RelativeLayout.LayoutParams.MATCH_PARENT);
//        paramsRight.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//        paramsRight.addRule(RelativeLayout.ALIGN_PARENT_END);
//        paramsRight.addRule(RelativeLayout.BELOW, R.id.layout_top);
//        paramsRight.addRule(RelativeLayout.ABOVE, R.id.layout_bottom);
//        mActivityMainBinding.layoutRight.setLayoutParams(paramsRight);
        for (int i = 0; i < rightPixelsCount; i++) {
            View circleView = new View(MainActivity.this);
            LinearLayout.LayoutParams paramsCircleView = new LinearLayout.LayoutParams(minLEDsize, minLEDsize);
            paramsCircleView.setMargins(0, rightLEDMargin, 0, rightLEDMargin);
            circleView.setLayoutParams(paramsCircleView);
            circleView.setBackgroundResource(R.drawable.round_view);
            switch (startCorner) {
                case 0:
                    if (clockwise) {
                        circleView.setTag("LED" + (leftPixelsCount + topPixelsCount + i));
                    } else {
                        circleView.setTag("LED" + (bottomPixelsCount + rightPixelsCount - 1 - i));
                    }
                    break;
                case 1:
                    if (clockwise) {
                        circleView.setTag("LED" + (topPixelsCount + i));
                    } else {
                        circleView.setTag("LED" + (leftPixelsCount + bottomPixelsCount + rightPixelsCount - 1 - i));
                    }
                    break;
                case 2:
                    if (clockwise) {
                        circleView.setTag("LED" + i);
                    } else {
                        circleView.setTag("LED" + (allPixelsCount - 1 - i));
                    }
                    break;
                case 3:
                    if (clockwise) {
                        circleView.setTag("LED" + (bottomPixelsCount + leftPixelsCount + topPixelsCount + i));
                    } else {
                        circleView.setTag("LED" + (rightPixelsCount - 1 - i));
                    }
                    break;
            }
            circleView.setOnClickListener(onClickListener);
            circleView.setOnLongClickListener(onLongClickListener);

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();
            gradientDrawable.setColor(Color.argb(currentAlpha, currentRed, currentGreen, currentBlue));

            mActivityMainBinding.layoutRight.addView(circleView);
        }
    }

    private void segmentLEDs(int startPixel, int countPixels, int alpha, int red, int green, int blue) {
        for (int i = startPixel; i < startPixel + countPixels; i++) {
            View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
            GradientDrawable gradientDrawable = (GradientDrawable) led.getBackground();
            gradientDrawable.mutate();
            gradientDrawable.setColor(Color.argb(alpha, red, green, blue));
        }
    }

    public static float convertDpToPixel(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return px;
    }

    public static float convertPixelsToDp(float px, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float dp = px / ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return dp;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }
}

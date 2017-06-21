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
import android.graphics.Rect;
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
    private char mode;
    private int countSegments;
    private int[] currentPixelsColorsArray;
    private int[] pixelsColorsArray;
    private int model, startCorner, allPixelsCount, leftPixelsCount, topPixelsCount, rightPixelsCount, bottomPixelsCount;
    private boolean clockwise;
    private ActivityMainBinding mActivityMainBinding;
    private boolean isSmall;
    private long lastTime;
    private boolean isSegment = false;
    Rect outRect = new Rect();
    int[] location = new int[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        setSupportActionBar(mActivityMainBinding.toolbar);

        mActivityMainBinding.imageLightbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.arraycopy(currentPixelsColorsArray, 0, pixelsColorsArray, 0, currentPixelsColorsArray.length);
                ColorPickerDialogBuilder
                        .with(MainActivity.this)
                        .setTitle("Choose color")
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
                                if ((System.currentTimeMillis() - lastTime) > 100) {
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

                                        command = "s " + countSegments + " " + tempCommand + '\n';
                                    } else {
                                        Arrays.fill(pixelsColorsArray, Color.argb(alpha, red, green, blue));
                                        command = "s 1 0 " + allPixelsCount + " " +  alpha + " " + red + " " + green + " " + blue + '\n';
                                    }

                                    Log.d(TAG, "onColorChanged: " + command);

                                    try {
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

                                command = "s " + countSegments + " " + tempCommand + '\n';
                                try {
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
            streamWriter.write("?\n");
            streamWriter.flush();
        } catch (IOException ex) {
            Log.e(TAG, "openBT: ", ex);
            Toast.makeText(MainActivity.this, "Check Bluetooth device", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(MainActivity.this, "No bluetooth adapter available", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, "No paired devices", Toast.LENGTH_SHORT).show();

                    Intent intentOpenBluetoothSettings = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    intentOpenBluetoothSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivityForResult(intentOpenBluetoothSettings, 1);
                } else {
                    Log.d(TAG, "findBT: Bluetooth Device Found");
                }
            } else {
                Log.d(TAG, "findBT: No paired devices");
                Toast.makeText(MainActivity.this, "No paired devices", Toast.LENGTH_SHORT).show();

                Intent intentOpenBluetoothSettings = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                intentOpenBluetoothSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(intentOpenBluetoothSettings, 1);
            }
        }
    }

    private  void openBT() throws IOException {
        if (mmDevice != null) {
            ParcelUuid[] supportedUuids = mmDevice.getUuids();
            UUID uuid = supportedUuids[0].getUuid();
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
//            if (mmSocket == null) {
//                Method createMethod = mmDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class });
//                mmSocket = (BluetoothSocket) createMethod.invoke(mmDevice, 1);
//            }
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            streamWriter = new OutputStreamWriter(mmOutputStream);
            mmInputStream = mmSocket.getInputStream();
            streamReader = new InputStreamReader(mmInputStream);

            beginListenForData();

            Log.d(TAG, "openBT: Bluetooth Opened");
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
//                                        int[] params = new int[strParams.length];
//                                        for (int j = 0; j < strParams.length; j++) {
//                                            params[j] = Integer.parseInt(strParams[j]);
//                                        }
                                        model = Integer.parseInt(strParams[0]);
                                        startCorner = Integer.parseInt(strParams[1]);
                                        clockwise = Integer.parseInt(strParams[2]) == 0;
                                        allPixelsCount = Integer.parseInt(strParams[3]);
                                        leftPixelsCount = Integer.parseInt(strParams[4]);
                                        topPixelsCount = Integer.parseInt(strParams[5]);
                                        rightPixelsCount = Integer.parseInt(strParams[6]);
                                        bottomPixelsCount = Integer.parseInt(strParams[7]);

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

                                        mode = strParams[8].charAt(0);
                                        switch (mode) {
                                            case 's':
                                                countSegments = Integer.parseInt(strParams[9]);
                                                for (int j = 0; j < countSegments; j++) {
                                                    final int startPixel = Integer.parseInt(strParams[10 + j*6]);
                                                    final int countPixels = Integer.parseInt(strParams[11 + j*6]);
                                                    final int alpha = Integer.parseInt(strParams[12 + j*6]);
                                                    final int red = Integer.parseInt(strParams[13 + j*6]);
                                                    final int green = Integer.parseInt(strParams[14 + j*6]);
                                                    final int blue = Integer.parseInt(strParams[15 + j*6]);

                                                    Arrays.fill(currentPixelsColorsArray, startPixel, startPixel + countPixels, Color.argb(alpha, red, green, blue));

                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (countSegments == 1 && countPixels == allPixelsCount) {
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

    private void closeBT() throws IOException {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        Log.d(TAG, "closeBT: Bluetooth Closed");
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
                command = "r\n";
                try {
                    streamWriter.write(command);
                    streamWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.action_rainbow_cycle:
                command = "c\n";
                try {
                    streamWriter.write(command);
                    streamWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void drawLEDs() {
        int tempHeight = mActivityMainBinding.relativeLayout.getHeight();
        int leftLEDSize = tempHeight / leftPixelsCount;
        int rightLEDSize = tempHeight / rightPixelsCount;

        int width = mActivityMainBinding.relativeLayout.getWidth();
        int topLEDSize = width / topPixelsCount;
        int bottomLEDSize = width / bottomPixelsCount;

        int minLEDsize = (int) (Math.min(Math.min(leftLEDSize, rightLEDSize), Math.min(topLEDSize, bottomLEDSize)) * 0.8f);

        int height = tempHeight - minLEDsize - minLEDsize;

        int leftLEDMargin = (height - leftPixelsCount * minLEDsize)/leftPixelsCount/2;
        int rightLEDMargin = (height - rightPixelsCount * minLEDsize)/rightPixelsCount/2;
        int topLEDMargin = (width - topPixelsCount * minLEDsize)/(topPixelsCount - 1)/2;
        int bottomLEDMargin = (width - bottomPixelsCount * minLEDsize)/(bottomPixelsCount - 1)/2;

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

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();

            mActivityMainBinding.layoutTop.addView(circleView);
        }

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

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();

            mActivityMainBinding.layoutBottom.addView(circleView);
        }

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

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();

            mActivityMainBinding.layoutLeft.addView(circleView);
        }

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

            GradientDrawable gradientDrawable = (GradientDrawable) circleView.getBackground();
            gradientDrawable.mutate();

            mActivityMainBinding.layoutRight.addView(circleView);
        }

        mActivityMainBinding.frameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        //isLongClick = false;
                        break;
                    case MotionEvent.ACTION_DOWN:
                        int touchRawXDown = (int) event.getRawX();
                        int touchRawYDown = (int) event.getRawY();

                        for (int i = 0; i < allPixelsCount; i++) {
                            View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                            if (isViewInBounds(led, touchRawXDown, touchRawYDown)) {
                                if (led.getScaleX() == 1.0f && led.getScaleY() == 1.0f) {
                                    isSmall = false;
                                    led.setScaleX(0.8f);
                                    led.setScaleY(0.8f);
                                } else {
                                    isSmall = true;
                                    led.setScaleX(1.0f);
                                    led.setScaleY(1.0f);
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int touchRawXMove = (int) event.getRawX();
                        int touchRawYMove = (int) event.getRawY();

                        for (int i = 0; i < allPixelsCount; i++) {
                            View led = mActivityMainBinding.relativeLayout.findViewWithTag("LED" + i);
                            if (isViewInBounds(led, touchRawXMove, touchRawYMove)) {
                                if (isSmall) {
                                    led.setScaleX(1.0f);
                                    led.setScaleY(1.0f);
                                } else {
                                    led.setScaleX(0.8f);
                                    led.setScaleY(0.8f);
                                }
                            }
                        }
                        break;
                }

                return true;
            }
        });
    }

    private boolean isViewInBounds(View view, int x, int y){
        view.getDrawingRect(outRect);
        view.getLocationOnScreen(location);
        outRect.offset(location[0], location[1]);
        return outRect.contains(x, y);
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
}

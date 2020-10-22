package si.uni_lj.fri.pbd.miniapp2;

import java.util.Arrays;
import java.util.Date;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class AccelerationService extends Service implements SensorEventListener {

    private static String TAG = AccelerationService.class.toString();
    public enum GestureType {
        IDLE,
        HORIZONTAL,
        VERTICAL
    }

    private IBinder serviceBinder = new AccelerationService.RunServiceBinder();
    LocalBroadcastManager broadcastManager;

    private double[] previousValues = { 0.0, 0.0, 0.0 };
    private long lastRecordedTime = new Date().getTime();
    private SensorManager sensorManager;
    private Sensor sensor;

    @Override
    public void onCreate() {
        // Create sensor and sensor manager
        Log.d(TAG, "Creating and connecting to accelerometer");
        sensorManager = (SensorManager)getSystemService(Service.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        else {
            Log.e(TAG, "Cannot get connection to accelerometer");
            stopSelf();
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Create LocalBroadcastManager
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double noiseThreshold = 5.0;
        double[] values = { event.values[0], event.values[1], event.values[2] };
        double dx = Math.abs(values[0] - previousValues[0]);
        double dy = Math.abs(values[1] - previousValues[1]);
        double dz = Math.abs(values[2] - previousValues[2]);
        if (dx <= noiseThreshold) dx = 0;
        if (dy <= noiseThreshold) dy = 0;
        if (dz <= noiseThreshold) dz = 0;
        previousValues = Arrays.copyOf(values, 3);

        GestureType gesture = GestureType.IDLE;
        if (dx > dz) gesture = GestureType.HORIZONTAL;
        if (dz > dx) gesture = GestureType.VERTICAL;

        if (gesture != GestureType.IDLE && (new Date().getTime() - lastRecordedTime >= 500)) {
            lastRecordedTime = new Date().getTime();
            if (gesture == GestureType.VERTICAL) {
                Log.d(TAG, "Sending ACTION_START  broadcast to MediaPlayerService");
                Intent intent = new Intent(this, MediaPlayerService.class);
                intent.setAction(MediaPlayerService.ACTION_START);
                broadcastManager.sendBroadcast(intent);
            }
            else {
                Log.d(TAG, "Sending ACTION_PAUSE broadcast to MediaPlayerService");
                Intent intent = new Intent(this, MediaPlayerService.class);
                intent.setAction(MediaPlayerService.ACTION_PAUSE);
                broadcastManager.sendBroadcast(intent);
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public class RunServiceBinder extends Binder {
        AccelerationService getService() {
            return AccelerationService.this;
        }
    }
}

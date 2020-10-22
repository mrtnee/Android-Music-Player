package si.uni_lj.fri.pbd.miniapp2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private MediaPlayerService mediaService;
    private TextView textTitle;
    private TextView textDuration;
    private Switch switchExternalStorage;

    private static final int ACCESS_EXT_STORAGE_REQUEST_ID = 1;
    private final static int MSG_UPDATE_TIME = 0;

    BroadcastReceiver broadcastReceiver;
    private final Handler mUpdateTimeHandler = new UIUpdateHandler(this);
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MediaPlayerService.RunServiceBinder binder = (MediaPlayerService.RunServiceBinder)iBinder;

            mediaService = binder.getService();
            mediaService.foreground();
            Log.d(TAG, "Service connected!");
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) { }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupPermission();

        textTitle = findViewById(R.id.text_song_title);
        textDuration = findViewById(R.id.text_song_duration);

        // Create the broadcast receiver and register it
        broadcastReceiver = new ActionBroadcastReceiver(this);
        IntentFilter filter = new IntentFilter(MediaPlayerService.ACTION_START);
        filter.addAction(MediaPlayerService.ACTION_PAUSE);
        filter.addAction(MediaPlayerService.ACTION_STOP);
        filter.addAction(MediaPlayerService.ACTION_EXIT);
        filter.addAction(MediaPlayerService.NO_MEDIA_ON_EXTERNAL_DRIVE);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);

        // Find the switch and create a listener that updates MediaPlayerService
        switchExternalStorage = findViewById(R.id.switch_external_storage);
        switchExternalStorage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mediaService != null) {
                    if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED && isChecked) {
                        Switch sw = (Switch) buttonView;
                        sw.setChecked(false);
                        Toast.makeText(getApplicationContext(), "Permission to access external storage was denied.", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        mediaService.setLoadFromStorage(isChecked);
                    }
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent i = new Intent(this, MediaPlayerService.class);
        i.setAction(MediaPlayerService.START_SERVICE);
        startService(i);
        bindService(i, mConnection, 0);

        // make the UI update while the application is active
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);

        // stop the periodic updating of UI
        mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    public void setupPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request the permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Permission is required in order to play music files that are saved locally on the device.")
                        .setTitle("Permission required");

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                                ACCESS_EXT_STORAGE_REQUEST_ID);
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
            else {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                        ACCESS_EXT_STORAGE_REQUEST_ID);
            }
        }
        else {
            // Permission is granted
            Log.d(TAG, "Permission to access external storage granted");
        }
    }

    // OnClick methods
    public void onStartClick(View view) {
        mediaService.start();
    }
    public void onPauseClick(View view) {
        mediaService.pause();
    }
    public void onStopClick(View view) {
        mediaService.stop();
    }
    public void onExitClick(View view) {
        mediaService.exit();
        stopService(new Intent(getApplicationContext(), MediaPlayerService.class));
        finish();
        System.exit(0); 
    }
    public void onGonClick(View view) {
        mediaService.gOn();
    }
    public void onGoffClick(View view) {
        mediaService.gOff();
    }

    private void updateUI() {
        if (mediaService != null && mediaService.isPlaying()) {
            // Update duration
            textDuration.setText(mediaService.getDurationString());

            // Update title
            String songTitle = mediaService.getTitle();
            textTitle.setText(songTitle);
        }
        else {
            textDuration.setText(getString(R.string.song_duration_placeholder));
            textTitle.setText(R.string.song_title_placeholder);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ACCESS_EXT_STORAGE_REQUEST_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission to access external storage files granted by user");
            }
            else {
                Log.d(TAG, "Permission to access external storage files denied by user");;
            }
        }
    }

    static class UIUpdateHandler extends Handler {
        private final static int UPDATE_RATE_MS = 1000;
        private final WeakReference<MainActivity> activity;

        UIUpdateHandler(MainActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message message) {
            if (MSG_UPDATE_TIME == message.what) {
                activity.get().updateUI();
                sendEmptyMessageDelayed(MSG_UPDATE_TIME, UPDATE_RATE_MS);
            }
        }
    }

    public static class ActionBroadcastReceiver extends BroadcastReceiver {
        private final WeakReference<MainActivity> activity;

        public ActionBroadcastReceiver(MainActivity mainActivity) {
            activity = new WeakReference<>(mainActivity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "ActionBroadcastReceiver onReceive with Intent: " + intent.getAction());
            if (intent.getAction().equals(MediaPlayerService.ACTION_START)) {
                activity.get().onStartClick(null);
            }
            else if (intent.getAction().equals(MediaPlayerService.ACTION_PAUSE)) {
                activity.get().onPauseClick(null);
            }
            else if (intent.getAction().equals(MediaPlayerService.ACTION_EXIT)) {
                activity.get().onExitClick(null);
            }
            else if (intent.getAction().equals(MediaPlayerService.ACTION_STOP)) {
                activity.get().onStopClick(null);
            }
            else if (intent.getAction().equals(MediaPlayerService.NO_MEDIA_ON_EXTERNAL_DRIVE)) {
                activity.get().switchExternalStorage.setChecked(false);
                Toast.makeText(context, "You have no songs on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

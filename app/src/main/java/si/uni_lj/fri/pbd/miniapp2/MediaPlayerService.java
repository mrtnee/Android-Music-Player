package si.uni_lj.fri.pbd.miniapp2;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.renderscript.RenderScript;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileDescriptor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Random;

public class MediaPlayerService extends Service {

    private static final String TAG = MediaPlayerService.class.getSimpleName();

    public static final String START_SERVICE = "start_service";
    public static final String ACTION_START = "start_music";
    public static final String ACTION_STOP = "stop_music";
    public static final String ACTION_PAUSE = "pause_music";
    public static final String ACTION_EXIT = "exit_application";
    public static final String NO_MEDIA_ON_EXTERNAL_DRIVE = "no_songs_on_device";
    public static final int NOTIFICATION_ID = 69;
    private static final int MSG_UPDATE_TIME = 0;

    private static final String channelID = "background_music_player";
    private NotificationManager notificationManager;
    private final Handler updateUIHandler = new NotificationUIUpdateHandler(this);
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID);
    private RemoteViews notificationLayout;

    // variables for the state of the song
    private boolean isStopped = false;
    private boolean isPlaying = false;
    private int duration;
    private String title;
    private boolean loadFromStorage = false;
    private int firstPlay = 0;

    // variables for AccelerationService
    private IBinder serviceBinder = new RunServiceBinder();
    private MediaPlayer mediaPlayer;
    private Service accelerationService;
    private boolean gon;
    LocalBroadcastManager broadcastManager;
    BroadcastReceiver broadcastReceiver;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            Log.d(TAG, "Acceleration service is connected!");
            AccelerationService.RunServiceBinder binder = (AccelerationService.RunServiceBinder) iBinder;
            accelerationService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Acceleration service is disconnected!");
        }
    };

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating service");
        loadRandomSong();
        createNotificationChannel();

        updateUIHandler.sendEmptyMessage(MSG_UPDATE_TIME);

        // Create local broadcast manager for handling broadcasts
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service!");
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        updateUIHandler.removeMessages(MSG_UPDATE_TIME);
        super.onDestroy();
        Log.d(TAG, "Destroying service!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        return START_NOT_STICKY;
    }

    public void loadRandomSong() {
        // First stop the song that is currently playing
        stop();
        if (loadFromStorage) {
            loadRandomSongFromStorage();
        }
        else {
            loadRandomSongFromResources();
        }
    }

    private void loadRandomSongFromResources() {
        ArrayList<Integer> songIds = new ArrayList<>();
        songIds.add(R.raw.gta_san_andreas);
        songIds.add(R.raw.smoke_on_the_water);
        songIds.add(R.raw.stayin_alive);

        int randomSong = songIds.get(new Random().nextInt(songIds.size()));

        // First song that will played when the app is started is GTA San Andreas theme song
        // It's not well made but it works
        if (firstPlay < 2) {
            randomSong = R.raw.gta_san_andreas;
            firstPlay++;
        }

        mediaPlayer = MediaPlayer.create(this, randomSong);

        duration = mediaPlayer.getDuration();
        // Retrieve song title with MediaDataRetriever
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Uri mediaPath = Uri.parse("android.resource://" + getPackageName() + "/" + randomSong);
        metadataRetriever.setDataSource(this, mediaPath);
        title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
    }

    private void loadRandomSongFromStorage() {
        // First check for permissions, if the user has not given the permission, play the built in songs
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            loadRandomSongFromResources();
            return;
        }

        String[] projection = {
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME
        };

        Cursor cursor = getApplicationContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
        );

        int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);

        // Check if user has any songs in storage
        if (cursor.getCount() < 1) {
            Intent intent = new Intent(NO_MEDIA_ON_EXTERNAL_DRIVE);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            return;
        }

        // move cursor to random song
        cursor.moveToPosition(new Random().nextInt(cursor.getCount()));

        long id = cursor.getLong(idColumn);
        Uri songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        mediaPlayer = MediaPlayer.create(this, songUri);
        // When the song is finished playing, play the next song
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                loadRandomSong();
            }
        });

        duration = mediaPlayer.getDuration();
        title = cursor.getString(titleColumn);
    }

    public void start() {
        if (mediaPlayer == null || isStopped) {
            loadRandomSong();
            isStopped = false;
        }

        mediaPlayer.start();
        isPlaying = true;
    }

    public void pause() {
        if (mediaPlayer != null && !isStopped) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
//            mediaPlayer.stop();
            mediaPlayer.release();
        }
        isStopped = true;
        isPlaying = false;
    }

    public void exit() {
        stop();
    }

    public void gOn() {
        if (!gon) {
            // Create broadcast receiver
            broadcastReceiver = new GestureBroadcastReceiver(this);
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(MediaPlayerService.ACTION_START);
            filter.addAction(MediaPlayerService.ACTION_PAUSE);
            broadcastManager.registerReceiver(broadcastReceiver, filter);

            Intent i = new Intent(this, AccelerationService.class);
            i.setAction("random action");
            startService(i);
            bindService(i, connection, 0);
            gon = true;

            Toast.makeText(this, "Gestures activated.", Toast.LENGTH_SHORT).show();
        }
    }

    public void gOff() {
        if (gon) {
            broadcastManager.unregisterReceiver(broadcastReceiver);
            accelerationService.stopSelf();
            unbindService(connection);
            gon = false;

            Toast.makeText(this, "Gestured deactivated", Toast.LENGTH_SHORT).show();
        }
    }

    public int getDuration() {
        return this.duration;
    }

    public String getDurationString() {
        int elapsedTime = elapsedTime() / 1000;
        int duration = getDuration() / 1000;
        return elapsedTime + " / " + duration + " s";
    }

    public int elapsedTime() {
        return mediaPlayer.getCurrentPosition();
    }

    public String getTitle() {
        return this.title;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setLoadFromStorage(boolean loadFromStorage) {
        this.loadFromStorage = loadFromStorage;
        loadRandomSong();
    }

    public void foreground() {
        startForeground(NOTIFICATION_ID, createNotification());
    }

    private Notification createNotification() {
        // TODO: when user clicks on notification launch MainActivity
        notificationLayout = new RemoteViews(getPackageName(), R.layout.notification_big);
        // Create pending intent for start button
        Intent intentStart = new Intent(this, MediaPlayerService.class);
        intentStart.setAction(ACTION_START);
        PendingIntent pendingIntentStart = PendingIntent.getService(this, 0, intentStart, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationLayout.setOnClickPendingIntent(R.id.notification_button_start, pendingIntentStart);
        // Pause button
        Intent intentPause = new Intent(this, MediaPlayerService.class);
        intentPause.setAction(ACTION_PAUSE);
        PendingIntent pendingIntentPause = PendingIntent.getService(this, 0, intentPause, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationLayout.setOnClickPendingIntent(R.id.notification_button_pause, pendingIntentPause);
        // Stop button
        Intent intentStop = new Intent(this, MediaPlayerService.class);
        intentStop.setAction(ACTION_STOP);
        PendingIntent pendingIntentStop = PendingIntent.getService(this, 0, intentStop, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationLayout.setOnClickPendingIntent(R.id.notification_button_stop, pendingIntentStop);
        // Exit button
        Intent intentExit = new Intent(this, MediaPlayerService.class);
        intentExit.setAction(ACTION_EXIT);
        PendingIntent pendingIntentExit = PendingIntent.getService(this, 0, intentExit, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationLayout.setOnClickPendingIntent(R.id.notification_button_exit, pendingIntentExit);

        // Pending intent when user clicks the notification -> launch MainActivity
        Intent launchMainActivity = new Intent(this, MainActivity.class);
        PendingIntent pendingLaunchMainActivity = PendingIntent.getActivity(this, 0, launchMainActivity, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayout)
                .setContentIntent(pendingLaunchMainActivity)
                .build();

        return notification;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        else {
            NotificationChannel channel = new NotificationChannel(MediaPlayerService.channelID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channel_description));

            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotificationUI() {
        if (notificationLayout != null) {
            if (isPlaying()) {
                notificationLayout.setTextViewText(R.id.notification_text_song_title, getTitle());
                notificationLayout.setTextViewText(R.id.notification_text_song_duration, getDurationString());
            }
            else {
                notificationLayout.setTextViewText(R.id.notification_text_song_title, getString(R.string.song_title_placeholder));
                notificationLayout.setTextViewText(R.id.notification_text_song_duration, getString(R.string.song_duration_placeholder));
            }
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    public class RunServiceBinder extends Binder {
        MediaPlayerService getService() { return MediaPlayerService.this; }
    }

    static class NotificationUIUpdateHandler extends Handler {
        private static final int UPDATE_RATE_MS = 1000;
        private final WeakReference<MediaPlayerService> service;

        NotificationUIUpdateHandler(MediaPlayerService service) {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message message) {
            if (MSG_UPDATE_TIME == message.what) {
                service.get().updateNotificationUI();
                sendEmptyMessageDelayed(MSG_UPDATE_TIME, UPDATE_RATE_MS);
            }
        }
    }

    public static class GestureBroadcastReceiver  extends BroadcastReceiver {

        MediaPlayerService mediaPlayerService;

        public GestureBroadcastReceiver(MediaPlayerService service) {
            mediaPlayerService = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_START)) {
                Log.d(TAG, "ACTION_START broadcast received");
                mediaPlayerService.start();
            }
            else if (intent.getAction().equals(ACTION_PAUSE)) {
                Log.d(TAG, "ACTION_PAUSE broadcast received");
                mediaPlayerService.pause();
            }
        }
    }
}

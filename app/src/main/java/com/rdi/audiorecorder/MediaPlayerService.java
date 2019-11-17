package com.rdi.audiorecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import static com.rdi.audiorecorder.MainActivity.EXTRA_FILE_NAME_FOR_PLAYING;
import static com.rdi.audiorecorder.MainActivity.MSG_PLAY_PREVIOUS_OR_NEXT;
import static com.rdi.audiorecorder.MainActivity.MSG_PLAY_STOP;
import static com.rdi.audiorecorder.MainActivity.MSG_UPDATE_TIMER_TEXT_VIEW;


public class MediaPlayerService extends Service {
    private static final String TAG = "MediaPlayerService";

    private RemoteViews remoteViews;

    private static final String CHANNEL_1_ID = "Chanal_1";
    public static final String PLAY_PREVIOUS = "PLAY_PREVIOUS";
    public static final String PLAY_NEXT = "PLAY_NEXT";
    public static final String RECORD_CLOSE = "PLAYER_Close";
    private static final int NOTIFICATION_ID = 1;
    public static final int MSG_START_PLAYING = 201;

    private String mPlayPreviousOrNext;

    private MediaPlayer mMediaPlayer;

    private final Handler handler = new Handler();
    private Runnable incrementRunnable;

    private int mCountTimer = 0;


    private Messenger mMessenger = new Messenger(new InternalHandler());

    private Messenger mMainActivityMessenger;

    class InternalHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_START_PLAYING:
                    mMainActivityMessenger = msg.replyTo;
                    Bundle bundle = msg.getData();
                    String nameOfPlayingFile = bundle.getString(EXTRA_FILE_NAME_FOR_PLAYING);

                    stoppingPlayingRemoveCallbacks();

                    playAndNotificationStart(nameOfPlayingFile);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    public void stoppingPlayingRemoveCallbacks() {
        handler.removeCallbacks(incrementRunnable);
        updateNotification(0);
        playStop();
    }

    @Override
    public void onCreate() {
        createNotificationChanel();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + intent.getAction());

        if (PLAY_PREVIOUS.equals(intent.getAction()) || PLAY_NEXT.equals(intent.getAction())) {
            stoppingPlayingRemoveCallbacks();
            mPlayPreviousOrNext = intent.getAction();
            sendMessage(MSG_PLAY_PREVIOUS_OR_NEXT);
        } else if (RECORD_CLOSE.equals(intent.getAction())) {
            stoppingPlayingRemoveCallbacks();
            sendMessage(MSG_PLAY_STOP);
            stopForeground(true);
        }
        return START_NOT_STICKY;
    }

    public void playAndNotificationStart(String nameOfPlayingFile) {
        Log.d(TAG, "playAndNotificationStart: ");
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stoppingPlayingRemoveCallbacks();
                sendMessage(MSG_PLAY_STOP);
                stopForeground(true);
            }
        });

        playStart(nameOfPlayingFile);
        Log.d(TAG, "timerForRecording: ");
        timerForRecording();
        mCountTimer = 0;
        startForeground(NOTIFICATION_ID, createNotification(mCountTimer));
    }

    private void sendMessage(int msg) {
        Bundle bundle = new Bundle();
        Message message;

        switch (msg) {
            case MSG_UPDATE_TIMER_TEXT_VIEW:
                bundle.putInt(MainActivity.EXTRA_TIMER, mCountTimer);
                break;
            case MSG_PLAY_STOP:

                break;

            case MSG_PLAY_PREVIOUS_OR_NEXT:
                bundle.putString(MainActivity.EXTRA_PREVIOUS_OR_NEXT, mPlayPreviousOrNext);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + msg);
        }
        message = Message.obtain(null, msg);
        message.setData(bundle);
        try {
            mMainActivityMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    private void timerForRecording() {
        incrementRunnable = new Runnable() {
            @Override
            public void run() {
                mCountTimer++;
                handler.postDelayed(this, 1000);
                updateNotification(mCountTimer);
                sendMessage(MSG_UPDATE_TIMER_TEXT_VIEW);
            }
        };
        handler.postDelayed(incrementRunnable, 1000);
    }

    private Notification createNotification(int countTimer) {

        Intent intentButtonPrevious = new Intent(this, MediaPlayerService.class);
        intentButtonPrevious.setAction(PLAY_PREVIOUS);
        PendingIntent pendingIntentButtonPrevious = PendingIntent.getService(this, 0, intentButtonPrevious, 0);

        Intent intentButtonNext = new Intent(this, MediaPlayerService.class);
        intentButtonNext.setAction(PLAY_NEXT);
        PendingIntent pendingIntentButtonNext = PendingIntent.getService(this, 0, intentButtonNext, 0);

        Intent closeServiseIntent = new Intent(this, MediaPlayerService.class);
        closeServiseIntent.setAction(RECORD_CLOSE);
        PendingIntent closePendingIntent = PendingIntent.getService(this, 0, closeServiseIntent, 0);

        remoteViews = new RemoteViews(getPackageName(), R.layout.playing_audio_notification);
        remoteViews.setTextViewText(R.id.text_recording, "Воспроизведение " +
                getResources().getQuantityString(R.plurals.times_count, countTimer, countTimer));
        remoteViews.setImageViewResource(R.id.image_record, R.drawable.ic_play_circle_filled_black_24dp);
        remoteViews.setOnClickPendingIntent(R.id.btnPrevious, pendingIntentButtonPrevious);
        remoteViews.setOnClickPendingIntent(R.id.btnNext, pendingIntentButtonNext);
        remoteViews.setOnClickPendingIntent(R.id.btnStop, closePendingIntent);

        Log.d(TAG, "createNotification: ");

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntentForGoToMainActivity = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_1_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContent(remoteViews)
                .setAutoCancel(true)
                .setContentIntent(pendingIntentForGoToMainActivity);

        return builder.build();
    }

    private void updateNotification(int time) {
        Notification notification = createNotification(time);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(NOTIFICATION_ID, notification);
    }

    private void createNotificationChanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_1_ID,
                    "Channel 1 name", NotificationManager.IMPORTANCE_DEFAULT);
            Log.d(TAG, "createNotificationChanel: ");

            notificationChannel.setDescription("Channel 1 description");

            NotificationManager notificationManager = getSystemService((NotificationManager.class));
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    public void playStart(String fileName) {
        try {
            mMediaPlayer.setDataSource(fileName);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releasePlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public void playStop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}

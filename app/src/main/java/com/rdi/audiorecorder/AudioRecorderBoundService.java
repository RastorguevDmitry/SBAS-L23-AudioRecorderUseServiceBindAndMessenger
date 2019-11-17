package com.rdi.audiorecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AudioRecorderBoundService extends Service {

    private static final String TAG = "AudioRecorderBoundService";

    private static final String CHANNEL_1_ID = "Chanal_1";
    private static final String RECORD_CLICK = "RecordClick";
    private static final String RECORD_CLOSE = "RECORD_Close";
    private static final int NOTIFICATION_ID = 1;
    private int mCountTimer = 0;
    private RemoteViews remoteViews;
    private final Handler handler = new Handler();
    private Runnable incrementRunnable;
    private boolean isRecord;

    private MediaRecorder mediaRecorder;
    private String fileName;


    private IBinder mLocalBinder = new AudioRecorderBoundService.LocalBinder();

    private OnRecordStopListener mOnRecordStopListener;

    class LocalBinder extends Binder {
        AudioRecorderBoundService getBoundService() {
            return AudioRecorderBoundService.this;
        }
    }


    @Override
    public void onCreate() {
        createNotificationChanel();

        super.onCreate();
    }

    public void createFileForSaving() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String recordFileName = "/record_" + timeStamp + ".3gpp";
        fileName = getApplicationContext().getExternalFilesDir(AUDIO_SERVICE) + recordFileName;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + intent.getAction());

        if (RECORD_CLICK.equals(intent.getAction()) & isRecord == true) {
            handler.removeCallbacks(incrementRunnable);
            updateNotification(mCountTimer);
            isRecord = false;
            recordPause();
        } else if (RECORD_CLOSE.equals(intent.getAction())) {
            recordStop();
            stopForeground(true);
            handler.removeCallbacks(incrementRunnable);
        }
        return START_NOT_STICKY;
    }

    public void recordAndNotificationStart(OnRecordStopListener onRecordStopListener) {
        Log.d(TAG, "recordAndNotificationStart: ");
        createFileForSaving();
        isRecord = true;
        mOnRecordStopListener = onRecordStopListener;
        recordStart();
        timerForRecording();
        mCountTimer = 0;
        startForeground(NOTIFICATION_ID, createNotification(mCountTimer));
    }


    private void timerForRecording() {
        incrementRunnable = new Runnable() {
            @Override
            public void run() {
                mCountTimer++;
                handler.postDelayed(this, 1000);
                updateNotification(mCountTimer);
            }
        };
        handler.postDelayed(incrementRunnable, 1000);
    }


    private Notification createNotification(int countTimer) {
        Intent intentButtonRecord = new Intent(this, AudioRecorderBoundService.class);
        intentButtonRecord.setAction(RECORD_CLICK);
        PendingIntent pendingIntentButtonRecord = PendingIntent.getService(this, 0, intentButtonRecord, 0);

        Intent closeServiseIntent = new Intent(this, AudioRecorderBoundService.class);
        closeServiseIntent.setAction(RECORD_CLOSE);
        PendingIntent closePendingIntent = PendingIntent.getService(this, 0, closeServiseIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        remoteViews = new RemoteViews(getPackageName(), R.layout.recording_audio_notification);
        remoteViews.setTextViewText(R.id.text_recording, "Записано: " +
                getResources().getQuantityString(R.plurals.times_count, countTimer, countTimer));
        remoteViews.setImageViewResource(R.id.image_record, R.drawable.ic_play_circle_filled_black_24dp);
        remoteViews.setOnClickPendingIntent(R.id.btnRecord, pendingIntentButtonRecord);
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind() called with: intent = [" + intent + "]");

        return super.onUnbind(intent);
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    public void recordStart() {
        try {
            releaseRecorder();

            File outFile = new File(fileName);
            if (outFile.exists()) {
                outFile.delete();
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(fileName);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void recordPause() {

        //       mediaRecorder.pause(); device 23API,  needed 24API
        mediaRecorder.stop();
    }

    public void recordStop() {
        mOnRecordStopListener.onRecordStop();
        if (mediaRecorder != null) {
            mediaRecorder.stop();
        }
    }


    interface OnRecordStopListener {
        void onRecordStop();
    }
}

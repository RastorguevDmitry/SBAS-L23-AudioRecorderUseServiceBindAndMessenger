package com.rdi.audiorecorder;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;

import static com.rdi.audiorecorder.MediaPlayerService.MSG_START_PLAYING;
import static com.rdi.audiorecorder.MediaPlayerService.PLAY_NEXT;
import static com.rdi.audiorecorder.MediaPlayerService.PLAY_PREVIOUS;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE = 900;
    public static final String EXTRA_FILE_NAME_FOR_PLAYING = "EXTRA_FILE_NAME_FOR_PLAYING";
    public static final int MSG_UPDATE_TIMER_TEXT_VIEW = 202;
    public static final int MSG_PLAY_PREVIOUS_OR_NEXT = 250;
    public static final int MSG_PLAY_STOP = 350;
    public static final String EXTRA_TIMER = "EXTRA_TIMER";
    public static final String EXTRA_PREVIOUS_OR_NEXT = "EXTRA_PREVIOUS_OR_NEXT";

    private ListView mListView;
    private TextView mTextViewLablePlayingRecord;
    private TextView mCountTimerPlayingRecord;
    private ImageButton mImageButtonPlayingRecord;
    private ImageButton mImageButtonPlayingPrevious;
    private ImageButton mImageButtonPlayingNext;

    private File[] mRecordsFilesArray;
    private ArrayList<String> mRecordsLabelArray;

    private boolean mIsServiceBound;
    private AudioRecorderBoundService mmAudioRecorderBoundService;

    private Messenger mMediaPlayerServiceMessenger;
    private Messenger mMainActivityMessenger = new Messenger(new InternalMainActivityHandler());

    class InternalMainActivityHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_TIMER_TEXT_VIEW:
                    Log.d(TAG, "handleMessage() called with: msg = [" + msg + "]");

                    Bundle bundle = msg.getData();
                    int timerText = bundle.getInt(EXTRA_TIMER);
                    mCountTimerPlayingRecord.setText(getResources().getString(R.string.playing) + getResources().getQuantityString(R.plurals.times_count, timerText, timerText));

                    break;

                case MSG_PLAY_PREVIOUS_OR_NEXT:
                    Log.d(TAG, "handleMessage() called with: msg = [" + msg + "]");

                    Bundle bundlePreviousOrNext = msg.getData();
                    String stringPreviousOrNext = bundlePreviousOrNext.getString(EXTRA_PREVIOUS_OR_NEXT);
                    chekedPreviousOrNextPosition(stringPreviousOrNext);
                    playingRecords();
                    break;

                case MSG_PLAY_STOP:
                    Log.d(TAG, "handleMessage() called with: msg = [" + msg + "]");
                    mCountTimerPlayingRecord.setText(R.string.end_of_playing);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mAudioRecorderConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected() called with: componentName = [" + componentName + "], iBinder = [" + iBinder + "]");
            mmAudioRecorderBoundService = ((AudioRecorderBoundService.LocalBinder) iBinder).getBoundService();
            mIsServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected() called with: componentName = [" + componentName + "]");
            mmAudioRecorderBoundService = null;
            mIsServiceBound = false;
        }
    };

    private ServiceConnection mMediaPlayerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected() called with: componentName = [" + componentName + "], iBinder = [" + iBinder + "]");
            mMediaPlayerServiceMessenger = new Messenger(iBinder);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected() called with: componentName = [" + componentName + "]");
            mMediaPlayerServiceMessenger = null;
            mIsServiceBound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();

        setClickItemListeners();

        requestPermissions();

        startAndBindService();
    }

    private void initViews() {
        mListView = findViewById(R.id.list_records);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mTextViewLablePlayingRecord = findViewById(R.id.lable_playing_record);
        mCountTimerPlayingRecord = findViewById(R.id.count_timer_playing_record);
        mImageButtonPlayingRecord = findViewById(R.id.image_button_playing_record);
        mImageButtonPlayingPrevious = findViewById(R.id.image_button_playing_previous);
        mImageButtonPlayingNext = findViewById(R.id.image_button_playing_next);
    }

    private void setClickItemListeners() {
        mImageButtonPlayingRecord.setOnClickListener(this);
        mImageButtonPlayingPrevious.setOnClickListener(this);
        mImageButtonPlayingNext.setOnClickListener(this);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mTextViewLablePlayingRecord.setText(mRecordsLabelArray.get(position));
                mImageButtonPlayingRecord.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
            }
        });
        findViewById(R.id.start_record).setOnClickListener(this);
    }

    private void requestPermissions() {
        if (isExternalStorageReadable()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                readRecordsFilesFromStorage();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                        REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void startAndBindService() {
        Intent intentStartAudioRecorderService = new Intent(MainActivity.this, AudioRecorderBoundService.class);
        startService(intentStartAudioRecorderService);
        Intent bindIntentAudioRecorderService = new Intent(this, AudioRecorderBoundService.class);
        bindService(bindIntentAudioRecorderService, mAudioRecorderConnection, BIND_AUTO_CREATE);


        Intent intentStartMediaPlayerService = new Intent(MainActivity.this, MediaPlayerService.class);
        startService(intentStartMediaPlayerService);
        Intent bindIntentMediaPlayer = new Intent(this, MediaPlayerService.class);
        bindService(bindIntentMediaPlayer, mMediaPlayerServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_record:
                Log.d(TAG, "onClick: ");

                mmAudioRecorderBoundService.recordAndNotificationStart(new AudioRecorderBoundService.OnRecordStopListener() {
                    @Override
                    public void onRecordStop() {
                        Log.d(TAG, "onRecordStop: ");
                        readRecordsFilesFromStorage();
                    }
                });
                break;

            case R.id.image_button_playing_record:
                if (mListView.getCheckedItemPosition() < 0) chekedPreviousOrNextPosition(PLAY_NEXT);
                playingRecords();
                break;

            case R.id.image_button_playing_previous:
                chekedPreviousOrNextPosition(PLAY_PREVIOUS);
                playingRecords();
                break;
            case R.id.image_button_playing_next:
                chekedPreviousOrNextPosition(PLAY_NEXT);
                playingRecords();
                break;
        }
    }

    private void readRecordsFilesFromStorage() {
        File rootFolder = getApplicationContext().getExternalFilesDir(AUDIO_SERVICE);
        mRecordsFilesArray = rootFolder.listFiles();
        mRecordsLabelArray = new ArrayList<>();
        for (File file :
                mRecordsFilesArray) {
            mRecordsLabelArray.add(file.getName());
        }

        mListView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mRecordsLabelArray));
    }


    public void chekedPreviousOrNextPosition(String stringPreviousOrNext) {
        int chekedPoition = mListView.getCheckedItemPosition();
        mListView.setItemChecked(chekedPoition, false);
        if (stringPreviousOrNext.equals(MediaPlayerService.PLAY_NEXT)) {
            chekedPoition = chekedPoition == mRecordsLabelArray.size() - 1 ? 0 : chekedPoition + 1;

        } else {
            chekedPoition = chekedPoition == 0 ? mRecordsLabelArray.size() - 1 : chekedPoition - 1;
        }
        mListView.setItemChecked(chekedPoition, true);
    }


    public void playingRecords() {
        Bundle bundle = new Bundle();
        int chekedPosition = mListView.getCheckedItemPosition();
        bundle.putString(MainActivity.EXTRA_FILE_NAME_FOR_PLAYING, mRecordsFilesArray[chekedPosition].toString());
        mTextViewLablePlayingRecord.setText(mRecordsLabelArray.get(chekedPosition));

        Message message = Message.obtain(null, MSG_START_PLAYING);
        message.replyTo = mMainActivityMessenger;
        message.setData(bundle);

        try {
            mMediaPlayerServiceMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE & grantResults[0] == PackageManager.PERMISSION_GRANTED &
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            readRecordsFilesFromStorage();
        } else super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}

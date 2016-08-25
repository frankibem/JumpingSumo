package navigation.ai.fibem.com.classicnavigation.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.parrot.arsdk.arcommands.ARCOMMANDS_JUMPINGSUMO_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.util.Timer;
import java.util.TimerTask;

import navigation.ai.fibem.com.classicnavigation.R;
import navigation.ai.fibem.com.classicnavigation.data.MotionData;
import navigation.ai.fibem.com.classicnavigation.drone.AutoPilot;
import navigation.ai.fibem.com.classicnavigation.drone.JSDrone;
import navigation.ai.fibem.com.classicnavigation.view.JSVideoView;

public class PilotActivity extends AppCompatActivity
        implements AutoPilot.PilotCallback {
    private JSDrone mJSDrone;

    private ProgressDialog mConnectionProgressDialog;
    private JSVideoView mVideoView;
    private JSVideoView mdroneVideoView;
    private TextView mBatteryLabel;
    private TextView mTurnSpeedLabel;
    private TextView mForwardSpeedLabel;
    private Button mStartStopBtn;

    private boolean autoPiloting = false;
    private AutoPilot pilot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pilot);

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);

        mJSDrone = new JSDrone(this, service);
        mJSDrone.addListener(mJSListener);

        initControls();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the JumpingSumo drone is connecting
        if ((mJSDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mJSDrone.getConnectionState()))) {
            mConnectionProgressDialog = new ProgressDialog(this);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting to drone...");
            mConnectionProgressDialog.show();

            // if the connection to the Jumping fails, finish the activity
            if (!mJSDrone.connect()) {
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onBackPressed() {
        if (mJSDrone != null) {
            mConnectionProgressDialog = new ProgressDialog(this);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.show();

            if (!mJSDrone.disconnect()) {
                finish();
            }
        }
    }

    private void initControls() {
        mVideoView = (JSVideoView) findViewById(R.id.videoView);
        mdroneVideoView = (JSVideoView) findViewById(R.id.droneVideoView);
        mStartStopBtn = (Button) findViewById(R.id.btn_startStop);
        mBatteryLabel = (TextView) findViewById(R.id.txt_battery);
        mTurnSpeedLabel = (TextView) findViewById(R.id.txt_turnSpeed);
        mForwardSpeedLabel = (TextView) findViewById(R.id.txt_forwardSpeed);

        mStartStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (autoPiloting) {
                    // Stop moving
                    autoPiloting = false;
                    mStartStopBtn.setText("START");
                    pilot.stop();
                    motionTimer.cancel();
                } else {
                    autoPiloting = true;
                    mStartStopBtn.setText("STOP");

                    pilot.reset();
                    createTimerTask();
                    motionTimer.schedule(motionUpdate, 0, 60);
                }
            }
        });
    }

    private final JSDrone.Listener mJSListener = new JSDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    // Create the auto-pilot
                    mConnectionProgressDialog.setMessage("Creating the auto-pilot...");
                    pilot = new AutoPilot(mJSDrone, PilotActivity.this, PilotActivity.this);
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_JUMPINGSUMO_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            byte[] data = frame.getByteData();
            if (pilot != null && autoPiloting) {
                pilot.setNextFrame(data);
            }

            mVideoView.displayFrame(data);
            mdroneVideoView.displayFrame(data, true);
        }

        @Override
        public void onAudioStateReceived(boolean inputEnabled, boolean outputEnabled) {
        }

        @Override
        public void configureAudioDecoder(ARControllerCodec codec) {
        }

        @Override
        public void onAudioFrameReceived(ARFrame frame) {
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
        }

        @Override
        public void onDownloadComplete(String mediaName) {
        }
    };

    private Timer motionTimer;
    private TimerTask motionUpdate;

    private void createTimerTask() {
        motionTimer = new Timer("MotionTimer");
        motionUpdate = new TimerTask() {
            @Override
            public void run() {
                if ((pilot != null)) {
                    pilot.move();
                }
            }
        };
    }

    @Override
    public void onPilotInitialized() {
        mConnectionProgressDialog.dismiss();
    }

    @Override
    public void onErrorInitializing(Exception ex) {
        Log.e("AutoPilot", ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onMotionUpdated(MotionData data) {
        mTurnSpeedLabel.setText(Byte.toString(data.getTurnSpeed()));
        mForwardSpeedLabel.setText(Byte.toString(data.getForwardSpeed()));
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };
}
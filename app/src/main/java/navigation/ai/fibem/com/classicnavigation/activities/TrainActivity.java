package navigation.ai.fibem.com.classicnavigation.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
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

import java.io.IOException;

import navigation.ai.fibem.com.classicnavigation.R;
import navigation.ai.fibem.com.classicnavigation.data.MotionData;
import navigation.ai.fibem.com.classicnavigation.data.MotionRecorder;
import navigation.ai.fibem.com.classicnavigation.data.Observable;
import navigation.ai.fibem.com.classicnavigation.data.Observer;
import navigation.ai.fibem.com.classicnavigation.drone.JSDrone;
import navigation.ai.fibem.com.classicnavigation.view.JSVideoView;

public class TrainActivity extends AppCompatActivity implements Observer {
    private static final String TAG = "JSActivity";
    private JSDrone mJSDrone;

    private ProgressDialog mConnectionProgressDialog;

    private JSVideoView mVideoView;
    private TextView mBatteryLabel;
    private TextView mTurnSpeedLabel;
    private TextView mForwardSpeedLabel;
    private Button mStartStopBtn;

    private static final int MAX_TURN_SPEED = 30;
    private static final int MAX_FORWARD_SPEED = 50;

    private MotionData motionData;
    MotionRecorder recorder;
    private boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_train);

        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);

        mJSDrone = new JSDrone(this, service);
        mJSDrone.addListener(mJSListener);

        motionData = new MotionData();
        motionData.addObserver(this);
        recorder = new MotionRecorder();

        initControls();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the JumpingSumo drone is connecting
        if ((mJSDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mJSDrone.getConnectionState()))) {
            mConnectionProgressDialog = new ProgressDialog(this);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
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
        mStartStopBtn = (Button) findViewById(R.id.btn_startStop);
        mBatteryLabel = (TextView) findViewById(R.id.txt_battery);
        mTurnSpeedLabel = (TextView) findViewById(R.id.txt_turnSpeed);
        mForwardSpeedLabel = (TextView) findViewById(R.id.txt_forwardSpeed);

        mStartStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recording) {
                    // Stop recording
                    recording = false;
                    recorder.reset();
                    mStartStopBtn.setText("START");
                } else {
                    recording = true;
                    mStartStopBtn.setText("STOP");
                }
            }
        });
    }

    private final JSDrone.Listener mJSListener = new JSDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
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
            final byte[] data = mVideoView.displayFrame(frame);

            if (recording) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            recorder.record(motionData.copy(), data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
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

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Check that input came from a game controller
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {
            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();
            for (int i = 0; i < historySize; i++) {
                processJoystickIInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickIInput(event, -1);

            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    private void processJoystickIInput(MotionEvent event, int historyPos) {
        InputDevice input = event.getDevice();

        // Calculate the horizontal distance to move by using the input value from the right joystick
        float x = getCenteredAxis(event, input, MotionEvent.AXIS_Z, historyPos);
        float y = getCenteredAxis(event, input, MotionEvent.AXIS_Y, historyPos);

        // Move drone
        byte turnSpeed = (byte) (MAX_TURN_SPEED * x);
        byte forwardSpeed = (byte) (MAX_FORWARD_SPEED * -y);

        // Stop if no joystick motion
        if (x == 0 && y == 0) {
            mJSDrone.setFlag((byte) 0);
        } else {
            mJSDrone.setSpeed(forwardSpeed);
            mJSDrone.setTurn(turnSpeed);
            mJSDrone.setFlag((byte) 1);
        }

        motionData.updateMotion(forwardSpeed, turnSpeed);
    }

    private static float getCenteredAxis(MotionEvent event, InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis) :
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    @Override
    public void update(Observable observable) {
        mTurnSpeedLabel.setText(Byte.toString(motionData.getTurnSpeed()));
        mForwardSpeedLabel.setText(Byte.toString(motionData.getForwardSpeed()));
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
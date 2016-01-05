package org.jraf.android.aclock.app;

import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.jraf.android.aclock.R;
import org.jraf.android.aclock.prefs.MainPrefs;
import org.jraf.android.aclock.view.ClockView;

public class MainActivity extends AppCompatActivity {
    private static final int UI_ANIMATION_DELAY_MS = 300;
    private static final long UPDATE_BRIGHTNESS_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    private ClockView mVieClock;
    private View mVieBackgroundOpacity;

    private Handler mHideHandler = new Handler();
    private Toast mToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        mVieClock = (ClockView) findViewById(R.id.vieClock);
        mVieClock.setOnTouchListener(mAdjustOnTouchListener);

        mVieBackgroundOpacity = findViewById(R.id.vieBackgroundOpacity);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(UI_ANIMATION_DELAY_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUpdateBrightnessHandler.sendEmptyMessage(0);
    }

    @Override
    protected void onPause() {
        mUpdateBrightnessHandler.removeMessages(0);
        super.onPause();
    }

    /**
     * Handler to update the time periodically.
     */
    private Handler mUpdateBrightnessHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            MainPrefs mainPrefs = MainPrefs.get(MainActivity.this);
            int hour = mVieClock.getTime().hour;
            boolean isDay = 8 <= hour && hour < 23;
            if (isDay) {
                if (mainPrefs.containsBrightnessDay()) {
                    setBrightness(mainPrefs.getBrightnessDay());
                }

                if (mainPrefs.containsBackgroundOpacityDay()) {
                    setBackgroundOpacity(mainPrefs.getBackgroundOpacityDay());
                }
            } else {
                if (mainPrefs.containsBrightnessNight()) {
                    setBrightness(mainPrefs.getBrightnessNight());
                }

                if (mainPrefs.containsBackgroundOpacityNight()) {
                    setBackgroundOpacity(mainPrefs.getBackgroundOpacityNight());
                }
            }

            // Reschedule
            sendEmptyMessageDelayed(0, UPDATE_BRIGHTNESS_RATE_MS);
        }
    };

    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private Runnable mHideRunnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            mVieClock.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private final View.OnTouchListener mAdjustOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            float y = event.getY();
            int height = v.getHeight();
            float x = event.getX();
            int width = v.getWidth();

            float value = 1f - y / height;

            final boolean left = x < width / 2;
            if (left) {
                // Brightness
                toast(value, R.string.toast_brightness);
                setBrightness(value);
            } else {
                // Background opacity
                toast(value, R.string.toast_backgroundOpacity);
                setBackgroundOpacity(value);
            }

            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                MainPrefs mainPrefs = MainPrefs.get(MainActivity.this);
                int hour = mVieClock.getTime().hour;
                boolean isDay = 8 <= hour && hour < 23;
                if (left) {
                    // Brightness
                    if (isDay) {
                        mainPrefs.putBrightnessDay(value);
                    } else {
                        mainPrefs.putBrightnessNight(value);
                    }
                } else {
                    // Background opacity
                    if (isDay) {
                        mainPrefs.putBackgroundOpacityDay(value);
                    } else {
                        mainPrefs.putBackgroundOpacityNight(value);
                    }
                }
            }

            return true;
        }
    };


    /*
     * Brightness.
     */

    private void setBrightness(float value) {
        if (value < 0) value = 0;
        if (value > 1) value = 1;

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = value;
        getWindow().setAttributes(layout);
    }


    /*
     * Background opacity.
     */

    private void setBackgroundOpacity(float value) {
        if (value < 0) value = 0;
        if (value > 1) value = 1;
        mVieBackgroundOpacity.setBackgroundColor(Color.argb((int) (255f * (1f - value)), 0, 0, 0));
    }


    /*
     * Toast.
     */

    protected void toast(float brightness, int textRes) {
        String text = getString(textRes, (int) (brightness * 100));
        if (mToast == null) mToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        mToast.setText(text);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.show();
    }
}

package org.jraf.android.aclock.view;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.AttributeSet;
import android.view.View;

import org.jraf.android.aclock.R;

public class ClockView extends View {
    /**
     * Update rate in milliseconds.
     */
    private static final long UPDATE_RATE_MS = 1000;

    /**
     * This is supposed to be the 'widest' (in pixels) possible value for the seconds.
     */
    private static final String WIDEST_SECONDS = "00";

    /**
     * Number of pixels between hour / minutes and seconds.
     */
    private static final float SECONDS_SPACE_FACTOR = .04f;

    /**
     * How small seconds are compared to hour / minutes.
     */
    private static final float SECONDS_SIZE_FACTOR = .37f;

    /**
     * How small the am/pm indicator is compared to hour / minutes.
     */
    private static final float AM_PM_SIZE_FACTOR = .26f;


    private Time mTime;
    private boolean mIs24HourFormat;
    private SimpleDateFormat mDateFormat;
    private Paint mSecondsPaint;
    private Paint mHourMinutesPaint;
    private Paint mAmPmPaint;
    private Paint mDatePaint;
    private Rect mHourMinutesBounds;
    private Rect mSecondsBounds;
    private Rect mDateBounds;

    public ClockView(Context context) {
        super(context);
        init(context);
    }

    public ClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mTime = new Time();

        mIs24HourFormat = DateFormat.is24HourFormat(context);

        mHourMinutesPaint = new Paint();
        mHourMinutesPaint.setAntiAlias(true);
        mSecondsPaint = new Paint();
        mSecondsPaint.setAntiAlias(true);
        mAmPmPaint = new Paint();
        mAmPmPaint.setAntiAlias(true);
        mDatePaint = new Paint();
        mDatePaint.setAntiAlias(true);

        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "fonts/FredokaOne-Regular.ttf");
        mHourMinutesPaint.setTypeface(typeface);
        mSecondsPaint.setTypeface(typeface);
        mDatePaint.setTypeface(typeface);
        mAmPmPaint.setTypeface(typeface);

        mHourMinutesPaint.setColor(ContextCompat.getColor(context, R.color.hourMinutes));
        mSecondsPaint.setColor(ContextCompat.getColor(context, R.color.seconds));
        mAmPmPaint.setColor(ContextCompat.getColor(context, R.color.amPm));
        mDatePaint.setColor(ContextCompat.getColor(context, R.color.date));

        // Shadows
        int shadowColor = 0xFF000000; // black
        int shadowRadiusBig = getResources().getDimensionPixelSize(R.dimen.shadow_radius_big);
        int shadowDeltaBig = (int) (shadowRadiusBig / 1.5);
        int shadowRadiusSmall = getResources().getDimensionPixelSize(R.dimen.shadow_radius_small);
        int shadowDeltaSmall = (int) (shadowRadiusSmall / 1.5);
        mHourMinutesPaint.setShadowLayer(shadowRadiusBig, shadowDeltaBig, shadowDeltaBig, shadowColor);
        mSecondsPaint.setShadowLayer(shadowRadiusSmall, shadowDeltaSmall, shadowDeltaSmall, shadowColor);
        mAmPmPaint.setShadowLayer(shadowRadiusSmall, shadowDeltaSmall, shadowDeltaSmall, shadowColor);
        mDatePaint.setShadowLayer(shadowRadiusSmall, shadowDeltaSmall, shadowDeltaSmall, shadowColor);

        mHourMinutesBounds = new Rect();
        mSecondsBounds = new Rect();
        mDateBounds = new Rect();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mUpdateTimeHandler.sendEmptyMessage(0);
    }

    @Override
    protected void onDetachedFromWindow() {
        mUpdateTimeHandler.removeMessages(0);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();

        mTime.setToNow();
        String hourMinutesStr = getHourMinutes();
        String secondsStr = getSeconds();
        String dateStr = getDate();

        // Measure the date
        computeFitTextSizeForDate(dateStr, canvasWidth);
        mDatePaint.getTextBounds(dateStr, 0, dateStr.length(), mDateBounds);
        int dateWidth = mDateBounds.right;
        int dateHeight = mDateBounds.height();
        int dateX = (canvasWidth - dateWidth) / 2 - mDateBounds.left;
        int dateY = dateHeight;

        // Find the biggest possible font size that fits
        int step = 6;
        boolean wasNarrower = false;
        boolean firstTime = true;
        int hourMinutesWidth;
        int hourMinutesHeight;
        int secondsWidth;
        int hourMinutesSecondsWidth;
        int secondsSpace = (int) (SECONDS_SPACE_FACTOR * canvasWidth);
        while (true) {
            // Measure hour / minutes
            mHourMinutesPaint.getTextBounds(hourMinutesStr, 0, hourMinutesStr.length(), mHourMinutesBounds);
            hourMinutesWidth = mHourMinutesBounds.right;
            hourMinutesHeight = mHourMinutesBounds.height();

            // Measure seconds
            mSecondsPaint.getTextBounds(WIDEST_SECONDS, 0, WIDEST_SECONDS.length(), mSecondsBounds);
            secondsWidth = mSecondsBounds.right;

            hourMinutesSecondsWidth = hourMinutesWidth + secondsSpace + secondsWidth;

            int availableWidth = canvasWidth;
            if (hourMinutesSecondsWidth == availableWidth) {
                // Perfect fit: stop here
                break;
            } else if (hourMinutesSecondsWidth < availableWidth) {
                // Narrower
                if (firstTime || wasNarrower) {
                    // Was already narrower, try a bigger font
                    mHourMinutesPaint.setTextSize(mHourMinutesPaint.getTextSize() + step);
                    adjustSecondsAndAmPmSizes();
                } else {
                    // Went from wider to narrower: we found the best fit: stop here
                    break;
                }

                wasNarrower = true;
            } else {
                // Wider
                if (firstTime || !wasNarrower) {
                    // Was already wider, try a smaller font
                    mHourMinutesPaint.setTextSize(mHourMinutesPaint.getTextSize() - step);
                    adjustSecondsAndAmPmSizes();
                } else {
                    // Went from narrower to wider: we went too far: go back to the previous font size and stop here
                    mHourMinutesPaint.setTextSize(mHourMinutesPaint.getTextSize() - step);
                    adjustSecondsAndAmPmSizes();

                    // Measure hour / minutes
                    mHourMinutesPaint.getTextBounds(hourMinutesStr, 0, hourMinutesStr.length(), mHourMinutesBounds);
                    hourMinutesWidth = mHourMinutesBounds.right;
                    hourMinutesHeight = mHourMinutesBounds.height();

                    // Measure seconds
                    mSecondsPaint.getTextBounds(WIDEST_SECONDS, 0, WIDEST_SECONDS.length(), mSecondsBounds);
                    secondsWidth = mSecondsBounds.right;

                    hourMinutesSecondsWidth = hourMinutesWidth + secondsSpace + secondsWidth;

                    break;
                }

                wasNarrower = false;
            }
            firstTime = false;
        }

        int hourMinutesX = (canvasWidth - hourMinutesSecondsWidth) / 2 - mHourMinutesBounds.left;
        int hourMinutesTop = (canvasHeight - hourMinutesHeight - dateHeight) / 2;
        int hourMinutesY = hourMinutesTop + hourMinutesHeight + dateHeight;

        // Draw hour / minutes
        canvas.drawText(hourMinutesStr, hourMinutesX, hourMinutesY, mHourMinutesPaint);

        // Draw seconds
        int secondsX = hourMinutesX + hourMinutesWidth + secondsSpace - mSecondsBounds.left;
        canvas.drawText(secondsStr, secondsX, hourMinutesY, mSecondsPaint);

        // Draw AM/PM
        if (!mIs24HourFormat) {
            String amPmStr = getAmPm();
            int amPmY = hourMinutesTop + mSecondsBounds.height();
            canvas.drawText(amPmStr, secondsX, amPmY, mAmPmPaint);
        }

        // Draw the date
        canvas.drawText(dateStr, dateX, dateY, mDatePaint);
    }

    private void adjustSecondsAndAmPmSizes() {
        float size = mHourMinutesPaint.getTextSize();
        mSecondsPaint.setTextSize(size * SECONDS_SIZE_FACTOR);
        mAmPmPaint.setTextSize(size * AM_PM_SIZE_FACTOR);
    }

    private int computeFitTextSizeForDate(String dateStr, int wantedWidth) {
        int step = 6;
        int measure = (int) mDatePaint.measureText(dateStr);
        if (measure == wantedWidth) {
            // Perfect fit: do nothing
        } else if (measure < wantedWidth) {
            // Narrower: try a bigger font
            do {
                mDatePaint.setTextSize(mDatePaint.getTextSize() + step);
            } while ((measure = (int) mDatePaint.measureText(dateStr)) < wantedWidth);
            if (measure > wantedWidth) {
                // We went too far
                mDatePaint.setTextSize(mDatePaint.getTextSize() - step);
            }
        } else {
            // Wider: try a smaller font
            do {
                mDatePaint.setTextSize(mDatePaint.getTextSize() - step);
            } while (mDatePaint.measureText(dateStr) > wantedWidth);
        }
        return (int) mDatePaint.getTextSize();
    }


    /**
     * Handler to update the time periodically.
     */
    private Handler mUpdateTimeHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            invalidate();
            long timeMs = System.currentTimeMillis();
            long delayMs = UPDATE_RATE_MS - (timeMs % UPDATE_RATE_MS);
            sendEmptyMessageDelayed(0, delayMs);
        }
    };


    /*
     * Time / date formatting.
     */
    // region

    private String getHourMinutes() {
        int hour = mTime.hour;
        if (!mIs24HourFormat) hour = hour % 12;
        StringBuilder res = new StringBuilder(String.valueOf(hour));
        res.append(':');
        if (mTime.minute < 10) res.append('0');
        res.append(String.valueOf(mTime.minute));
        return res.toString();
    }

    private String getSeconds() {
        StringBuilder res = new StringBuilder();
        if (mTime.second < 10) res.append('0');
        res.append(String.valueOf(mTime.second));
        return res.toString();
    }

    private String getAmPm() {
        if (mTime.hour <= 11) return "AM";
        return "PM";
    }

    private String getDate() {
        if (mDateFormat == null) {
            mDateFormat = new SimpleDateFormat("EEEE', 'MMMM' 'd', 'yyyy");
        }
        return mDateFormat.format(new Date());
    }

    // endregion


    public Time getTime() {
        return mTime;
    }
}

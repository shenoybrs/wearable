/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String TAG = "MyWatchFace";


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextHourPaint;
        Paint mTextDayPaint;
        Paint mAmbientDayPaint;
        Paint mTextMaxPaint;
        Paint mTextMinPaint;
        Paint mBitmapPaint;
        boolean mAmbient;
        Time mTime;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        final BroadcastReceiver mWeatherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Local broadcast received!");
                mTemperatureMax = intent.getFloatExtra(WeatherUpdateService.PREF_MAX_TEMP, 0);
                mTemperatureMin = intent.getFloatExtra(WeatherUpdateService.PREF_MIN_TEMP, 0);
                int weatherId = intent.getIntExtra(WeatherUpdateService.PREF_WEATHER_ID, 0);
                mWeatherBitmap = BitmapFactory.decodeResource(getResources(), Utility.getArtResourceForWeatherCondition(weatherId));

                invalidate();
            }
        };

        float mXOffset;
        float mYOffset;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private float mTemperatureMax;
        private float mTemperatureMin;
        private Rect mHourBounds;
        private Rect mDayBounds;
        private Rect mMaxBounds;
        private Rect mMinBounds;

        private Bitmap mWeatherBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextHourPaint = new Paint();
            mTextHourPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextHourPaint.setTextAlign(Paint.Align.CENTER);
            mTextHourPaint.setTextSize(getResources().getDimension(R.dimen.hour_text));
            mTextHourPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mHourBounds = new Rect();

            mTextDayPaint = new Paint();
            mTextDayPaint = createTextPaint(resources.getColor(R.color.day_text));
            mTextDayPaint.setTextAlign(Paint.Align.CENTER);
            mTextDayPaint.setTextSize(getResources().getDimension(R.dimen.day_text));
            mTextDayPaint.setTypeface(Typeface.DEFAULT);
            mDayBounds = new Rect();

            mAmbientDayPaint = new Paint();
            mAmbientDayPaint = createTextPaint(Color.WHITE);
            mAmbientDayPaint.setTextAlign(Paint.Align.CENTER);
            mAmbientDayPaint.setTextSize(getResources().getDimension(R.dimen.day_text));
            mAmbientDayPaint.setTypeface(Typeface.DEFAULT);

            mTextMaxPaint = new Paint();
            mTextMaxPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextMaxPaint.setTextAlign(Paint.Align.CENTER);
            mTextMaxPaint.setTextSize(getResources().getDimension(R.dimen.temp_text));
            mTextMaxPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mMaxBounds = new Rect();

            mTextMinPaint = new Paint();
            mTextMinPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextMinPaint.setTextAlign(Paint.Align.CENTER);
            mTextMinPaint.setTextSize(getResources().getDimension(R.dimen.temp_text));
            mTextMinPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mMinBounds = new Rect();

            mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

            LocalBroadcastManager.getInstance(MyWatchFace.this).registerReceiver(
                    mWeatherReceiver, new IntentFilter("custom-event-name"));


        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
            Log.d(TAG, "onTimeClick()");
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextHourPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String hour = mAmbient ? String.format("%d:%02d", mTime.hour, mTime.minute) : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            mTextHourPaint.getTextBounds(hour, 0, hour.length(), mHourBounds);
            canvas.drawText(hour, bounds.centerX(), bounds.centerY() /2, mTextHourPaint);

            String date = mTime.month + "-" + mTime.monthDay + "-" + mTime.year;
            mTextDayPaint.getTextBounds(date, 0, date.length(), mDayBounds);
            canvas.drawText(date, bounds.centerX() , bounds.centerY() /2 +  mHourBounds.height() , mAmbient? mAmbientDayPaint: mTextDayPaint);

            if (! mAmbient) {
                canvas.drawBitmap(mWeatherBitmap, bounds.centerX() - mWeatherBitmap.getWidth() / 2, bounds.centerY() - mWeatherBitmap.getHeight() / 3, mBitmapPaint);
            }
            float y = bounds.height()*2/3;
            String max = (int)mTemperatureMax + "º";
            mTextMaxPaint.getTextBounds(max, 0 , max.length(), mMaxBounds);
            canvas.drawText(max, bounds.centerX() - mMaxBounds.width()/2 - mWeatherBitmap.getWidth()/2 , y - mMaxBounds.height(), mTextMaxPaint);

            String min = (int)mTemperatureMin + "º";
            mTextMinPaint.getTextBounds(min, 0 , min.length(), mMinBounds);
            canvas.drawText(min, bounds.centerX() + mMinBounds.width()/2 + mWeatherBitmap.getWidth()/2 , y - mMinBounds.height(), mTextMinPaint);


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}

package com.limelight.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualController;

/**
 * Shows how the on-screen controller will look with the current settings, on this
 * device's screen and on a half-folded foldable (controls below the hinge).
 */
public class OscPreviewPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {
    // Typical inner display of a book-style foldable held in landscape
    private static final float FOLD_PREVIEW_ASPECT = 2152f / 2076f;
    // Never preview narrower than 16:9 so the controls stay readable
    private static final float MIN_PHONE_ASPECT = 16f / 9f;
    // Keep the previews small enough to see both at once on tablets/unfolded foldables
    private static final int MAX_PHONE_PREVIEW_WIDTH_DP = 420;
    private static final int MAX_FOLD_PREVIEW_WIDTH_DP = 260;

    public OscPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_osc_preview);
        setSelectable(false);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDetached() {
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetached();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // Any of the OSC settings can change the preview, so just redraw it
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        PreviewFrame phone = (PreviewFrame) holder.findViewById(R.id.osc_preview_phone);
        PreviewFrame fold = (PreviewFrame) holder.findViewById(R.id.osc_preview_fold);

        DisplayMetrics screen = getContext().getResources().getDisplayMetrics();
        float phoneAspect = Math.max(MIN_PHONE_ASPECT,
                (float) Math.max(screen.widthPixels, screen.heightPixels) / Math.min(screen.widthPixels, screen.heightPixels));

        phone.setup(phoneAspect, false, Math.round(MAX_PHONE_PREVIEW_WIDTH_DP * screen.density));
        fold.setup(FOLD_PREVIEW_ASPECT, true, Math.round(MAX_FOLD_PREVIEW_WIDTH_DP * screen.density));
    }

    /**
     * A screen-shaped frame that draws a placeholder stream and lays out a
     * non-interactive on-screen controller on top of it.
     */
    public static class PreviewFrame extends FrameLayout {
        private float aspect = 16f / 9f;
        private boolean folded;
        private int maxWidth = Integer.MAX_VALUE;
        private int laidOutWidth = -1;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect hinge = new Rect();

        public PreviewFrame(Context context, AttributeSet attrs) {
            super(context, attrs);
            setWillNotDraw(false);
        }

        void setup(float aspect, boolean folded, int maxWidth) {
            this.aspect = aspect;
            this.folded = folded;
            this.maxWidth = maxWidth;
            // Force a rebuild with the latest settings
            laidOutWidth = -1;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), maxWidth);
            int height = Math.round(width / aspect);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            laidOutWidth = -1;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            if (width > 0 && height > 0 && width != laidOutWidth) {
                laidOutWidth = width;
                // Adding children during layout must be deferred to the next frame
                post(() -> buildControls(width, height));
            }
            super.onLayout(changed, left, top, right, bottom);
        }

        private void buildControls(int width, int height) {
            removeAllViews();

            Rect screenArea = new Rect(0, 0, width, height);
            Rect foldArea = null;
            if (folded) {
                int hingeHeight = Math.max(2, height / 100);
                hinge.set(0, height / 2 - hingeHeight / 2, width, height / 2 + hingeHeight / 2);
                foldArea = new Rect(0, hinge.bottom, width, height);
            }
            VirtualController.createPreview(this, getContext(), screenArea, foldArea);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();

            // Screen background
            paint.setShader(null);
            paint.setColor(Color.BLACK);
            canvas.drawRect(0, 0, width, height, paint);

            // Placeholder for the stream: full screen normally, above the hinge when folded
            int streamBottom = folded ? hinge.top : height;
            int streamHeight = Math.min(streamBottom, Math.round(width * 9f / 16f));
            int streamTop = (streamBottom - streamHeight) / 2;
            int streamWidth = Math.round(streamHeight * 16f / 9f);
            int streamLeft = (width - streamWidth) / 2;
            paint.setShader(new LinearGradient(0, streamTop, 0, streamTop + streamHeight,
                    Color.rgb(0x2B, 0x4A, 0x6F), Color.rgb(0x15, 0x22, 0x33), Shader.TileMode.CLAMP));
            canvas.drawRect(streamLeft, streamTop, streamLeft + streamWidth, streamTop + streamHeight, paint);
            paint.setShader(null);

            if (folded) {
                paint.setColor(Color.rgb(0x55, 0x55, 0x55));
                canvas.drawRect(hinge, paint);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // The preview is just a picture: keep the controls from reacting to touches
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }
    }
}

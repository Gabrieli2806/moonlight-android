package com.limelight.utils;

import android.app.Activity;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps the video visible when the soft keyboard (IME) covers part of the screen.
 *
 * When the IME opens, the stream is scaled down and repositioned so it fits
 * entirely in the space above the keyboard, letting the user keep watching
 * while typing. This is useful on any device, not only foldables.
 *
 * Fold-specific behaviour (Flex/tabletop layout, per-posture stream resolution)
 * is handled separately by {@link FoldableDeviceHelper} and the Flex Mode code
 * in {@code Game}, which reposition the stream via layout parameters. This
 * class deliberately only applies a scale + translation on top of whatever
 * layout those produce, so the two do not fight over the same view.
 */
public class FoldableHelper {
    private final Activity activity;
    private final View streamView;

    private int imeObstructionPx = 0;

    public FoldableHelper(Activity activity, View streamView) {
        this.activity = activity;
        this.streamView = streamView;

        ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(),
                (v, insets) -> {
                    onImeInsetsChanged(insets);
                    return insets;
                });
    }

    public void unregister() {
        ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(), null);
        reset();
    }

    private void onImeInsetsChanged(WindowInsetsCompat insets) {
        int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        if (imeHeight != imeObstructionPx) {
            imeObstructionPx = imeHeight;
            applyTransform();
        }
    }

    /** Height of the area the stream view is laid out within. */
    private int getParentHeight() {
        View parent = (View) streamView.getParent();
        int h = parent != null ? parent.getHeight() : 0;
        if (h == 0) {
            h = activity.getResources().getDisplayMetrics().heightPixels;
        }
        return h;
    }

    private int getParentWidth() {
        View parent = (View) streamView.getParent();
        int w = parent != null ? parent.getWidth() : 0;
        if (w == 0) {
            w = activity.getResources().getDisplayMetrics().widthPixels;
        }
        return w;
    }

    private void reset() {
        streamView.setScaleX(1.0f);
        streamView.setScaleY(1.0f);
        streamView.setTranslationY(0);
    }

    /**
     * Scales and repositions the stream so it fills the space above the keyboard
     * without leaving a letterbox gap.
     *
     * StreamContainer measures itself to exactly the video's aspect ratio, so the
     * view bounds ARE the video bounds. Scaling around the view's own center and
     * then translating that center onto the center of the free area avoids the
     * gap a top-pivot scale would leave, and lets the video grow when the free
     * area is taller than the video currently is.
     */
    private void applyTransform() {
        int obstruction = imeObstructionPx;

        if (obstruction <= 0) {
            reset();
            return;
        }

        int parentHeight = getParentHeight();
        int parentWidth = getParentWidth();
        int videoWidth = streamView.getWidth();
        int videoHeight = streamView.getHeight();

        if (parentHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            reset();
            return;
        }

        int availableHeight = parentHeight - obstruction;
        if (availableHeight <= 0) {
            reset();
            return;
        }

        // Largest uniform scale that keeps the whole video inside the free area
        float scale = Math.min((float) availableHeight / (float) videoHeight,
                (float) parentWidth / (float) videoWidth);

        streamView.setPivotX(videoWidth / 2.0f);
        streamView.setPivotY(videoHeight / 2.0f);
        streamView.setScaleX(scale);
        streamView.setScaleY(scale);

        // Pivoting at the center leaves the center where layout put it, so
        // translate it onto the middle of the free area
        float currentCenterY = streamView.getTop() + videoHeight / 2.0f;
        streamView.setTranslationY((availableHeight / 2.0f) - currentCenterY);
    }
}

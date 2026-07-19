package com.limelight.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

/**
 * Quality-of-life helper for foldable devices (e.g. Samsung Galaxy Z Fold/Flip).
 *
 * Provides two features, each individually toggleable:
 *  - Tabletop mode: when the device is half-opened with a horizontal hinge,
 *    the stream is scaled into the top half of the screen (above the hinge)
 *    so on-screen controls / virtual keyboard can live on the bottom half.
 *  - Keyboard avoidance: when the soft keyboard (IME) is visible, the stream
 *    is scaled to fit in the space above it so the video remains visible
 *    while typing.
 *
 * The stream view is transformed with a uniform scale (pivot at the top
 * center), which preserves aspect ratio and avoids resizing the Surface,
 * so the decoder is not disturbed.
 */
public class FoldableHelper {
    private final Activity activity;
    private final View streamView;
    private final boolean tabletopEnabled;
    private final boolean keyboardAvoidanceEnabled;

    private WindowInfoTrackerCallbackAdapter windowInfoTracker;

    private int hingeObstructionPx = 0;
    private int imeObstructionPx = 0;
    private boolean inTabletopMode = false;

    private final Consumer<WindowLayoutInfo> layoutStateConsumer = this::onWindowLayoutInfoChanged;

    public FoldableHelper(Activity activity, View streamView,
                          boolean tabletopEnabled, boolean keyboardAvoidanceEnabled) {
        this.activity = activity;
        this.streamView = streamView;
        this.tabletopEnabled = tabletopEnabled;
        this.keyboardAvoidanceEnabled = keyboardAvoidanceEnabled;

        if (tabletopEnabled) {
            windowInfoTracker = new WindowInfoTrackerCallbackAdapter(
                    WindowInfoTracker.getOrCreate(activity));
        }

        if (keyboardAvoidanceEnabled) {
            ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(),
                    (v, insets) -> {
                        onImeInsetsChanged(insets);
                        return insets;
                    });
        }
    }

    public void register() {
        if (windowInfoTracker != null) {
            windowInfoTracker.addWindowLayoutInfoListener(activity,
                    Runnable::run, layoutStateConsumer);
        }
    }

    public void unregister() {
        if (windowInfoTracker != null) {
            windowInfoTracker.removeWindowLayoutInfoListener(layoutStateConsumer);
        }
    }

    public boolean isInTabletopMode() {
        return inTabletopMode;
    }

    private void onWindowLayoutInfoChanged(@NonNull WindowLayoutInfo layoutInfo) {
        int newObstruction = 0;
        boolean tabletop = false;

        for (androidx.window.layout.DisplayFeature feature : layoutInfo.getDisplayFeatures()) {
            if (!(feature instanceof FoldingFeature)) {
                continue;
            }

            FoldingFeature fold = (FoldingFeature) feature;
            if (fold.getState() == FoldingFeature.State.HALF_OPENED &&
                    fold.getOrientation() == FoldingFeature.Orientation.HORIZONTAL) {
                // Tabletop posture: keep the video above the hinge
                Rect bounds = fold.getBounds();
                int viewHeight = getViewHeight();
                if (viewHeight > 0 && bounds.top > 0 && bounds.top < viewHeight) {
                    newObstruction = viewHeight - bounds.top;
                    tabletop = true;
                }
            }
        }

        if (newObstruction != hingeObstructionPx || tabletop != inTabletopMode) {
            hingeObstructionPx = newObstruction;
            inTabletopMode = tabletop;
            streamView.post(this::applyTransform);
        }
    }

    private void onImeInsetsChanged(WindowInsetsCompat insets) {
        int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        if (imeHeight != imeObstructionPx) {
            imeObstructionPx = imeHeight;
            applyTransform();
        }
    }

    private int getViewHeight() {
        int h = streamView.getHeight();
        if (h == 0) {
            h = activity.getResources().getDisplayMetrics().heightPixels;
        }
        return h;
    }

    private void applyTransform() {
        int obstruction = Math.max(hingeObstructionPx, imeObstructionPx);
        int viewHeight = getViewHeight();

        if (obstruction <= 0 || viewHeight <= 0 || obstruction >= viewHeight) {
            streamView.setScaleX(1.0f);
            streamView.setScaleY(1.0f);
            streamView.setTranslationY(0);
            return;
        }

        float scale = (float) (viewHeight - obstruction) / (float) viewHeight;
        streamView.setPivotX(streamView.getWidth() / 2.0f);
        streamView.setPivotY(0);
        streamView.setScaleX(scale);
        streamView.setScaleY(scale);
        streamView.setTranslationY(0);
    }
}

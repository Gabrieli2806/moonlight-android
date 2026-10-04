package com.limelight.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.StreamContainer;

/**
 * Keeps the stream visible on large/foldable screens by shrinking it into the area
 * above the soft keyboard, the on-screen full keyboard, or the hinge when the device
 * is half-folded (tabletop posture).
 */
public class FoldSplitHelper {
    // Don't split if the stream would be left with less than this fraction of the window height
    private static final float MIN_STREAM_HEIGHT_FRACTION = 0.25f;

    private static final float MIN_KEYBOARD_HEIGHT_FRACTION = 0.33f;
    private static final float MAX_KEYBOARD_HEIGHT_FRACTION = 0.5f;
    private static final int MIN_KEYBOARD_HEIGHT_DP = 220;

    private static final double LETTERBOXED_STREAM_MAX_ASPECT_RATIO = 1.5;
    private static final double ASSUMED_HOST_ASPECT_RATIO = 16.0 / 9.0;

    public interface Host {
        // Height in pixels of the on-screen full keyboard, or 0 if it's hidden
        int getOverlayKeyboardHeight();
        // Requests a new height for the on-screen full keyboard
        void setOverlayKeyboardHeight(int height);
        double getStreamAspectRatio();
        boolean isSplitSuspended();
    }

    private final Activity activity;
    private final StreamContainer streamContainer;
    private final View rootView;
    private final PreferenceConfiguration prefConfig;
    private final Host host;

    private final int[] location = new int[2];
    private int imeBottomInset = 0;
    private Rect hingeBounds = null;
    private int reservedHeight = 0;
    private boolean splitActive = false;
    private Integer originalGravity = null;

    private WindowInfoTrackerCallbackAdapter windowInfoTracker;

    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::update;

    private final Consumer<WindowLayoutInfo> layoutInfoListener = layoutInfo -> {
        Rect newHingeBounds = null;
        for (DisplayFeature feature : layoutInfo.getDisplayFeatures()) {
            if (feature instanceof FoldingFeature) {
                FoldingFeature fold = (FoldingFeature) feature;
                if (fold.getState() == FoldingFeature.State.HALF_OPENED &&
                        fold.getOrientation() == FoldingFeature.Orientation.HORIZONTAL) {
                    newHingeBounds = fold.getBounds();
                }
            }
        }
        hingeBounds = newHingeBounds;
        update();
    };

    public FoldSplitHelper(Activity activity, StreamContainer streamContainer, View rootView,
                           PreferenceConfiguration prefConfig, Host host) {
        this.activity = activity;
        this.streamContainer = streamContainer;
        this.rootView = rootView;
        this.prefConfig = prefConfig;
        this.host = host;
    }

    public void start() {
        if (!prefConfig.keyboardSplitScreen && !prefConfig.flexModeSplit && !prefConfig.splitCropLetterbox) {
            return;
        }

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

        if (prefConfig.keyboardSplitScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Avoid the window being panned by the IME. We'll make room for it ourselves.
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            View decorView = activity.getWindow().getDecorView();
            decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                imeBottomInset = insets.isVisible(WindowInsets.Type.ime()) ?
                        insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
                // The root view may be resized as a result of these insets, so wait for that
                rootView.post(this::update);
                return v.onApplyWindowInsets(insets);
            });
        }

        if (prefConfig.flexModeSplit) {
            try {
                windowInfoTracker = new WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(activity));
                windowInfoTracker.addWindowLayoutInfoListener(activity,
                        ContextCompat.getMainExecutor(activity), layoutInfoListener);
            } catch (Exception e) {
                LimeLog.warning("Unable to track folding state: " + e);
                windowInfoTracker = null;
            }
        }
    }

    public void stop() {
        if (rootView.getViewTreeObserver().isAlive()) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }
        if (windowInfoTracker != null) {
            windowInfoTracker.removeWindowLayoutInfoListener(layoutInfoListener);
            windowInfoTracker = null;
        }
    }

    private int computeReservedHeight() {
        int rootHeight = rootView.getHeight();
        if (rootHeight == 0 || host.isSplitSuspended()) {
            return 0;
        }

        rootView.getLocationInWindow(location);
        int rootBottom = location[1] + rootHeight;

        int reserved = 0;
        if (prefConfig.keyboardSplitScreen) {
            // Only count the part of the IME that actually covers our content
            int windowHeight = activity.getWindow().getDecorView().getHeight();
            reserved = Math.max(reserved, rootBottom - (windowHeight - imeBottomInset));
            reserved = Math.max(reserved, host.getOverlayKeyboardHeight());
        }
        if (prefConfig.flexModeSplit && hingeBounds != null) {
            reserved = Math.max(reserved, rootBottom - hingeBounds.top);
        }

        if (reserved <= 0 || rootHeight - reserved < rootHeight * MIN_STREAM_HEIGHT_FRACTION) {
            return 0;
        }
        return reserved;
    }

    // A near-square stream (e.g. native resolution on an unfolded foldable) usually comes
    // from a widescreen host display that the host letterboxes into the video. In that case
    // we lay out for the picture inside the video rather than the video itself.
    public double getContentAspectRatio() {
        double aspectRatio = host.getStreamAspectRatio();
        if (prefConfig.splitCropLetterbox && aspectRatio < LETTERBOXED_STREAM_MAX_ASPECT_RATIO) {
            return ASSUMED_HOST_ASPECT_RATIO;
        }
        return aspectRatio;
    }

    public boolean isCroppingLetterbox() {
        return getContentAspectRatio() != host.getStreamAspectRatio();
    }

    // Size the on-screen keyboard so a full-width stream fits exactly above it with no gap,
    // within limits that keep both the keys and the stream usable.
    private void fitOverlayKeyboard() {
        int rootWidth = rootView.getWidth();
        int rootHeight = rootView.getHeight();
        double aspectRatio = getContentAspectRatio();
        if (!prefConfig.keyboardSplitScreen || rootWidth == 0 || rootHeight == 0 || aspectRatio <= 0) {
            return;
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int maxHeight = (int) (rootHeight * MAX_KEYBOARD_HEIGHT_FRACTION);
        int minHeight = Math.min(maxHeight, Math.max(
                (int) (rootHeight * MIN_KEYBOARD_HEIGHT_FRACTION),
                (int) (MIN_KEYBOARD_HEIGHT_DP * density)));

        int idealHeight = rootHeight - (int) Math.round(rootWidth / aspectRatio);
        host.setOverlayKeyboardHeight(Math.max(minHeight, Math.min(idealHeight, maxHeight)));
    }

    private void update() {
        fitOverlayKeyboard();

        int reserved = computeReservedHeight();
        // In PiP there's nothing to make room for, but we still crop the host's letterboxing
        // so the small window only shows the actual picture.
        boolean active = reserved > 0 || (host.isSplitSuspended() && isCroppingLetterbox());
        if (reserved == reservedHeight && active == splitActive) {
            return;
        }
        reservedHeight = reserved;
        splitActive = active;

        LimeLog.info("Reserving " + reserved + "px below the stream");

        streamContainer.setSplitMode(active, host.getStreamAspectRatio(), getContentAspectRatio());

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) streamContainer.getLayoutParams();
        if (originalGravity == null) {
            originalGravity = params.gravity;
        }
        // FrameLayout positions a centered child at (parent - child) / 2 + topMargin - bottomMargin,
        // while sizing it with topMargin + bottomMargin removed. Splitting the reserved height
        // 1/4 top and 3/4 bottom shifts the child up by half of it, which centers it in the
        // area above the reserved space even when it's taller than that area.
        params.gravity = active ? Gravity.CENTER : originalGravity;
        params.topMargin = reserved / 4;
        params.bottomMargin = reserved - params.topMargin;
        streamContainer.setLayoutParams(params);
    }
}

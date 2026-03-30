package com.limelight.utils;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

import com.limelight.LimeLog;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Helper class for detecting foldable device states using Jetpack WindowManager.
 * Supports flex mode detection (half-opened) and fold/unfold state tracking.
 */
public class FoldableDeviceHelper {

    public enum FoldState {
        /** Device is flat/fully open or not a foldable */
        FLAT,
        /** Device is in flex mode (half-opened, like a laptop) */
        HALF_OPENED,
        /** No folding feature detected (non-foldable device) */
        UNKNOWN
    }

    public interface FoldStateListener {
        /**
         * Called when the fold state changes.
         * @param state the new fold state
         * @param hingeBounds the bounds of the hinge/fold line, or null if not applicable
         */
        void onFoldStateChanged(FoldState state, Rect hingeBounds);
    }

    private final Activity activity;
    private final WindowInfoTrackerCallbackAdapter windowInfoTracker;
    private final Consumer<WindowLayoutInfo> layoutInfoConsumer;
    private final Executor mainExecutor;
    private FoldStateListener listener;
    private FoldState currentState = FoldState.UNKNOWN;
    private Rect currentHingeBounds = null;
    private boolean isRegistered = false;

    public FoldableDeviceHelper(@NonNull Activity activity) {
        this.activity = activity;
        this.windowInfoTracker = new WindowInfoTrackerCallbackAdapter(
                WindowInfoTracker.getOrCreate(activity)
        );
        this.mainExecutor = new HandlerExecutor(new Handler(Looper.getMainLooper()));
        this.layoutInfoConsumer = this::onWindowLayoutInfoChanged;
    }

    public void setListener(FoldStateListener listener) {
        this.listener = listener;
    }

    /**
     * Start listening for fold state changes.
     * Should be called in onStart().
     */
    public void start() {
        if (!isRegistered) {
            windowInfoTracker.addWindowLayoutInfoListener(activity, mainExecutor, layoutInfoConsumer);
            isRegistered = true;
        }
    }

    /**
     * Stop listening for fold state changes.
     * Should be called in onStop().
     */
    public void stop() {
        if (isRegistered) {
            windowInfoTracker.removeWindowLayoutInfoListener(layoutInfoConsumer);
            isRegistered = false;
        }
    }

    public FoldState getCurrentState() {
        return currentState;
    }

    public Rect getCurrentHingeBounds() {
        return currentHingeBounds;
    }

    /**
     * Returns true if the device is currently in flex mode (half-opened).
     */
    public boolean isInFlexMode() {
        return currentState == FoldState.HALF_OPENED;
    }

    /**
     * Returns true if the hinge is horizontal (typical Z Fold flex mode in landscape).
     */
    public boolean isHingeHorizontal() {
        if (currentHingeBounds == null) return false;
        return currentHingeBounds.width() > currentHingeBounds.height();
    }

    private void onWindowLayoutInfoChanged(WindowLayoutInfo windowLayoutInfo) {
        List<DisplayFeature> features = windowLayoutInfo.getDisplayFeatures();

        FoldState newState = FoldState.FLAT;
        Rect newHingeBounds = null;

        for (DisplayFeature feature : features) {
            if (feature instanceof FoldingFeature) {
                FoldingFeature foldingFeature = (FoldingFeature) feature;
                newHingeBounds = foldingFeature.getBounds();

                if (foldingFeature.getState() == FoldingFeature.State.HALF_OPENED) {
                    newState = FoldState.HALF_OPENED;
                } else {
                    newState = FoldState.FLAT;
                }

                LimeLog.info("Foldable: state=" + foldingFeature.getState()
                        + " orientation=" + foldingFeature.getOrientation()
                        + " bounds=" + newHingeBounds);
                break; // Only handle first folding feature
            }
        }

        if (features.isEmpty()) {
            newState = FoldState.UNKNOWN;
        }

        boolean hingeChanged = currentHingeBounds == null ? newHingeBounds != null : !currentHingeBounds.equals(newHingeBounds);

        if (newState != currentState || hingeChanged) {
            FoldState oldState = currentState;
            currentState = newState;
            currentHingeBounds = newHingeBounds;

            LimeLog.info("Foldable: state changed " + oldState + " -> " + newState);

            if (listener != null) {
                listener.onFoldStateChanged(newState, newHingeBounds);
            }
        }
    }

    /**
     * Simple executor that posts Runnables to a Handler.
     */
    private static class HandlerExecutor implements Executor {
        private final Handler handler;

        HandlerExecutor(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            handler.post(command);
        }
    }
}

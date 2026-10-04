package com.limelight.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.window.area.WindowAreaCapability;
import androidx.window.area.WindowAreaController;
import androidx.window.area.WindowAreaInfo;
import androidx.window.area.WindowAreaPresentationSessionCallback;
import androidx.window.area.WindowAreaSessionPresenter;
import androidx.window.java.area.WindowAreaControllerCallbackAdapter;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.evdev.EvdevListener;

import java.util.List;

/**
 * Uses the rear (cover) display of an unfolded foldable as a trackpad for the stream,
 * operated with the fingers holding the back of the device.
 */
public class RearTouchpad implements WindowAreaPresentationSessionCallback {
    private static final float SENSITIVITY = 1.5f;
    private static final int TAP_TIMEOUT_MS = 250;
    private static final int CLICK_DURATION_MS = 50;
    private static final int SCROLL_STEP_DP = 24;

    private final Game game;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowAreaControllerCallbackAdapter controller;
    private WindowAreaInfo rearArea;
    private WindowAreaSessionPresenter session;
    private boolean starting = false;

    private int touchSlop;
    private float scrollStep;
    private float lastX, lastY;
    private float downX, downY;
    private float pendingX, pendingY, pendingScroll;
    private long downTime;
    private int maxPointerCount;
    private boolean moved;
    private boolean reanchor;

    private final Consumer<List<WindowAreaInfo>> areaListener = areas -> {
        WindowAreaInfo newRearArea = null;
        for (WindowAreaInfo area : areas) {
            if (WindowAreaInfo.Type.TYPE_REAR_FACING.equals(area.getType())) {
                newRearArea = area;
            }
        }
        rearArea = newRearArea;
    };

    public RearTouchpad(Game game) {
        this.game = game;
        try {
            controller = new WindowAreaControllerCallbackAdapter(WindowAreaController.getOrCreate());
            controller.addWindowAreaInfoListListener(ContextCompat.getMainExecutor(game), areaListener);
        } catch (Exception e) {
            LimeLog.warning("Rear display is not available: " + e);
            controller = null;
        }
    }

    public boolean isActive() {
        return session != null;
    }

    public void toggle() {
        if (session != null) {
            session.close();
            return;
        }
        if (starting) {
            return;
        }

        WindowAreaCapability capability = (controller != null && rearArea != null) ?
                rearArea.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA) : null;
        WindowAreaCapability.Status status = capability != null ? capability.getStatus() : null;
        LimeLog.info("Rear display status: " + status);

        if (WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE.equals(status)) {
            starting = true;
            controller.presentContentOnWindowArea(rearArea.getToken(), game,
                    ContextCompat.getMainExecutor(game), this);
        }
        else if (WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE.equals(status)) {
            Toast.makeText(game, R.string.rear_touchpad_unavailable, Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(game, R.string.rear_touchpad_unsupported, Toast.LENGTH_LONG).show();
        }
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            session.close();
            session = null;
        }
        if (controller != null) {
            controller.removeWindowAreaInfoListListener(areaListener);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onSessionStarted(@NonNull WindowAreaSessionPresenter session) {
        starting = false;
        this.session = session;

        Context context = session.getContext();
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrollStep = SCROLL_STEP_DP * context.getResources().getDisplayMetrics().density;

        FrameLayout layout = new FrameLayout(context);
        layout.setBackgroundColor(Color.BLACK);
        layout.setKeepScreenOn(true);

        TextView label = new TextView(context);
        label.setText(R.string.rear_touchpad_label);
        label.setTextColor(0x40FFFFFF);
        layout.addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        layout.setOnTouchListener((v, event) -> handleTouch(event));
        session.setContentView(layout);

        Toast.makeText(game, R.string.rear_touchpad_enabled, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSessionEnded(Throwable t) {
        starting = false;
        session = null;
        if (t != null) {
            LimeLog.warning("Rear display session failed: " + t);
            Toast.makeText(game, R.string.rear_touchpad_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onContainerVisibilityChanged(boolean isVisible) {
    }

    private boolean handleTouch(MotionEvent event) {
        if (!game.connected) {
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downTime = event.getEventTime();
                downX = event.getX();
                downY = event.getY();
                maxPointerCount = 1;
                moved = false;
                reanchor = true;
                pendingX = pendingY = pendingScroll = 0;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointerCount = Math.max(maxPointerCount, event.getPointerCount());
                reanchor = true;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                reanchor = true;
                break;

            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;

            case MotionEvent.ACTION_UP:
                if (!moved && event.getEventTime() - downTime < TAP_TIMEOUT_MS) {
                    click(maxPointerCount >= 2 ? EvdevListener.BUTTON_RIGHT : EvdevListener.BUTTON_LEFT);
                }
                break;
        }
        return true;
    }

    private void handleMove(MotionEvent event) {
        // Track the first pointer. Anchors are reset whenever the set of pointers changes
        // so that lifting or adding a finger doesn't make the cursor jump.
        float x = event.getX(0);
        float y = event.getY(0);
        if (reanchor || event.getActionIndex() != 0) {
            reanchor = false;
            lastX = x;
            lastY = y;
            return;
        }

        if (!moved && Math.hypot(x - downX, y - downY) > touchSlop) {
            moved = true;
        }

        // The rear display faces away from the user, so left and right are mirrored
        float deltaX = -(x - lastX);
        float deltaY = y - lastY;
        lastX = x;
        lastY = y;

        if (!moved) {
            return;
        }

        if (event.getPointerCount() >= 2) {
            pendingScroll += deltaY;
            int steps = (int) (pendingScroll / scrollStep);
            if (steps != 0) {
                pendingScroll -= steps * scrollStep;
                game.mouseVScroll((byte) steps);
            }
        }
        else {
            pendingX += deltaX * SENSITIVITY;
            pendingY += deltaY * SENSITIVITY;
            int moveX = (int) pendingX;
            int moveY = (int) pendingY;
            if (moveX != 0 || moveY != 0) {
                pendingX -= moveX;
                pendingY -= moveY;
                game.mouseMove(moveX, moveY);
            }
        }
    }

    private void click(int button) {
        game.mouseButtonEvent(button, true);
        handler.postDelayed(() -> {
            if (game.connected) {
                game.mouseButtonEvent(button, false);
            }
        }, CLICK_DURATION_MS);
    }
}

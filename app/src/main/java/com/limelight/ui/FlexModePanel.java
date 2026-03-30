package com.limelight.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.touch.TrackpadContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * Manages the Flex Mode bottom panel which provides a mouse touchpad area
 * and buttons for left/right click and keyboard toggle.
 */
public class FlexModePanel {

    private static final String PREFS_NAME = "flex_mode_prefs";
    private static final String PREF_KEYBOARD_TYPE = "keyboard_type";
    private static final int KEYBOARD_SOFT = 0;
    private static final int KEYBOARD_FULL = 1;

    public interface KeyboardToggleCallback {
        void onToggleSoftKeyboard();
        void onToggleFullKeyboard();
    }

    private final Activity activity;
    private final NvConnection conn;
    private final KeyboardToggleCallback keyboardCallback;
    private final View panelRoot;
    private final View touchpadArea;
    private final TextView btnLeftClick;
    private final ImageButton btnKeyboard;
    private final TextView btnRightClick;
    private final SharedPreferences prefs;

    private int preferredKeyboardType;

    private final TrackpadContext[] trackpadContexts = new TrackpadContext[2];

    public FlexModePanel(Activity activity, NvConnection conn, KeyboardToggleCallback keyboardCallback) {
        this.activity = activity;
        this.conn = conn;
        this.keyboardCallback = keyboardCallback;

        prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferredKeyboardType = prefs.getInt(PREF_KEYBOARD_TYPE, KEYBOARD_SOFT);

        panelRoot = activity.findViewById(R.id.flexModePanel);
        touchpadArea = activity.findViewById(R.id.flexTouchpadArea);
        btnLeftClick = activity.findViewById(R.id.flexBtnLeftClick);
        btnKeyboard = activity.findViewById(R.id.flexBtnKeyboard);
        btnRightClick = activity.findViewById(R.id.flexBtnRightClick);

        for (int i = 0; i < trackpadContexts.length; i++) {
            trackpadContexts[i] = new TrackpadContext(conn, i);
        }

        setupTouchpad();
        setupButtons();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchpad() {
        touchpadArea.setOnTouchListener((v, event) -> {
            if (conn == null) {
                return false;
            }
            return handleTouchpadEvent(event);
        });
    }

    private boolean handleTouchpadEvent(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        int eventX = (int) event.getX(actionIndex);
        int eventY = (int) event.getY(actionIndex);
        long eventTime = event.getEventTime();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (actionIndex < trackpadContexts.length) {
                    // Update pointer count for all contexts
                    for (TrackpadContext ctx : trackpadContexts) {
                        ctx.setPointerCount(event.getPointerCount());
                    }
                    trackpadContexts[actionIndex].touchDownEvent(
                            eventX, eventY, eventTime,
                            event.getActionMasked() == MotionEvent.ACTION_DOWN);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                // Handle move events for all active pointers
                for (int i = 0; i < event.getPointerCount() && i < trackpadContexts.length; i++) {
                    int x = (int) event.getX(i);
                    int y = (int) event.getY(i);
                    if (!trackpadContexts[i].isCancelled()) {
                        trackpadContexts[i].touchMoveEvent(x, y, eventTime);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (actionIndex < trackpadContexts.length) {
                    trackpadContexts[actionIndex].touchUpEvent(eventX, eventY, eventTime);
                    // Update pointer count
                    for (TrackpadContext ctx : trackpadContexts) {
                        ctx.setPointerCount(event.getPointerCount() - 1);
                    }
                }

                // Reset contexts on final finger up
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    for (int i = 0; i < trackpadContexts.length; i++) {
                        trackpadContexts[i] = new TrackpadContext(conn, i);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                for (TrackpadContext ctx : trackpadContexts) {
                    ctx.cancelTouch();
                }
                for (int i = 0; i < trackpadContexts.length; i++) {
                    trackpadContexts[i] = new TrackpadContext(conn, i);
                }
                return true;
            }
        }

        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupButtons() {
        // Left click - press on down, release on up
        btnLeftClick.setOnTouchListener((v, event) -> {
            if (conn == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                    v.setPressed(false);
                    return true;
            }
            return false;
        });

        // Right click - press on down, release on up
        btnRightClick.setOnTouchListener((v, event) -> {
            if (conn == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    v.setPressed(false);
                    return true;
            }
            return false;
        });

        // Keyboard button: tap = use preferred, long-press = choose type
        btnKeyboard.setOnClickListener(v -> {
            togglePreferredKeyboard();
        });

        btnKeyboard.setOnLongClickListener(v -> {
            showKeyboardTypePopup(v);
            return true;
        });
    }

    private void togglePreferredKeyboard() {
        if (keyboardCallback == null) return;

        if (preferredKeyboardType == KEYBOARD_FULL) {
            LimeLog.info("Flex Mode: toggling full keyboard (preferred)");
            keyboardCallback.onToggleFullKeyboard();
        } else {
            LimeLog.info("Flex Mode: toggling soft keyboard (preferred)");
            keyboardCallback.onToggleSoftKeyboard();
        }
    }

    private void showKeyboardTypePopup(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add(0, KEYBOARD_SOFT, 0, R.string.flex_keyboard_soft);
        popup.getMenu().add(0, KEYBOARD_FULL, 1, R.string.flex_keyboard_full);

        // Mark current preference
        popup.getMenu().findItem(preferredKeyboardType).setChecked(true);

        popup.setOnMenuItemClickListener(item -> {
            int type = item.getItemId();
            preferredKeyboardType = type;
            prefs.edit().putInt(PREF_KEYBOARD_TYPE, type).apply();
            LimeLog.info("Flex Mode: keyboard preference set to " + (type == KEYBOARD_FULL ? "full" : "soft"));

            // Immediately toggle the selected keyboard
            if (keyboardCallback != null) {
                if (type == KEYBOARD_FULL) {
                    keyboardCallback.onToggleFullKeyboard();
                } else {
                    keyboardCallback.onToggleSoftKeyboard();
                }
            }
            return true;
        });

        popup.show();
    }

    public void show(int bottomHalfHeight) {
        if (panelRoot == null) return;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panelRoot.getLayoutParams();
        params.height = bottomHalfHeight;
        params.gravity = android.view.Gravity.BOTTOM;
        panelRoot.setLayoutParams(params);
        panelRoot.setVisibility(View.VISIBLE);

        LimeLog.info("Flex Mode: panel shown, height=" + bottomHalfHeight);
    }

    public void hide() {
        if (panelRoot == null) return;

        panelRoot.setVisibility(View.GONE);
        LimeLog.info("Flex Mode: panel hidden");
    }

    public boolean isVisible() {
        return panelRoot != null && panelRoot.getVisibility() == View.VISIBLE;
    }
}

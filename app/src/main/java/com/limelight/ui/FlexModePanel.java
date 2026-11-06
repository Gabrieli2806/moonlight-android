package com.limelight.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.input.touch.TrackpadContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * Manages the Flex Mode bottom panel. Supports two modes:
 * 1. Touchpad-only: full touchpad with left/right click buttons and keyboard toggle
 * 2. Keyboard+Touchpad: 75% keyboard + 25% touchpad strip with mouse buttons
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
    private final SharedPreferences prefs;
    private int preferredKeyboardType;

    // Root panel
    private final View panelRoot;

    // Mode 1: Touchpad-only views
    private final View touchpadOnlyGroup;
    private final View touchpadArea;
    private final TextView btnLeftClick;
    private final ImageButton btnKeyboard;
    private final TextView btnRightClick;
    private final ImageView touchpadHint;

    // Mode 2: Keyboard+Touchpad views
    private final View keyboardTouchpadGroup;
    private final FrameLayout keyboardContainer;
    private final View kbTouchpadArea;
    private final TextView kbBtnLeftClick;
    private final TextView kbBtnRightClick;

    // Trackpad contexts for each mode
    private TrackpadContext[] touchpadContexts = new TrackpadContext[2];
    private TrackpadContext[] kbTouchpadContexts = new TrackpadContext[2];

    private boolean keyboardMode = false;

    public FlexModePanel(Activity activity, NvConnection conn, KeyboardToggleCallback keyboardCallback) {
        this.activity = activity;
        this.conn = conn;
        this.keyboardCallback = keyboardCallback;

        prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferredKeyboardType = prefs.getInt(PREF_KEYBOARD_TYPE, KEYBOARD_SOFT);

        // Root
        panelRoot = activity.findViewById(R.id.flexModePanel);

        // Mode 1 views
        touchpadOnlyGroup = activity.findViewById(R.id.flexTouchpadOnlyGroup);
        touchpadArea = activity.findViewById(R.id.flexTouchpadArea);
        btnLeftClick = activity.findViewById(R.id.flexBtnLeftClick);
        btnKeyboard = activity.findViewById(R.id.flexBtnKeyboard);
        btnRightClick = activity.findViewById(R.id.flexBtnRightClick);
        touchpadHint = activity.findViewById(R.id.flexTouchpadHint);

        // Mode 2 views
        keyboardTouchpadGroup = activity.findViewById(R.id.flexKeyboardTouchpadGroup);
        keyboardContainer = activity.findViewById(R.id.flexKeyboardContainer);
        kbTouchpadArea = activity.findViewById(R.id.flexKbTouchpadArea);
        kbBtnLeftClick = activity.findViewById(R.id.flexKbBtnLeftClick);
        kbBtnRightClick = activity.findViewById(R.id.flexKbBtnRightClick);

        resetTouchpadContexts();
        resetKbTouchpadContexts();

        setupTouchpad(touchpadArea, true);
        setupTouchpad(kbTouchpadArea, false);
        setupMouseButtons(btnLeftClick, btnRightClick);
        setupMouseButtons(kbBtnLeftClick, kbBtnRightClick);
        setupKeyboardButton();
    }

    private void resetTouchpadContexts() {
        for (int i = 0; i < touchpadContexts.length; i++) {
            touchpadContexts[i] = new TrackpadContext(conn, i);
        }
    }

    private void resetKbTouchpadContexts() {
        for (int i = 0; i < kbTouchpadContexts.length; i++) {
            kbTouchpadContexts[i] = new TrackpadContext(conn, i);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchpad(View area, boolean isPrimary) {
        area.setOnTouchListener((v, event) -> {
            if (conn == null) return false;
            TrackpadContext[] contexts = isPrimary ? touchpadContexts : kbTouchpadContexts;
            return handleTouchpadEvent(event, contexts, isPrimary);
        });
    }

    private boolean handleTouchpadEvent(MotionEvent event, TrackpadContext[] contexts, boolean isPrimary) {
        int actionIndex = event.getActionIndex();
        int eventX = (int) event.getX(actionIndex);
        int eventY = (int) event.getY(actionIndex);
        long eventTime = event.getEventTime();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (actionIndex < contexts.length) {
                    for (TrackpadContext ctx : contexts) {
                        ctx.setPointerCount(event.getPointerCount());
                    }
                    contexts[actionIndex].touchDownEvent(
                            eventX, eventY, eventTime,
                            event.getActionMasked() == MotionEvent.ACTION_DOWN);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount() && i < contexts.length; i++) {
                    int x = (int) event.getX(i);
                    int y = (int) event.getY(i);
                    if (!contexts[i].isCancelled()) {
                        contexts[i].touchMoveEvent(x, y, eventTime);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (actionIndex < contexts.length) {
                    contexts[actionIndex].touchUpEvent(eventX, eventY, eventTime);
                    for (TrackpadContext ctx : contexts) {
                        ctx.setPointerCount(event.getPointerCount() - 1);
                    }
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (isPrimary) resetTouchpadContexts();
                    else resetKbTouchpadContexts();
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                for (TrackpadContext ctx : contexts) {
                    ctx.cancelTouch();
                }
                if (isPrimary) resetTouchpadContexts();
                else resetKbTouchpadContexts();
                return true;
            }
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupMouseButtons(TextView leftBtn, TextView rightBtn) {
        leftBtn.setOnTouchListener((v, event) -> {
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

        rightBtn.setOnTouchListener((v, event) -> {
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
    }

    private void setupKeyboardButton() {
        btnKeyboard.setOnClickListener(v -> togglePreferredKeyboard());
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
        popup.getMenu().findItem(preferredKeyboardType).setChecked(true);

        popup.setOnMenuItemClickListener(item -> {
            int type = item.getItemId();
            preferredKeyboardType = type;
            prefs.edit().putInt(PREF_KEYBOARD_TYPE, type).apply();
            LimeLog.info("Flex Mode: keyboard preference set to " + (type == KEYBOARD_FULL ? "full" : "soft"));
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

    /**
     * Show the panel in touchpad-only mode.
     */
    public void show(int bottomHalfHeight) {
        show(bottomHalfHeight, false);
    }

    /**
     * Show the panel. If useKeyboardMode is true, shows keyboard+touchpad split.
     */
    public void show(int bottomHalfHeight, boolean useKeyboardMode) {
        if (panelRoot == null) return;

        this.keyboardMode = useKeyboardMode;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panelRoot.getLayoutParams();
        params.height = bottomHalfHeight;
        params.gravity = android.view.Gravity.BOTTOM;
        panelRoot.setLayoutParams(params);
        panelRoot.setVisibility(View.VISIBLE);

        if (useKeyboardMode) {
            touchpadOnlyGroup.setVisibility(View.GONE);
            keyboardTouchpadGroup.setVisibility(View.VISIBLE);
            if (touchpadHint != null) touchpadHint.setVisibility(View.GONE);
            LimeLog.info("Flex Mode: panel shown in keyboard+touchpad mode, height=" + bottomHalfHeight);
        } else {
            touchpadOnlyGroup.setVisibility(View.VISIBLE);
            keyboardTouchpadGroup.setVisibility(View.GONE);
            if (touchpadHint != null) touchpadHint.setVisibility(View.VISIBLE);
            LimeLog.info("Flex Mode: panel shown in touchpad-only mode, height=" + bottomHalfHeight);
        }
    }

    public void hide() {
        if (panelRoot == null) return;
        panelRoot.setVisibility(View.GONE);
        keyboardMode = false;
        LimeLog.info("Flex Mode: panel hidden");
    }

    public boolean isVisible() {
        return panelRoot != null && panelRoot.getVisibility() == View.VISIBLE;
    }

    public boolean isKeyboardMode() {
        return keyboardMode;
    }

    /**
     * Returns the FrameLayout container where the KeyBoardLayoutController should inject its keyboard.
     */
    public FrameLayout getKeyboardContainer() {
        return keyboardContainer;
    }
}

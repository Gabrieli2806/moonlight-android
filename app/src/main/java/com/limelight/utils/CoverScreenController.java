package com.limelight.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.window.area.WindowAreaCapability;
import androidx.window.area.WindowAreaController;
import androidx.window.area.WindowAreaInfo;
import androidx.window.area.WindowAreaSession;
import androidx.window.area.WindowAreaSessionPresenter;
import androidx.window.area.WindowAreaPresentationSessionCallback;
import androidx.window.java.area.WindowAreaControllerCallbackAdapter;

import com.limelight.Game;
import com.limelight.LimeLog;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Presents a customizable button pad on the cover (outer) screen of a foldable
 * device while the stream keeps running on the main inner screen.
 *
 * This uses the Jetpack WindowManager "dual-screen mode"
 * ({@code WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA}), which
 * allows an app to present content on the rear-facing display concurrently with
 * its main window. Support is device- and OS-dependent, so every entry point
 * checks the reported capability status and degrades gracefully when the
 * feature is unsupported or temporarily unavailable (e.g. while folded).
 */
@SuppressLint("RestrictedApi")
public class CoverScreenController implements WindowAreaPresentationSessionCallback {
    private final Activity activity;
    private final Executor executor;
    private final WindowAreaControllerCallbackAdapter controller;

    private WindowAreaInfo rearAreaInfo;
    private WindowAreaSessionPresenter activeSession;
    private boolean listening;

    // On-screen diagnostics: some devices render a presentation on the cover
    // screen but do not route its touchscreen while the main display is active.
    // Showing what the pad actually receives makes that case obvious.
    private TextView statusView;
    private int touchCount;

    private final Consumer<List<WindowAreaInfo>> infoListener = this::onWindowAreaInfosChanged;

    public CoverScreenController(Activity activity) {
        this.activity = activity;
        this.executor = ContextCompat.getMainExecutor(activity);
        this.controller = new WindowAreaControllerCallbackAdapter(WindowAreaController.getOrCreate());
    }

    public void register() {
        if (!listening) {
            listening = true;
            controller.addWindowAreaInfoListListener(executor, infoListener);
        }
    }

    public void unregister() {
        if (listening) {
            listening = false;
            controller.removeWindowAreaInfoListListener(infoListener);
        }
        closeSession();
    }

    private void onWindowAreaInfosChanged(List<WindowAreaInfo> infos) {
        rearAreaInfo = null;
        for (WindowAreaInfo info : infos) {
            if (info.getType() == WindowAreaInfo.Type.TYPE_REAR_FACING) {
                rearAreaInfo = info;
                break;
            }
        }
    }

    private WindowAreaCapability.Status getStatus() {
        if (rearAreaInfo == null) {
            return WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED;
        }

        WindowAreaCapability capability =
                rearAreaInfo.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA);
        if (capability == null) {
            return WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED;
        }

        return capability.getStatus();
    }

    /** True if this device reports support for presenting on the cover screen. */
    public boolean isSupported() {
        WindowAreaCapability.Status status = getStatus();
        return status != WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED;
    }

    /** True if a cover screen button pad is currently being presented. */
    public boolean isActive() {
        return activeSession != null;
    }

    /**
     * Shows or hides the cover screen button pad.
     *
     * @return a human-readable reason when the request could not be honored, or
     *         null when the toggle was performed.
     */
    @Nullable
    public String toggle() {
        if (activeSession != null) {
            closeSession();
            return null;
        }

        WindowAreaCapability.Status status = getStatus();

        if (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED) {
            return "This device does not support showing content on the cover screen";
        }
        if (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE) {
            return "Cover screen is unavailable right now (unfold the device and try again)";
        }
        if (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) {
            // Another session already owns the area
            return null;
        }

        controller.presentContentOnWindowArea(
                rearAreaInfo.getToken(), activity, executor, this);
        return null;
    }

    private void closeSession() {
        if (activeSession != null) {
            activeSession.close();
            activeSession = null;
        }
    }

    // --- WindowAreaPresentationSessionCallback ---

    @Override
    public void onSessionStarted(WindowAreaSessionPresenter session) {
        activeSession = session;
        session.setContentView(buildButtonPad(session.getContext()));
        LimeLog.info("Cover screen button pad session started");
    }

    @Override
    public void onSessionEnded(@Nullable Throwable t) {
        activeSession = null;
        if (t != null) {
            LimeLog.warning("Cover screen session ended with error: " + t.getMessage());
        }
    }

    @Override
    public void onContainerVisibilityChanged(boolean isVisible) {
        LimeLog.info("Cover screen container visible: " + isVisible);
    }

    // --- Button pad UI ---

    private View buildButtonPad(Context context) {
        List<CoverScreenButtonConfig.ButtonBinding> bindings =
                CoverScreenButtonConfig.load(activity);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(24, 24, 24, 24);

        statusView = new TextView(context);
        statusView.setText("Artemis — cover controls");
        statusView.setTextColor(Color.parseColor("#AAAAAA"));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, 0, 0, 16);
        root.addView(statusView);

        // Any touch anywhere on the pad bumps the counter, so a tap that lands
        // between buttons still proves input is reaching this display
        root.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchCount++;
                updateStatus("touch #" + touchCount);
            }
            return false;
        });

        // Two buttons per row, laid out with weights so they always get a real
        // size regardless of how many bindings the user configured
        final int columns = 2;
        LinearLayout row = null;

        for (int i = 0; i < bindings.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            }

            LinearLayout.LayoutParams cell =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            cell.setMargins(8, 8, 8, 8);
            row.addView(createButton(context, bindings.get(i)), cell);
        }

        // Keep the last row's buttons the same width when it is not full
        if (row != null && bindings.size() % columns != 0) {
            for (int i = bindings.size() % columns; i < columns; i++) {
                View filler = new View(context);
                row.addView(filler,
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            }
        }

        return root;
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createButton(Context context, CoverScreenButtonConfig.ButtonBinding binding) {
        Button button = new Button(context);
        button.setText(binding.label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    sendBinding(binding, true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    sendBinding(binding, false);
                    return true;
                default:
                    return false;
            }
        });

        return button;
    }

    private void updateStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
        }
    }

    private void sendBinding(CoverScreenButtonConfig.ButtonBinding binding, boolean down) {
        Game game = Game.instance;

        if (game == null) {
            updateStatus(binding.label + ": no active stream");
            return;
        }
        if (!game.connected) {
            updateStatus(binding.label + ": not connected");
            return;
        }

        if (binding.isMouse) {
            game.mouseButtonEvent(binding.keyCode, down);
            if (down) {
                updateStatus(binding.label + ": mouse " + binding.keyCode);
            }
        }
        else {
            String result = game.sendOffscreenKey(binding.keyCode, down);
            if (down) {
                updateStatus(binding.label + ": " + result);
            }
        }
    }
}

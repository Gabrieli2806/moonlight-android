package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;

import com.limelight.LimeLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stores the user's cover screen button layout.
 *
 * The layout is a small JSON array persisted in the default shared preferences
 * so it can be edited from the settings screen and read by
 * {@link CoverScreenController} when a session starts.
 */
public class CoverScreenButtonConfig {
    public static final String PREF_KEY = "cover_screen_buttons";
    public static final int MAX_BUTTONS = 6;

    public static class ButtonBinding {
        public final String label;
        public final int keyCode;
        public final boolean isMouse;

        public ButtonBinding(String label, int keyCode, boolean isMouse) {
            this.label = label;
            this.keyCode = keyCode;
            this.isMouse = isMouse;
        }
    }

    /** A reasonable starting layout so the pad is never empty on first use. */
    private static List<ButtonBinding> defaultBindings() {
        return new ArrayList<>(Arrays.asList(
                new ButtonBinding("Esc", KeyEvent.KEYCODE_ESCAPE, false),
                new ButtonBinding("Tab", KeyEvent.KEYCODE_TAB, false),
                new ButtonBinding("Space", KeyEvent.KEYCODE_SPACE, false),
                new ButtonBinding("Enter", KeyEvent.KEYCODE_ENTER, false),
                new ButtonBinding("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, false),
                new ButtonBinding("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, false)));
    }

    public static List<ButtonBinding> load(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String raw = prefs.getString(PREF_KEY, null);

        if (TextUtils.isEmpty(raw)) {
            return defaultBindings();
        }

        try {
            List<ButtonBinding> bindings = new ArrayList<>();
            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length() && i < MAX_BUTTONS; i++) {
                JSONObject obj = array.getJSONObject(i);
                bindings.add(new ButtonBinding(
                        obj.getString("label"),
                        obj.getInt("keyCode"),
                        obj.optBoolean("isMouse", false)));
            }

            return bindings.isEmpty() ? defaultBindings() : bindings;
        } catch (Exception e) {
            LimeLog.warning("Failed to parse cover screen button config: " + e.getMessage());
            return defaultBindings();
        }
    }

    public static void save(Context context, List<ButtonBinding> bindings) {
        try {
            JSONArray array = new JSONArray();

            for (ButtonBinding binding : bindings) {
                JSONObject obj = new JSONObject();
                obj.put("label", binding.label);
                obj.put("keyCode", binding.keyCode);
                obj.put("isMouse", binding.isMouse);
                array.put(obj);
            }

            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PREF_KEY, array.toString())
                    .apply();
        } catch (Exception e) {
            LimeLog.warning("Failed to save cover screen button config: " + e.getMessage());
        }
    }
}

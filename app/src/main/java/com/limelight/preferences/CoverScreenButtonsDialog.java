package com.limelight.preferences;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.appcompat.app.AlertDialog;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.utils.CoverScreenButtonConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings UI for editing the cover screen button pad.
 *
 * Shows one row per button slot; tapping a slot opens a key picker populated
 * from the same {@code config/keyboard.json} asset used by the on-screen
 * keyboard, so the available keys stay consistent across the app.
 */
public class CoverScreenButtonsDialog {

    private static class KeyChoice {
        final String name;
        final int keyCode;
        final boolean isMouse;

        KeyChoice(String name, int keyCode, boolean isMouse) {
            this.name = name;
            this.keyCode = keyCode;
            this.isMouse = isMouse;
        }
    }

    public static void show(Context context) {
        final List<CoverScreenButtonConfig.ButtonBinding> bindings =
                new ArrayList<>(CoverScreenButtonConfig.load(context));

        // Pad out to the maximum so every slot is editable
        while (bindings.size() < CoverScreenButtonConfig.MAX_BUTTONS) {
            bindings.add(new CoverScreenButtonConfig.ButtonBinding("—", 0, false));
        }

        showSlotList(context, bindings);
    }

    private static void showSlotList(Context context,
                                     List<CoverScreenButtonConfig.ButtonBinding> bindings) {
        String[] rows = new String[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            rows[i] = context.getString(R.string.cover_screen_slot_label, i + 1, bindings.get(i).label);
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.title_cover_screen_buttons)
                .setItems(rows, (dialog, which) -> showKeyPicker(context, bindings, which))
                .setPositiveButton(R.string.cover_screen_save, (dialog, which) -> {
                    // Drop empty slots before saving
                    List<CoverScreenButtonConfig.ButtonBinding> toSave = new ArrayList<>();
                    for (CoverScreenButtonConfig.ButtonBinding binding : bindings) {
                        if (binding.keyCode != 0) {
                            toSave.add(binding);
                        }
                    }
                    CoverScreenButtonConfig.save(context, toSave);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showKeyPicker(Context context,
                                      List<CoverScreenButtonConfig.ButtonBinding> bindings,
                                      int slotIndex) {
        final List<KeyChoice> choices = loadKeyChoices(context);

        String[] names = new String[choices.size() + 1];
        names[0] = context.getString(R.string.cover_screen_clear_slot);
        for (int i = 0; i < choices.size(); i++) {
            names[i + 1] = choices.get(i).name;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.cover_screen_pick_key)
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        bindings.set(slotIndex,
                                new CoverScreenButtonConfig.ButtonBinding("—", 0, false));
                    }
                    else {
                        KeyChoice choice = choices.get(which - 1);
                        bindings.set(slotIndex, new CoverScreenButtonConfig.ButtonBinding(
                                choice.name, choice.keyCode, choice.isMouse));
                    }

                    showSlotList(context, bindings);
                })
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> showSlotList(context, bindings))
                .show();
    }

    private static List<KeyChoice> loadKeyChoices(Context context) {
        List<KeyChoice> choices = new ArrayList<>();

        try {
            AssetManager assets = context.getAssets();
            InputStream is = assets.open("config/keyboard.json");
            int length = is.available();
            byte[] buffer = new byte[length];
            is.read(buffer);
            is.close();

            JSONObject data = new JSONObject(new String(buffer, "utf8")).getJSONObject("data");

            JSONArray keystrokes = data.optJSONArray("keystroke");
            if (keystrokes != null) {
                for (int i = 0; i < keystrokes.length(); i++) {
                    JSONObject key = keystrokes.getJSONObject(i);
                    choices.add(new KeyChoice(key.getString("name"), key.getInt("code"), false));
                }
            }

            JSONArray mouse = data.optJSONArray("mouse");
            if (mouse != null) {
                for (int i = 0; i < mouse.length(); i++) {
                    JSONObject key = mouse.getJSONObject(i);
                    int code = key.getInt("code");

                    // Codes 9-11 are trackpad-style elements that need drag handling,
                    // which a simple cover screen button can't provide
                    if (code > 3) {
                        continue;
                    }

                    choices.add(new KeyChoice(key.getString("name"), code, true));
                }
            }
        } catch (Exception e) {
            LimeLog.warning("Failed to load key list for cover screen buttons: " + e.getMessage());
        }

        return choices;
    }
}

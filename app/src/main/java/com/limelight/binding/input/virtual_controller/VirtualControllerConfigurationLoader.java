/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.DisplayMetrics;

import com.limelight.R;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

public class VirtualControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "OSC";
    // Separate profile for the layout used below the hinge of a half-folded device,
    // so customizing one layout doesn't break the other.
    public static final String OSC_FOLD_PREFERENCE = "OSC_FOLD";
    // Profile for the PlayStation style layout on regular screens
    public static final String OSC_PLAYSTATION_PREFERENCE = "OSC_PS";

    private static int getPercent(
            int percent,
            int total) {
        return (int) (((float) total / (float) 100) * (float) percent);
    }

    // The default controls are specified using a grid of 128*72 cells at 16:9
    private static int screenScale(int units, int height) {
        return (int) (((float) height / (float) 72) * (float) units);
    }

    private static DigitalPad createDigitalPad(
            final VirtualController controller,
            final Context context) {

        DigitalPad digitalPad = new DigitalPad(controller, context);
        digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();

                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    inputContext.inputMap |= ControllerPacket.UP_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;
                }

                controller.sendControllerInputContext(10, 0x22);
            }
        });

        return digitalPad;
    }

    private static DigitalButton createDigitalButton(
            final int elementId,
            final int keyShort,
            final int keyLong,
            final int layer,
            final String text,
            final int icon,
            final int iconPress,
            final VirtualController controller,
            final Context context) {
        DigitalButton button = new DigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.setIconPress(iconPress);
        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyShort;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyLong;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRelease() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~keyShort;
                inputContext.inputMap &= ~keyLong;

                controller.sendControllerInputContext();
            }
        });

        return button;
    }

    private static DigitalButton createLeftTrigger(
            final int layer,
            final String text,
            final int icon,
            final int iconPress,
            final VirtualController controller,
            final Context context) {
        LeftTrigger button = new LeftTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.setIconPress(iconPress);
        return button;
    }

    private static DigitalButton createRightTrigger(
            final int layer,
            final String text,
            final int icon,
            final int iconPress,
            final VirtualController controller,
            final Context context) {
        RightTrigger button = new RightTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        button.setIconPress(iconPress);
        return button;
    }

    private static AnalogStick createLeftStick(
            final VirtualController controller,
            final Context context) {
        return new LeftAnalogStick(controller, context);
    }

    private static AnalogStick createRightStick(
            final VirtualController controller,
            final Context context) {
        return new RightAnalogStick(controller, context);
    }

    private static AnalogStickFree createLeftStick2(
            final VirtualController controller,
            final Context context) {
        return new LeftAnalogStickFree(controller, context);
    }

    private static AnalogStickFree createRightStick2(
            final VirtualController controller,
            final Context context) {
        return new RightAnalogStickFree(controller, context);
    }


    private static final int TRIGGER_L_BASE_X = 1;
    private static final int TRIGGER_R_BASE_X = 92;
    private static final int TRIGGER_DISTANCE = 23;
    private static final int TRIGGER_BASE_Y = 31;
    private static final int TRIGGER_WIDTH = 12;
    private static final int TRIGGER_HEIGHT = 9;

    // Face buttons are defined based on the Y button (button number 9)
    private static final int BUTTON_BASE_X = 106;
    private static final int BUTTON_BASE_Y = 1;
    private static final int BUTTON_SIZE = 10;

    private static final int DPAD_BASE_X = 4;
    private static final int DPAD_BASE_Y = 41;
    private static final int DPAD_SIZE = 30;

    private static final int ANALOG_L_BASE_X = 6;
    private static final int ANALOG_L_BASE_Y = 4;
    private static final int ANALOG_R_BASE_X = 98;
    private static final int ANALOG_R_BASE_Y = 42;
    private static final int ANALOG_SIZE = 26;

    private static final int L3_R3_BASE_Y = 60;

    private static final int START_X = 83;
    private static final int BACK_X = 34;
    private static final int START_BACK_Y = 64;
    private static final int START_BACK_WIDTH = 12;
    private static final int START_BACK_HEIGHT = 7;

    // Make the Guide Menu be in the center of START and BACK menu
    private static final int GUIDE_X = START_X-BACK_X;
    private static final int GUIDE_Y = START_BACK_Y;

    public static void createDefaultLayout(final VirtualController controller, final Context context) {
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
        Rect area = controller.getLayoutArea();

        if (controller.isFoldLayout()) {
            // The classic layout doesn't fit below the hinge, so always use the
            // PlayStation arrangement there, with the selected glyphs.
            createPlayStationLayout(controller, context, area, LayoutSpec.FOLD, config.oscGlyphStyle);
            return;
        }
        if (config.oscGlyphStyle == PreferenceConfiguration.OSC_GLYPH_STYLE_PLAYSTATION) {
            createPlayStationLayout(controller, context, area, LayoutSpec.PHONE, config.oscGlyphStyle);
            return;
        }

        // Displace controls on the right by this amount of pixels to account for different aspect ratios
        int rightDisplacement = area.width() - area.height() * 16 / 9;

        int height = area.height();

        // NOTE: Some of these getPercent() expressions seem like they can be combined
        // into a single call. Due to floating point rounding, this isn't actually possible.

        if (!config.onlyL3R3)
        {
            controller.addElement(createDigitalPad(controller, context),
                    screenScale(DPAD_BASE_X, height),
                    screenScale(DPAD_BASE_Y, height),
                    screenScale(DPAD_SIZE, height),
                    screenScale(DPAD_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_A,
                    !config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    !config.flipFaceButtons ? "A" : "B", R.drawable.facebutton_a,R.drawable.facebutton_a_press, controller, context),
                    screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + 2 * BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_B,
                    config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    config.flipFaceButtons ? "A" : "B", R.drawable.facebutton_b,R.drawable.facebutton_b_press, controller, context),
                    screenScale(BUTTON_BASE_X + BUTTON_SIZE, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_X,
                    !config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    !config.flipFaceButtons ? "X" : "Y", R.drawable.facebutton_x,R.drawable.facebutton_x_press, controller, context),
                    screenScale(BUTTON_BASE_X - BUTTON_SIZE, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y + BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_Y,
                    config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    config.flipFaceButtons ? "X" : "Y", R.drawable.facebutton_y,R.drawable.facebutton_y_press, controller, context),
                    screenScale(BUTTON_BASE_X, height) + rightDisplacement,
                    screenScale(BUTTON_BASE_Y, height),
                    screenScale(BUTTON_SIZE, height),
                    screenScale(BUTTON_SIZE, height)
            );

            controller.addElement(createLeftTrigger(
                    1, "LT", R.drawable.facebutton_zl,R.drawable.facebutton_zl_press, controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createRightTrigger(
                    1, "RT", R.drawable.facebutton_zr,R.drawable.facebutton_zr_press, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_LB,
                    ControllerPacket.LB_FLAG, 0, 1, "LB", R.drawable.facebutton_l,R.drawable.facebutton_l_press, controller, context),
                    screenScale(TRIGGER_L_BASE_X + TRIGGER_DISTANCE, height),
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_RB,
                    ControllerPacket.RB_FLAG, 0, 1, "RB", R.drawable.facebutton_r,R.drawable.facebutton_r_press, controller, context),
                    screenScale(TRIGGER_R_BASE_X, height) + rightDisplacement,
                    screenScale(TRIGGER_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            if(config.enableNewAnalogStick){
                controller.addElement(createLeftStick2(controller, context),
                        screenScale(ANALOG_L_BASE_X, height),
                        screenScale(ANALOG_L_BASE_Y, height),
                        screenScale(ANALOG_SIZE, height),
                        screenScale(ANALOG_SIZE, height)
                );

                controller.addElement(createRightStick2(controller, context),
                        screenScale(ANALOG_R_BASE_X, height) + rightDisplacement,
                        screenScale(ANALOG_R_BASE_Y, height),
                        screenScale(ANALOG_SIZE, height),
                        screenScale(ANALOG_SIZE, height)
                );
            }else{
                controller.addElement(createLeftStick(controller, context),
                        screenScale(ANALOG_L_BASE_X, height),
                        screenScale(ANALOG_L_BASE_Y, height),
                        screenScale(ANALOG_SIZE, height),
                        screenScale(ANALOG_SIZE, height)
                );

                controller.addElement(createRightStick(controller, context),
                        screenScale(ANALOG_R_BASE_X, height) + rightDisplacement,
                        screenScale(ANALOG_R_BASE_Y, height),
                        screenScale(ANALOG_SIZE, height),
                        screenScale(ANALOG_SIZE, height)
                );
            }
            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_BACK,
                    ControllerPacket.BACK_FLAG, 0, 2, "BACK", R.drawable.facebutton_minus,R.drawable.facebutton_minus_press, controller, context),
                    screenScale(BACK_X, height),
                    screenScale(START_BACK_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_START,
                    ControllerPacket.PLAY_FLAG, 0, 3, "START", R.drawable.facebutton_plus,R.drawable.facebutton_plus_press, controller, context),
                    screenScale(START_X, height) + rightDisplacement,
                    screenScale(START_BACK_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_LSB,
                            ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", R.drawable.facebutton_l3,R.drawable.facebutton_l3_press, controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_RSB,
                            ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", R.drawable.facebutton_r3,R.drawable.facebutton_r3_press, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_TOUCHPAD,
                            ControllerPacket.TOUCHPAD_FLAG, 0, 1, "Trackpad", R.drawable.facebutton_touchpad_press,R.drawable.facebutton_touchpad, controller, context),
                    screenScale(50, height),
                    screenScale(50, height),
                    screenScale(20, height),
                    screenScale(12, height)
            );
        }
        else {
            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_LSB,
                    ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", -1, -1,controller, context),
                    screenScale(TRIGGER_L_BASE_X, height),
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_RSB,
                    ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", -1,-1, controller, context),
                    screenScale(TRIGGER_R_BASE_X + TRIGGER_DISTANCE, height) + rightDisplacement,
                    screenScale(L3_R3_BASE_Y, height),
                    screenScale(TRIGGER_WIDTH, height),
                    screenScale(TRIGGER_HEIGHT, height)
            );
        }


        if(config.showGuideButton){
            controller.addElement(createDigitalButton(VirtualControllerElement.EID_GDB,
                            ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 1, "GUIDE", -1, -1, controller, context),
                    screenScale(GUIDE_X, height)+ rightDisplacement,
                    screenScale(GUIDE_Y, height),
                    screenScale(START_BACK_WIDTH, height),
                    screenScale(START_BACK_HEIGHT, height)
            );
        }

        controller.setOpacity(config.oscOpacity);
    }

    /**
     * Positions (in units of the 128x72 grid) for the PlayStation style arrangement:
     * D-pad above the left stick, face buttons above the right stick, sticks towards the center.
     * Everything on the right side is mirrored from the right edge of the area.
     */
    private static final class LayoutSpec {
        float shoulderW, shoulderH, shoulderY, shoulderOuterX, shoulderInnerX;
        float dpadX, dpadY, dpadSize;
        float faceX, faceY, faceSize;
        float stickX, stickY, stickSize;
        float menuX, menuY, menuSize;
        float touchpadW, touchpadH, touchpadY;
        float clickX, clickY, clickW, clickH;
        float guideY, guideSize;

        // Area below the hinge of a half-folded device: wide and short, so keep things compact
        static final LayoutSpec FOLD = new LayoutSpec();
        // Regular phone in landscape: bigger targets, sticks reachable by the thumbs
        static final LayoutSpec PHONE = new LayoutSpec();

        static {
            FOLD.shoulderW = 16; FOLD.shoulderH = 7; FOLD.shoulderY = 2; FOLD.shoulderOuterX = 2; FOLD.shoulderInnerX = 20;
            FOLD.dpadX = 5; FOLD.dpadY = 12; FOLD.dpadSize = 28;
            FOLD.faceX = 106; FOLD.faceY = 11; FOLD.faceSize = 10;
            FOLD.stickX = 30; FOLD.stickY = 43; FOLD.stickSize = 27;
            FOLD.menuX = 40; FOLD.menuY = 13; FOLD.menuSize = 8;
            FOLD.touchpadW = 26; FOLD.touchpadH = 16; FOLD.touchpadY = 2;
            FOLD.clickX = 10; FOLD.clickY = 56; FOLD.clickW = 14; FOLD.clickH = 6;
            FOLD.guideY = 58; FOLD.guideSize = 8;

            PHONE.shoulderW = 16; PHONE.shoulderH = 8; PHONE.shoulderY = 2; PHONE.shoulderOuterX = 2; PHONE.shoulderInnerX = 20;
            PHONE.dpadX = 3; PHONE.dpadY = 14; PHONE.dpadSize = 31;
            PHONE.faceX = 101; PHONE.faceY = 12; PHONE.faceSize = 12;
            PHONE.stickX = 35; PHONE.stickY = 42; PHONE.stickSize = 28;
            PHONE.menuX = 40; PHONE.menuY = 16; PHONE.menuSize = 8;
            PHONE.touchpadW = 24; PHONE.touchpadH = 10; PHONE.touchpadY = 2;
            PHONE.clickX = 6; PHONE.clickY = 57; PHONE.clickW = 14; PHONE.clickH = 7;
            PHONE.guideY = 30; PHONE.guideSize = 8;
        }
    }

    /** Icons for one glyph style. Index 0 is the normal icon, 1 the pressed one. */
    private static final class GlyphSet {
        int[] a, b, x, y, l1, l2, r1, r2, l3, r3, share, options, guide;
        String aText, bText, xText, yText, l1Text, l2Text, r1Text, r2Text, shareText, optionsText, guideText;

        static GlyphSet forStyle(int style) {
            GlyphSet g = new GlyphSet();
            if (style == PreferenceConfiguration.OSC_GLYPH_STYLE_PLAYSTATION) {
                g.y = new int[]{R.drawable.facebutton_ps_triangle, R.drawable.facebutton_ps_triangle_press};
                g.x = new int[]{R.drawable.facebutton_ps_square, R.drawable.facebutton_ps_square_press};
                g.b = new int[]{R.drawable.facebutton_ps_circle, R.drawable.facebutton_ps_circle_press};
                g.a = new int[]{R.drawable.facebutton_ps_cross, R.drawable.facebutton_ps_cross_press};
                g.l1 = new int[]{R.drawable.facebutton_ps_l1, R.drawable.facebutton_ps_l1_press};
                g.l2 = new int[]{R.drawable.facebutton_ps_l2, R.drawable.facebutton_ps_l2_press};
                g.r1 = new int[]{R.drawable.facebutton_ps_r1, R.drawable.facebutton_ps_r1_press};
                g.r2 = new int[]{R.drawable.facebutton_ps_r2, R.drawable.facebutton_ps_r2_press};
                g.l3 = new int[]{R.drawable.facebutton_ps_l3, R.drawable.facebutton_ps_l3_press};
                g.r3 = new int[]{R.drawable.facebutton_ps_r3, R.drawable.facebutton_ps_r3_press};
                g.share = new int[]{R.drawable.facebutton_ps_share, R.drawable.facebutton_ps_share_press};
                g.options = new int[]{R.drawable.facebutton_ps_options, R.drawable.facebutton_ps_options_press};
                g.guide = new int[]{R.drawable.facebutton_ps_home, R.drawable.facebutton_ps_home_press};
                g.yText = "\u25B3"; g.xText = "\u25A1"; g.bText = "\u25CB"; g.aText = "\u2715";
                g.l1Text = "L1"; g.l2Text = "L2"; g.r1Text = "R1"; g.r2Text = "R2";
                g.shareText = "SHARE"; g.optionsText = "OPTIONS"; g.guideText = "PS";
            } else {
                g.y = new int[]{R.drawable.facebutton_y, R.drawable.facebutton_y_press};
                g.x = new int[]{R.drawable.facebutton_x, R.drawable.facebutton_x_press};
                g.b = new int[]{R.drawable.facebutton_b, R.drawable.facebutton_b_press};
                g.a = new int[]{R.drawable.facebutton_a, R.drawable.facebutton_a_press};
                g.l1 = new int[]{R.drawable.facebutton_l, R.drawable.facebutton_l_press};
                g.l2 = new int[]{R.drawable.facebutton_zl, R.drawable.facebutton_zl_press};
                g.r1 = new int[]{R.drawable.facebutton_r, R.drawable.facebutton_r_press};
                g.r2 = new int[]{R.drawable.facebutton_zr, R.drawable.facebutton_zr_press};
                g.l3 = new int[]{R.drawable.facebutton_l3, R.drawable.facebutton_l3_press};
                g.r3 = new int[]{R.drawable.facebutton_r3, R.drawable.facebutton_r3_press};
                g.share = new int[]{R.drawable.facebutton_minus, R.drawable.facebutton_minus_press};
                g.options = new int[]{R.drawable.facebutton_plus, R.drawable.facebutton_plus_press};
                g.guide = new int[]{-1, -1};
                g.yText = "Y"; g.xText = "X"; g.bText = "B"; g.aText = "A";
                g.l1Text = "LB"; g.l2Text = "LT"; g.r1Text = "RB"; g.r2Text = "RT";
                g.shareText = "BACK"; g.optionsText = "START"; g.guideText = "GUIDE";
            }
            return g;
        }
    }

    /**
     * PlayStation style arrangement fitted into the given area, anchored to its bottom.
     * Used for regular phones and for the area below the hinge of a half-folded device.
     */
    private static void createPlayStationLayout(final VirtualController controller, final Context context,
                                                final Rect area, final LayoutSpec spec, final int glyphStyle) {
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
        GlyphSet glyphs = GlyphSet.forStyle(glyphStyle);

        // Same 128x72 grid as the classic layout, scaled to fit the area
        final float unit = Math.min(area.height() / 72f, area.width() / 128f);
        final int left = area.left;
        final int right = area.right;
        final int top = area.bottom - Math.round(72 * unit);
        final int centerX = area.centerX();

        class Grid {
            int size(float units) { return Math.round(units * unit); }
            int left(float units) { return left + size(units); }
            // Mirror of an element that starts at x on the left side and is w wide
            int mirror(float x, float w) { return right - size(x + w); }
            int y(float units) { return top + size(units); }
            int center(float width) { return centerX - size(width) / 2; }
        }
        Grid g = new Grid();

        if (!config.onlyL3R3) {
            controller.addElement(createDigitalPad(controller, context),
                    g.left(spec.dpadX), g.y(spec.dpadY), g.size(spec.dpadSize), g.size(spec.dpadSize));

            // Face buttons: Y/triangle on top, X/square left, B/circle right, A/cross bottom.
            // faceX is the left edge of the top button counted from the right edge of the grid.
            final float fs = spec.faceSize;
            final float faceTopRight = 128 - spec.faceX - fs; // distance from right edge
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_Y,
                            config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                            glyphs.yText, glyphs.y[0], glyphs.y[1], controller, context),
                    g.mirror(faceTopRight, fs), g.y(spec.faceY), g.size(fs), g.size(fs));
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_X,
                            !config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                            glyphs.xText, glyphs.x[0], glyphs.x[1], controller, context),
                    g.mirror(faceTopRight + fs, fs), g.y(spec.faceY + fs), g.size(fs), g.size(fs));
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_B,
                            config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                            glyphs.bText, glyphs.b[0], glyphs.b[1], controller, context),
                    g.mirror(faceTopRight - fs, fs), g.y(spec.faceY + fs), g.size(fs), g.size(fs));
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_A,
                            !config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                            glyphs.aText, glyphs.a[0], glyphs.a[1], controller, context),
                    g.mirror(faceTopRight, fs), g.y(spec.faceY + 2 * fs), g.size(fs), g.size(fs));

            // Shoulders: L2 L1 ... R1 R2 along the top edge
            final float sw = spec.shoulderW, sh = spec.shoulderH;
            controller.addElement(createLeftTrigger(
                            1, glyphs.l2Text, glyphs.l2[0], glyphs.l2[1], controller, context),
                    g.left(spec.shoulderOuterX), g.y(spec.shoulderY), g.size(sw), g.size(sh));
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_LB,
                            ControllerPacket.LB_FLAG, 0, 1, glyphs.l1Text, glyphs.l1[0], glyphs.l1[1], controller, context),
                    g.left(spec.shoulderInnerX), g.y(spec.shoulderY), g.size(sw), g.size(sh));
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_RB,
                            ControllerPacket.RB_FLAG, 0, 1, glyphs.r1Text, glyphs.r1[0], glyphs.r1[1], controller, context),
                    g.mirror(spec.shoulderInnerX, sw), g.y(spec.shoulderY), g.size(sw), g.size(sh));
            controller.addElement(createRightTrigger(
                            1, glyphs.r2Text, glyphs.r2[0], glyphs.r2[1], controller, context),
                    g.mirror(spec.shoulderOuterX, sw), g.y(spec.shoulderY), g.size(sw), g.size(sh));

            // Sticks towards the center, below the D-pad and face buttons
            final float ss = spec.stickSize;
            if (config.enableNewAnalogStick) {
                controller.addElement(createLeftStick2(controller, context),
                        g.left(spec.stickX), g.y(spec.stickY), g.size(ss), g.size(ss));
                controller.addElement(createRightStick2(controller, context),
                        g.mirror(spec.stickX, ss), g.y(spec.stickY), g.size(ss), g.size(ss));
            } else {
                controller.addElement(createLeftStick(controller, context),
                        g.left(spec.stickX), g.y(spec.stickY), g.size(ss), g.size(ss));
                controller.addElement(createRightStick(controller, context),
                        g.mirror(spec.stickX, ss), g.y(spec.stickY), g.size(ss), g.size(ss));
            }

            // Share / Options on either side of the touchpad
            final float ms = spec.menuSize;
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_BACK,
                            ControllerPacket.BACK_FLAG, 0, 2, glyphs.shareText, glyphs.share[0], glyphs.share[1], controller, context),
                    g.left(spec.menuX), g.y(spec.menuY), g.size(ms), g.size(ms));
            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_START,
                            ControllerPacket.PLAY_FLAG, 0, 3, glyphs.optionsText, glyphs.options[0], glyphs.options[1], controller, context),
                    g.mirror(spec.menuX, ms), g.y(spec.menuY), g.size(ms), g.size(ms));

            controller.addElement(createDigitalButton(
                            VirtualControllerElement.EID_TOUCHPAD,
                            ControllerPacket.TOUCHPAD_FLAG, 0, 1, "Trackpad", R.drawable.facebutton_touchpad_press, R.drawable.facebutton_touchpad, controller, context),
                    g.center(spec.touchpadW), g.y(spec.touchpadY), g.size(spec.touchpadW), g.size(spec.touchpadH));
        }

        // L3 / R3 below the D-pad and face buttons
        controller.addElement(createDigitalButton(
                        VirtualControllerElement.EID_LSB,
                        ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", glyphs.l3[0], glyphs.l3[1], controller, context),
                g.left(spec.clickX), g.y(spec.clickY), g.size(spec.clickW), g.size(spec.clickH));
        controller.addElement(createDigitalButton(
                        VirtualControllerElement.EID_RSB,
                        ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", glyphs.r3[0], glyphs.r3[1], controller, context),
                g.mirror(spec.clickX, spec.clickW), g.y(spec.clickY), g.size(spec.clickW), g.size(spec.clickH));

        if (config.showGuideButton) {
            controller.addElement(createDigitalButton(VirtualControllerElement.EID_GDB,
                            ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 1, glyphs.guideText, glyphs.guide[0], glyphs.guide[1], controller, context),
                    g.center(spec.guideSize), g.y(spec.guideY), g.size(spec.guideSize), g.size(spec.guideSize));
        }

        controller.setOpacity(config.oscOpacity);
    }

    public static void saveProfile(final VirtualController controller,
                                   final Context context) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(controller.getProfileName(), Activity.MODE_PRIVATE).edit();

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final VirtualController controller, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(controller.getProfileName(), Activity.MODE_PRIVATE);

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}

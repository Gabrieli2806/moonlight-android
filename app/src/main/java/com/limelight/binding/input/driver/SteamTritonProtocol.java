package com.limelight.binding.input.driver;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Report formats of the Steam Controller (2026, "Triton"), shared by the USB and Bluetooth
 * drivers. Based on SDL's SDL_hidapi_steam_triton.c. All buffers here start with the report ID.
 */
final class SteamTritonProtocol {
    static final int VALVE_VID = 0x28DE;
    static final int TRITON_WIRED_PID = 0x1302;
    static final int TRITON_BLE_PID = 0x1303;

    // Input report IDs
    static final int ID_CONTROLLER_STATE = 0x42;
    static final int ID_CONTROLLER_STATE_BLE = 0x45;
    static final int ID_WIRELESS_STATUS_X = 0x46;
    static final int ID_CONTROLLER_STATE_TIMESTAMP = 0x47;
    static final int ID_WIRELESS_STATUS = 0x79;
    static final int WIRELESS_STATE_DISCONNECT = 1;
    static final int WIRELESS_STATE_CONNECT = 2;

    static final int ID_OUT_REPORT_HAPTIC_RUMBLE = 0x80;
    static final int FEATURE_REPORT_ID = 0x01;
    static final int FEATURE_REPORT_BYTES = 64;
    static final int RUMBLE_REPORT_BYTES = 10;

    // The firmware watchdog turns lizard (keyboard/mouse) mode back on, so keep refreshing it
    static final long SETTINGS_INTERVAL_MS = 1000;
    // The rumble motors stop on their own after ~50 ms without a new command
    static final long RUMBLE_RESEND_MS = 40;

    // Button bits
    private static final int BTN_A = 0x00000001;
    private static final int BTN_B = 0x00000002;
    private static final int BTN_X = 0x00000004;
    private static final int BTN_Y = 0x00000008;
    private static final int BTN_QAM = 0x00000010;
    private static final int BTN_R3 = 0x00000020;
    private static final int BTN_VIEW = 0x00000040;
    private static final int BTN_R4 = 0x00000080;
    private static final int BTN_R5 = 0x00000100;
    private static final int BTN_RB = 0x00000200;
    private static final int BTN_DPAD_DOWN = 0x00000400;
    private static final int BTN_DPAD_RIGHT = 0x00000800;
    private static final int BTN_DPAD_LEFT = 0x00001000;
    private static final int BTN_DPAD_UP = 0x00002000;
    private static final int BTN_MENU = 0x00004000;
    private static final int BTN_L3 = 0x00008000;
    private static final int BTN_STEAM = 0x00010000;
    private static final int BTN_L4 = 0x00020000;
    private static final int BTN_L5 = 0x00040000;
    private static final int BTN_LB = 0x00080000;
    private static final int BTN_RIGHT_PAD_CLICK = 0x00400000;
    private static final int BTN_LEFT_PAD_CLICK = 0x04000000;

    static final int SUPPORTED_BUTTON_FLAGS = ControllerPacket.A_FLAG | ControllerPacket.B_FLAG |
            ControllerPacket.X_FLAG | ControllerPacket.Y_FLAG |
            ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG |
            ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG |
            ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG |
            ControllerPacket.PLAY_FLAG | ControllerPacket.BACK_FLAG |
            ControllerPacket.LS_CLK_FLAG | ControllerPacket.RS_CLK_FLAG |
            ControllerPacket.SPECIAL_BUTTON_FLAG |
            ControllerPacket.PADDLE1_FLAG | ControllerPacket.PADDLE2_FLAG |
            ControllerPacket.PADDLE3_FLAG | ControllerPacket.PADDLE4_FLAG |
            ControllerPacket.TOUCHPAD_FLAG | ControllerPacket.MISC_FLAG;

    private SteamTritonProtocol() {}

    /** Reported to the host as an Xbox pad, so Sunshine creates a virtual Xbox 360 controller. */
    static void configure(AbstractController controller) {
        controller.type = MoonBridge.LI_CTYPE_XBOX;
        controller.capabilities = MoonBridge.LI_CCAP_ANALOG_TRIGGERS | MoonBridge.LI_CCAP_RUMBLE;
        controller.supportedButtonFlags = SUPPORTED_BUTTON_FLAGS;
    }

    static boolean isStateReport(int reportId) {
        return reportId == ID_CONTROLLER_STATE || reportId == ID_CONTROLLER_STATE_BLE ||
                reportId == ID_CONTROLLER_STATE_TIMESTAMP;
    }

    /**
     * Reads buttons, triggers and sticks into {@code c}. These fields sit at the same offsets in
     * all three state reports. Returns false if the report is too short.
     */
    static boolean parseState(AbstractController c, byte[] data, int length) {
        if (length < 18) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        int b = buf.getInt(2);
        short trigL = buf.getShort(6);
        short trigR = buf.getShort(8);
        short lx = buf.getShort(10);
        short ly = buf.getShort(12);
        short rx = buf.getShort(14);
        short ry = buf.getShort(16);

        int flags = 0;
        if ((b & BTN_A) != 0) flags |= ControllerPacket.A_FLAG;
        if ((b & BTN_B) != 0) flags |= ControllerPacket.B_FLAG;
        if ((b & BTN_X) != 0) flags |= ControllerPacket.X_FLAG;
        if ((b & BTN_Y) != 0) flags |= ControllerPacket.Y_FLAG;
        if ((b & BTN_DPAD_UP) != 0) flags |= ControllerPacket.UP_FLAG;
        if ((b & BTN_DPAD_DOWN) != 0) flags |= ControllerPacket.DOWN_FLAG;
        if ((b & BTN_DPAD_LEFT) != 0) flags |= ControllerPacket.LEFT_FLAG;
        if ((b & BTN_DPAD_RIGHT) != 0) flags |= ControllerPacket.RIGHT_FLAG;
        if ((b & BTN_LB) != 0) flags |= ControllerPacket.LB_FLAG;
        if ((b & BTN_RB) != 0) flags |= ControllerPacket.RB_FLAG;
        // SDL maps VIEW to Start and MENU to Back on this controller
        if ((b & BTN_VIEW) != 0) flags |= ControllerPacket.PLAY_FLAG;
        if ((b & BTN_MENU) != 0) flags |= ControllerPacket.BACK_FLAG;
        if ((b & BTN_L3) != 0) flags |= ControllerPacket.LS_CLK_FLAG;
        if ((b & BTN_R3) != 0) flags |= ControllerPacket.RS_CLK_FLAG;
        if ((b & BTN_STEAM) != 0) flags |= ControllerPacket.SPECIAL_BUTTON_FLAG;
        if ((b & BTN_QAM) != 0) flags |= ControllerPacket.MISC_FLAG;
        // Same paddle order as SDL: upper right, upper left, lower right, lower left
        if ((b & BTN_R4) != 0) flags |= ControllerPacket.PADDLE1_FLAG;
        if ((b & BTN_L4) != 0) flags |= ControllerPacket.PADDLE2_FLAG;
        if ((b & BTN_R5) != 0) flags |= ControllerPacket.PADDLE3_FLAG;
        if ((b & BTN_L5) != 0) flags |= ControllerPacket.PADDLE4_FLAG;
        if ((b & (BTN_LEFT_PAD_CLICK | BTN_RIGHT_PAD_CLICK)) != 0) flags |= ControllerPacket.TOUCHPAD_FLAG;
        c.buttonFlags = flags;

        // Triggers are 0..32767
        c.leftTrigger = Math.max(0, trigL) / 32767f;
        c.rightTrigger = Math.max(0, trigR) / 32767f;

        // Raw Y is positive up; the listener expects Android's positive-down convention
        c.leftStickX = lx / 32768f;
        c.leftStickY = -ly / 32768f;
        c.rightStickX = rx / 32768f;
        c.rightStickY = -ry / 32768f;
        return true;
    }

    static void clearState(AbstractController c) {
        c.buttonFlags = 0;
        c.leftTrigger = c.rightTrigger = 0;
        c.leftStickX = c.leftStickY = c.rightStickX = c.rightStickY = 0;
    }

    /** Feature report 1, ID_SET_SETTINGS_VALUES (0x87): SETTING_LIZARD_MODE (9) = OFF (0). */
    static byte[] lizardOffReport() {
        byte[] report = new byte[FEATURE_REPORT_BYTES];
        report[0] = FEATURE_REPORT_ID;
        report[1] = (byte) 0x87;
        report[2] = 0x03;
        report[3] = 0x09;
        return report;
    }

    /** Output report 0x80 (MsgHapticRumble). */
    static byte[] rumbleReport(short lowFreqMotor, short highFreqMotor) {
        byte[] report = new byte[RUMBLE_REPORT_BYTES];
        ByteBuffer bb = ByteBuffer.wrap(report).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) ID_OUT_REPORT_HAPTIC_RUMBLE);
        bb.put((byte) 0);              // type
        bb.putShort((short) 0);        // intensity
        bb.putShort(lowFreqMotor);     // left motor speed
        bb.put((byte) 0);              // left gain
        bb.putShort(highFreqMotor);    // right motor speed
        bb.put((byte) 0);              // right gain
        return report;
    }
}

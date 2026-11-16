package com.limelight.binding.input.driver;

import static com.limelight.binding.input.driver.SteamTritonProtocol.*;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;

/**
 * Steam Controller (2026, "Triton") over USB, either wired (28de:1302) or as one slot of the
 * wireless puck (28de:1304/1305, interfaces 2..5). See {@link SteamTritonProtocol}.
 *
 * The controller is reported to the host as an Xbox controller, so Sunshine creates a virtual
 * Xbox 360 pad. The Moonlight protocol has no Steam Controller type, so the host cannot see
 * the exact device.
 */
public class SteamTritonController extends AbstractController {
    static final int VALVE_VID = SteamTritonProtocol.VALVE_VID;

    private final UsbDeviceConnection connection;
    private final UsbInterface iface;
    private final boolean wireless;
    private final Object outputLock = new Object();
    private UsbEndpoint inEndpt, outEndpt;
    private Thread inputThread, outputThread;
    private volatile boolean stopped;
    private volatile boolean connected;
    private volatile short rumbleLow, rumbleHigh;
    private volatile boolean rumbleDirty;
    Runnable ioErrorHandler;

    public static boolean canClaimDevice(UsbDevice device) {
        return device.getVendorId() == VALVE_VID && device.getProductId() == TRITON_WIRED_PID &&
                findWiredInterface(device) != null;
    }

    static UsbInterface findWiredInterface(UsbDevice device) {
        UsbInterface fallback = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface candidate = device.getInterface(i);
            if (candidate.getInterfaceClass() != UsbConstants.USB_CLASS_HID || !hasInEndpoint(candidate)) {
                continue;
            }
            // Boot keyboard (1) / mouse (2) interfaces are the "lizard mode" emulation
            if (candidate.getInterfaceProtocol() != 0) {
                continue;
            }
            if (candidate.getId() == 0) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    static boolean hasInEndpoint(UsbInterface iface) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            if (iface.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_IN) {
                return true;
            }
        }
        return false;
    }

    SteamTritonController(UsbDevice device, UsbDeviceConnection connection, UsbInterface iface,
                          boolean wireless, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.connection = connection;
        this.iface = iface;
        this.wireless = wireless;
        SteamTritonProtocol.configure(this);
    }

    public SteamTritonController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        this(device, connection, findWiredInterface(device), false, deviceId, listener);
    }

    @Override
    public boolean start() {
        if (iface == null || !connection.claimInterface(iface, true)) {
            LimeLog.warning("SteamTriton: failed to claim interface");
            return false;
        }

        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                inEndpt = endpt;
            } else {
                outEndpt = endpt;
            }
        }
        if (inEndpt == null) {
            LimeLog.warning("SteamTriton: missing input endpoint");
            return false;
        }

        // A wired controller is always present. A puck slot only appears once a controller
        // connects to it.
        if (!wireless) {
            sendSettings();
            setConnected(true);
        }

        inputThread = new Thread(this::inputLoop, "SteamTriton-in-" + iface.getId());
        outputThread = new Thread(this::outputLoop, "SteamTriton-out-" + iface.getId());
        inputThread.start();
        outputThread.start();
        return true;
    }

    private void inputLoop() {
        byte[] buffer = new byte[64];
        int errors = 0;
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            long startMs = SystemClock.uptimeMillis();
            int res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 1000);
            if (stopped) {
                break;
            }
            if (res <= 0) {
                // Android reports a timeout and an I/O error the same way, so only count
                // failures that come back much faster than the timeout.
                if (SystemClock.uptimeMillis() - startMs < 500 && ++errors > 5) {
                    LimeLog.warning("SteamTriton: device I/O error");
                    if (ioErrorHandler != null) {
                        ioErrorHandler.run();
                    } else if (wireless) {
                        setConnected(false);
                    } else {
                        stop();
                    }
                    break;
                }
                continue;
            }
            errors = 0;
            handleReport(buffer, res);
        }
    }

    private void outputLoop() {
        long lastSettingsMs = 0;
        while (!stopped) {
            long now = SystemClock.uptimeMillis();
            if (connected && now - lastSettingsMs >= SETTINGS_INTERVAL_MS) {
                sendSettings();
                lastSettingsMs = now;
            }
            if (connected && (rumbleDirty || rumbleLow != 0 || rumbleHigh != 0)) {
                rumbleDirty = false;
                sendRumble(rumbleLow, rumbleHigh);
            }
            try {
                Thread.sleep(RUMBLE_RESEND_MS);
            } catch (InterruptedException e) {
                // rumble() wakes us up early so new values go out right away
                if (stopped) {
                    return;
                }
            }
        }
    }

    private void handleReport(byte[] data, int length) {
        int reportId = data[0] & 0xFF;
        if (reportId == ID_WIRELESS_STATUS || reportId == ID_WIRELESS_STATUS_X) {
            if (length >= 2) {
                int state = data[1] & 0xFF;
                if (state == WIRELESS_STATE_CONNECT) {
                    sendSettings();
                    setConnected(true);
                } else if (state == WIRELESS_STATE_DISCONNECT) {
                    setConnected(false);
                }
            }
        } else if (isStateReport(reportId)) {
            if (!connected) {
                // A controller that was already paired when the puck was opened
                sendSettings();
                setConnected(true);
            }
            if (parseState(this, data, length)) {
                reportInput();
            }
        }
        // Battery status and anything else are ignored
    }

    private void setConnected(boolean nowConnected) {
        synchronized (this) {
            if (connected == nowConnected) {
                return;
            }
            connected = nowConnected;
        }
        if (nowConnected) {
            LimeLog.info("SteamTriton: controller connected on interface " + iface.getId());
            notifyDeviceAdded();
        } else {
            LimeLog.info("SteamTriton: controller disconnected on interface " + iface.getId());
            clearState(this);
            rumbleLow = rumbleHigh = 0;
            notifyDeviceRemoved();
        }
    }

    private void sendSettings() {
        byte[] report = lizardOffReport();
        synchronized (outputLock) {
            // HID SET_REPORT (Feature)
            int res = connection.controlTransfer(0x21, 0x09, (3 << 8) | FEATURE_REPORT_ID, iface.getId(),
                    report, report.length, 100);
            if (res < 0) {
                LimeLog.warning("SteamTriton: failed to disable lizard mode: " + res);
            }
        }
    }

    private void sendRumble(short low, short high) {
        byte[] report = rumbleReport(low, high);
        synchronized (outputLock) {
            if (outEndpt != null) {
                connection.bulkTransfer(outEndpt, report, report.length, 100);
            } else {
                // HID SET_REPORT (Output)
                connection.controlTransfer(0x21, 0x09, (2 << 8) | ID_OUT_REPORT_HAPTIC_RUMBLE,
                        iface.getId(), report, report.length, 100);
            }
        }
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        rumbleLow = lowFreqMotor;
        rumbleHigh = highFreqMotor;
        rumbleDirty = true;
        Thread t = outputThread;
        if (t != null) {
            t.interrupt();
        }
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // No trigger motors
    }

    /** Stops the reader without closing the shared connection (used by the puck). */
    void stopSlot() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (connected) {
            sendRumble((short) 0, (short) 0);
        }
        if (inputThread != null) {
            inputThread.interrupt();
        }
        if (outputThread != null) {
            outputThread.interrupt();
        }
        connection.releaseInterface(iface);
        setConnected(false);
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        boolean wasConnected = connected;
        stopSlot();
        connection.close();
        if (!wasConnected) {
            // Make sure the service forgets about us even if we never reported arrival
            notifyDeviceRemoved();
        }
    }
}

package com.limelight.binding.input.driver;

import static com.limelight.binding.input.driver.SteamTritonProtocol.*;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import com.limelight.LimeLog;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;

/**
 * Steam Controller (2026, "Triton") over Bluetooth LE. The controller must be paired in Android's
 * Bluetooth settings first. We open our own GATT connection next to Android's HID connection and
 * talk to Valve's vendor service, the same way SDL's HIDDeviceBLESteamController does.
 *
 * All GATT work happens on {@link #handler}; Android only allows one outstanding GATT
 * operation, so writes go through a small queue.
 */
@SuppressLint("MissingPermission") // The manager only creates us after BLUETOOTH_CONNECT is granted
public class SteamTritonBleController extends AbstractController {
    private static final UUID SERVICE = UUID.fromString("100F6C32-1735-4313-B402-38567131E5F3");
    private static final UUID REPORT_CHARACTERISTIC = UUID.fromString("100F6C34-1735-4313-B402-38567131E5F3");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // Valve characteristics are 100F6Cxx-... where xx = report ID + 0x35
    private static final int CHARACTERISTIC_REPORT_OFFSET = 0x35;
    private static final long VALVE_UUID_PREFIX = 0x100F6C00L;

    private static final int MTU = 517; // Triton needs a large MTU; SDL uses exactly this value
    private static final long MTU_TIMEOUT_MS = 2000;
    private static final long OPERATION_TIMEOUT_MS = 1000;

    private final Context context;
    private final BluetoothDevice device;
    private final Handler handler;

    // Only touched on the handler thread
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic inputChr, reportChr, rumbleChr;
    private int inputReportId;
    private boolean ready, stopped, rumbleQueued;
    private long lastSettingsMs;
    private final ArrayDeque<Op> operations = new ArrayDeque<>();
    private Op currentOp;
    private final Runnable setUpServiceRunnable = this::setUpService;
    private final Runnable operationTimeoutRunnable = this::onOperationTimeout;
    private final Runnable tickRunnable = this::tick;

    private volatile short rumbleLow, rumbleHigh;
    private volatile boolean connected;

    private static class Op {
        final BluetoothGattCharacteristic chr; // null for the CCCD write
        byte[] data;
        final boolean isRumble;

        Op(BluetoothGattCharacteristic chr, byte[] data, boolean isRumble) {
            this.chr = chr;
            this.data = data;
            this.isRumble = isRumble;
        }
    }

    static boolean isTriton(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null && name.startsWith("Steam Ctrl") &&
                    (device.getType() & BluetoothDevice.DEVICE_TYPE_LE) != 0;
        } catch (SecurityException e) {
            return false;
        }
    }

    SteamTritonBleController(Context context, BluetoothDevice device, Handler handler,
                             int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, VALVE_VID, TRITON_BLE_PID);
        this.context = context;
        this.device = device;
        this.handler = handler;
        SteamTritonProtocol.configure(this);
    }

    String getAddress() {
        return device.getAddress();
    }

    @Override
    public boolean start() {
        handler.post(this::connect);
        return true;
    }

    /** Called when Android reports the controller's Bluetooth link came up again. */
    void onAclConnected() {
        handler.post(() -> {
            if (!stopped && gatt == null) {
                connect();
            }
        });
    }

    /** Called when Android reports the controller's Bluetooth link went down. */
    void onAclDisconnected() {
        handler.post(this::disconnect);
    }

    private void connect() {
        if (stopped || gatt != null) {
            return;
        }
        LimeLog.info("SteamTritonBle: connecting to " + device.getAddress());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(context, false, callback);
        }
    }

    private void disconnect() {
        handler.removeCallbacksAndMessages(this);
        operations.clear();
        currentOp = null;
        rumbleQueued = false;
        inputChr = reportChr = rumbleChr = null;
        ready = false;
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        setConnected(false);
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            handler.post(() -> {
                if (g != gatt) {
                    return;
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Typically the controller is off (status 133). We'll try again when
                    // Android reports the Bluetooth link coming back.
                    LimeLog.info("SteamTritonBle: disconnected (status " + status + ")");
                    disconnect();
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            handler.post(() -> {
                if (g != gatt) {
                    return;
                }
                if (status != BluetoothGatt.GATT_SUCCESS || g.getService(SERVICE) == null) {
                    LimeLog.warning("SteamTritonBle: Valve service not found (status " + status + ")");
                    disconnect();
                    return;
                }
                // Set up once the MTU is raised; don't hang if the stack never answers
                if (!g.requestMtu(MTU)) {
                    setUpService();
                } else {
                    handler.postAtTime(setUpServiceRunnable, SteamTritonBleController.this,
                            SystemClock.uptimeMillis() + MTU_TIMEOUT_MS);
                }
            });
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            handler.post(() -> {
                if (g != gatt) {
                    return;
                }
                LimeLog.info("SteamTritonBle: MTU " + mtu + " (status " + status + ")");
                setUpService();
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            handler.post(() -> {
                if (g != gatt) {
                    return;
                }
                finishOperation();
                if (descriptor.getCharacteristic() == inputChr && !ready) {
                    onReady();
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            handler.post(() -> {
                if (g == gatt) {
                    finishOperation();
                }
            });
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            // Called on a binder thread; handle input right away to keep latency low
            if (characteristic.getUuid().equals(inputChrUuid)) {
                handleInput(characteristic.getValue());
            }
        }
    };

    private volatile UUID inputChrUuid;

    private static int valveReportId(UUID uuid) {
        long top = uuid.getMostSignificantBits() >>> 32;
        if ((top & 0xFFFFFF00L) != VALVE_UUID_PREFIX) {
            return -1;
        }
        return (int) (top & 0xFF) - CHARACTERISTIC_REPORT_OFFSET;
    }

    private void setUpService() {
        handler.removeCallbacks(setUpServiceRunnable, this);
        if (gatt == null || inputChr != null) {
            return;
        }
        BluetoothGattService service = gatt.getService(SERVICE);
        if (service == null) {
            disconnect();
            return;
        }

        BluetoothGattCharacteristic state45 = null, state47 = null;
        for (BluetoothGattCharacteristic chr : service.getCharacteristics()) {
            if (chr.getUuid().equals(REPORT_CHARACTERISTIC)) {
                reportChr = chr;
                continue;
            }
            int id = valveReportId(chr.getUuid());
            if (id == ID_CONTROLLER_STATE_BLE) {
                state45 = chr;
            } else if (id == ID_CONTROLLER_STATE_TIMESTAMP) {
                state47 = chr;
            } else if (id == ID_OUT_REPORT_HAPTIC_RUMBLE) {
                rumbleChr = chr;
            }
        }
        inputChr = state45 != null ? state45 : state47;
        if (inputChr == null || reportChr == null) {
            // The original Steam Controller uses the same service, but a different protocol
            LimeLog.warning("SteamTritonBle: not a Steam Controller (2026)");
            disconnect();
            stopped = true;
            return;
        }
        inputReportId = inputChr == state45 ? ID_CONTROLLER_STATE_BLE : ID_CONTROLLER_STATE_TIMESTAMP;
        inputChrUuid = inputChr.getUuid();

        gatt.setCharacteristicNotification(inputChr, true);
        operations.add(new Op(null, null, false));
        runNextOperation();
    }

    private void onReady() {
        ready = true;
        LimeLog.info("SteamTritonBle: ready, input report 0x" + Integer.toHexString(inputReportId));
        sendSettings();
        setConnected(true);
        handler.postAtTime(tickRunnable, this, SystemClock.uptimeMillis() + RUMBLE_RESEND_MS);
    }

    private void tick() {
        if (!ready || gatt == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastSettingsMs >= SETTINGS_INTERVAL_MS) {
            sendSettings();
        }
        if (rumbleLow != 0 || rumbleHigh != 0) {
            queueRumble();
        }
        handler.postAtTime(tickRunnable, this, now + RUMBLE_RESEND_MS);
    }

    private void handleInput(byte[] value) {
        if (value == null || value.length == 0) {
            return;
        }
        byte[] report;
        int length;
        if ((value[0] & 0xFF) == inputReportId && value.length > 45) {
            report = value;
            length = value.length;
        } else {
            // The report ID isn't sent over the air
            report = new byte[value.length + 1];
            report[0] = (byte) inputReportId;
            System.arraycopy(value, 0, report, 1, value.length);
            length = report.length;
        }
        if (connected && parseState(this, report, length)) {
            reportInput();
        }
    }

    private void sendSettings() {
        if (reportChr == null) {
            return;
        }
        lastSettingsMs = SystemClock.uptimeMillis();
        // Like SDL: the report ID doesn't go over the air
        byte[] report = lizardOffReport();
        queueWrite(new Op(reportChr, Arrays.copyOfRange(report, 1, report.length - 1), false));
    }

    private void queueRumble() {
        if (rumbleChr == null) {
            return;
        }
        byte[] report = rumbleReport(rumbleLow, rumbleHigh);
        byte[] payload = Arrays.copyOfRange(report, 1, report.length - 1);
        if (rumbleQueued) {
            // Only the latest rumble values matter
            for (Op op : operations) {
                if (op.isRumble) {
                    op.data = payload;
                    return;
                }
            }
        }
        rumbleQueued = true;
        queueWrite(new Op(rumbleChr, payload, true));
    }

    private void queueWrite(Op op) {
        operations.add(op);
        runNextOperation();
    }

    @SuppressWarnings("deprecation")
    private void runNextOperation() {
        while (currentOp == null && !operations.isEmpty() && gatt != null) {
            Op op = operations.poll();
            if (op.isRumble) {
                rumbleQueued = false;
            }
            boolean started;
            if (op.chr == null) {
                BluetoothGattDescriptor cccd = inputChr.getDescriptor(CCCD);
                if (cccd == null) {
                    // Some stacks deliver notifications without a CCCD write
                    if (!ready) {
                        onReady();
                    }
                    continue;
                }
                boolean indicate = (inputChr.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0;
                cccd.setValue(indicate ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                started = gatt.writeDescriptor(cccd);
            } else {
                op.chr.setValue(op.data);
                started = gatt.writeCharacteristic(op.chr);
            }
            if (started) {
                currentOp = op;
                handler.postAtTime(operationTimeoutRunnable, this,
                        SystemClock.uptimeMillis() + OPERATION_TIMEOUT_MS);
            } else {
                LimeLog.warning("SteamTritonBle: GATT write could not start");
            }
        }
    }

    private void onOperationTimeout() {
        if (currentOp != null) {
            LimeLog.warning("SteamTritonBle: GATT operation timed out");
            boolean wasCccd = currentOp.chr == null;
            finishOperation();
            if (wasCccd && !ready && gatt != null) {
                // We may still be getting input; carry on like SDL does
                onReady();
            }
        }
    }

    private void finishOperation() {
        handler.removeCallbacks(operationTimeoutRunnable, this);
        currentOp = null;
        runNextOperation();
    }

    private void setConnected(boolean nowConnected) {
        if (connected == nowConnected) {
            return;
        }
        connected = nowConnected;
        if (nowConnected) {
            LimeLog.info("SteamTritonBle: controller connected");
            notifyDeviceAdded();
        } else {
            LimeLog.info("SteamTritonBle: controller disconnected");
            clearState(this);
            rumbleLow = rumbleHigh = 0;
            notifyDeviceRemoved();
        }
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        rumbleLow = lowFreqMotor;
        rumbleHigh = highFreqMotor;
        handler.post(() -> {
            if (ready) {
                queueRumble();
            }
        });
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // No trigger motors
    }

    @Override
    public void stop() {
        handler.post(() -> {
            stopped = true;
            disconnect();
        });
    }
}

package com.limelight.binding.input.driver;

import android.Manifest;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import com.limelight.LimeLog;

import java.util.HashMap;

/**
 * Finds paired Steam Controllers (2026) and connects to them over Bluetooth LE. Controllers are
 * picked up from the bonded device list at start and whenever Android reports one connecting.
 */
@SuppressLint("MissingPermission") // start() checks BLUETOOTH_CONNECT before touching the adapter
public class SteamTritonBleManager {
    private static volatile boolean active;

    interface IdAllocator {
        int nextId();
    }

    private final Context context;
    private final UsbDriverListener listener;
    private final IdAllocator idAllocator;
    private final HashMap<String, SteamTritonBleController> controllers = new HashMap<>();
    private HandlerThread thread;
    private Handler handler;
    private boolean receiverRegistered;

    /** True while we drive paired Steam Controllers ourselves, so Android's copy must be ignored. */
    public static boolean isActive() {
        return active;
    }

    public static boolean isTritonInputDevice(int vendorId, int productId) {
        return vendorId == SteamTritonProtocol.VALVE_VID && productId == SteamTritonProtocol.TRITON_BLE_PID;
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true; // Granted at install time
        }
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.BLUETOOTH;
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /** Asks for the Bluetooth permission (Android 12+) if the Steam Controller driver needs it. */
    public static void requestPermissionIfNeeded(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isSupported(activity) && !hasPermission(activity)) {
            activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, requestCode);
        }
    }

    SteamTritonBleManager(Context context, UsbDriverListener listener, IdAllocator idAllocator) {
        this.context = context;
        this.listener = listener;
        this.idAllocator = idAllocator;
    }

    private final BroadcastReceiver aclReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                return;
            }
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                if (SteamTritonBleController.isTriton(device)) {
                    connectDevice(device);
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                SteamTritonBleController controller = controllers.get(device.getAddress());
                if (controller != null) {
                    controller.onAclDisconnected();
                }
            }
        }
    };

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "WrongConstant"})
    void start() {
        if (thread != null || !isSupported(context)) {
            return;
        }
        if (!hasPermission(context)) {
            LimeLog.info("SteamTritonBle: Bluetooth permission not granted");
            return;
        }
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btManager != null ? btManager.getAdapter() : null;
        if (adapter == null) {
            return;
        }

        thread = new HandlerThread("SteamTritonBle");
        thread.start();
        handler = new Handler(thread.getLooper());
        active = true;

        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (SteamTritonBleController.isTriton(device)) {
                    connectDevice(device);
                }
            }
        } catch (SecurityException e) {
            LimeLog.warning("SteamTritonBle: " + e);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        // These are protected system broadcasts; SDL registers for them the same way
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(aclReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(aclReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void connectDevice(BluetoothDevice device) {
        SteamTritonBleController controller = controllers.get(device.getAddress());
        if (controller != null) {
            controller.onAclConnected();
            return;
        }
        LimeLog.info("SteamTritonBle: found paired controller " + device.getAddress());
        controller = new SteamTritonBleController(context, device, handler, idAllocator.nextId(), listener);
        controllers.put(device.getAddress(), controller);
        controller.start();
    }

    void stop() {
        if (thread == null) {
            return;
        }
        active = false;
        if (receiverRegistered) {
            context.unregisterReceiver(aclReceiver);
            receiverRegistered = false;
        }
        for (SteamTritonBleController controller : controllers.values()) {
            controller.stop();
        }
        controllers.clear();
        // Let the queued disconnects run before the looper goes away
        thread.quitSafely();
        thread = null;
        handler = null;
    }
}

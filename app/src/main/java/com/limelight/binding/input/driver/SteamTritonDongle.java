package com.limelight.binding.input.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

import com.limelight.LimeLog;

import java.util.ArrayList;

/**
 * Wireless puck for the Steam Controller (2026). Each of interfaces 2..5 is one controller slot,
 * and each slot shows up as its own controller once something connects to it.
 */
public class SteamTritonDongle extends AbstractController {
    public static final int MAX_SLOTS = 4;
    private static final int[] PUCK_PIDS = {0x1304, 0x1305};

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final UsbDriverListener listener;
    private final int firstSlotId;
    private final ArrayList<SteamTritonController> slots = new ArrayList<>();
    private volatile boolean stopped;

    public static boolean canClaimDevice(UsbDevice device) {
        if (device.getVendorId() != SteamTritonController.VALVE_VID) {
            return false;
        }
        for (int pid : PUCK_PIDS) {
            if (device.getProductId() == pid) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param firstSlotId the first of {@link #MAX_SLOTS} consecutive controller IDs reserved
     *                    for the slots
     */
    public SteamTritonDongle(UsbDevice device, UsbDeviceConnection connection, int deviceId,
                             int firstSlotId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.listener = listener;
        this.firstSlotId = firstSlotId;
    }

    @Override
    public boolean start() {
        for (int i = 0; i < device.getInterfaceCount() && slots.size() < MAX_SLOTS; i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getId() < 2 || iface.getId() > 5 || !SteamTritonController.hasInEndpoint(iface)) {
                continue;
            }
            SteamTritonController slot = new SteamTritonController(device, connection, iface, true,
                    firstSlotId + slots.size(), listener);
            // The puck was unplugged
            slot.ioErrorHandler = this::stop;
            if (slot.start()) {
                slots.add(slot);
            }
        }

        if (slots.isEmpty()) {
            LimeLog.warning("SteamTriton: no usable slots on the puck");
            return false;
        }
        LimeLog.info("SteamTriton: puck opened with " + slots.size() + " slots");
        return true;
    }

    @Override
    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        for (SteamTritonController slot : slots) {
            slot.stopSlot();
        }
        slots.clear();
        connection.close();
        notifyDeviceRemoved();
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        // Rumble goes to the individual slots, which are registered as their own controllers
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
    }
}

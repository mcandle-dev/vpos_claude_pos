package com.example.apidemo.ble;
public class Device {
    private String deviceName;
    private String macAddress;
    private int rssi;
    private String serviceUuid;

    private long Timestamp;

    public Device(String deviceName, String macAddress, int rssi, String serviceUuid, long timestamp) {
        this.deviceName = deviceName;
        this.macAddress = macAddress;
        this.rssi = rssi;
        this.serviceUuid = serviceUuid;
        this.Timestamp = timestamp;
    }

    // Getters and setters
    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public void setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public long getTimestamp() {
        return Timestamp;
    }

    public void setTimestamp(long timestamp) {
        Timestamp = timestamp;
    }
}

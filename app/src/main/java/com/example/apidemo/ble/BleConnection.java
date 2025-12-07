package com.example.apidemo.ble;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vpos.apipackage.At;

/**
 * BLE Connection Manager using AT commands
 * Handles connection, data transmission, and channel configuration
 */
public class BleConnection {
    private static final String TAG = "BleConnection";

    private Integer connectionHandle = null;
    private boolean testMode = false;

    // Result classes
    public static class ConnectionResult {
        private final boolean success;
        private final String error;
        private final Integer handle;

        public ConnectionResult(boolean success, Integer handle, String error) {
            this.success = success;
            this.handle = handle;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public Integer getHandle() { return handle; }
    }

    public static class SendResult {
        private final boolean success;
        private final String error;

        public SendResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }

    public static class ReceiveResult {
        private final boolean success;
        private final byte[] data;
        private final String error;
        private final boolean timeout;

        public ReceiveResult(boolean success, byte[] data, String error, boolean timeout) {
            this.success = success;
            this.data = data;
            this.error = error;
            this.timeout = timeout;
        }

        public boolean isSuccess() { return success; }
        public byte[] getData() { return data; }
        public String getError() { return error; }
        public boolean isTimeout() { return timeout; }
    }

    public static class UuidChannel {
        public final int channelNum;
        public final String uuid;
        public final String properties;

        public UuidChannel(int channelNum, String uuid, String properties) {
            this.channelNum = channelNum;
            this.uuid = uuid;
            this.properties = properties;
        }
    }

    public static class UuidScanResult {
        private final boolean success;
        private final List<UuidChannel> channels;
        private final String error;

        public UuidScanResult(boolean success, List<UuidChannel> channels, String error) {
            this.success = success;
            this.channels = channels;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public List<UuidChannel> getChannels() { return channels; }
        public String getError() { return error; }
    }

    public BleConnection() {
        this.testMode = false;
    }

    public BleConnection(boolean testMode) {
        this.testMode = testMode;
    }

    /**
     * Connect to a BLE device
     * @param macAddress MAC address in format XX:XX:XX:XX:XX:XX
     * @return ConnectionResult with handle on success
     */
    public ConnectionResult connectToDevice(String macAddress) {
        Log.d(TAG, "Connecting to device: " + macAddress);

        // Step 1: Set pairing mode to "Just Works" (no user intervention)
        String pairCmd = "AT+MASTER_PAIR=3\r\n";
        Log.i(TAG, "[AT CMD] >>> " + pairCmd.trim());
        int ret = At.Lib_ComSend(pairCmd.getBytes(), pairCmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send pairing mode command, ret: " + ret);
            return new ConnectionResult(false, null, "Failed to set pairing mode: " + ret);
        }

        // Receive pairing mode response
        byte[] pairResponse = new byte[256];
        int[] pairLen = new int[1];
        ret = At.Lib_ComRecvAT(pairResponse, pairLen, 3000, 256);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + pairLen[0]);

        if (ret != 0 || pairLen[0] == 0) {
            Log.e(TAG, "Failed to receive pairing mode response, ret: " + ret);
            return new ConnectionResult(false, null, "No response for pairing mode");
        }

        String pairResponseStr = new String(pairResponse, 0, pairLen[0]);
        Log.i(TAG, "[AT RSP] <<< " + pairResponseStr.replace("\r\n", "\\r\\n"));

        if (!pairResponseStr.contains("OK")) {
            Log.e(TAG, "Failed to set pairing mode");
            return new ConnectionResult(false, null, "Failed to set pairing mode: " + pairResponseStr);
        }

        // Step 2: Send AT+CONNECT command
        String cmd = "AT+CONNECT=," + macAddress + "\r\n";
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send connect command, ret: " + ret);
            return new ConnectionResult(false, null, "Failed to send command: " + ret);
        }

        // Receive response
        byte[] response = new byte[512];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 5000, 512);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

        if (ret != 0 || len[0] == 0) {
            Log.e(TAG, "Failed to receive connect response, ret: " + ret);
            return new ConnectionResult(false, null, "No response from device");
        }

        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

        // Parse response
        Integer handle = parseConnectResponse(responseStr);
        if (handle != null) {
            connectionHandle = handle;
            Log.d(TAG, "Connected with handle: " + handle);
            return new ConnectionResult(true, handle, null);
        } else if (responseStr.contains("OK")) {
            // If only OK is returned, assume connection is successful but handle is unknown
            // Try to get connection handle by querying connected devices
            Log.d(TAG, "Connect response only returned OK, trying to get connection handle");
            handle = getConnectionHandleFromDeviceList();
            if (handle != null) {
                connectionHandle = handle;
                Log.d(TAG, "Connected with handle retrieved from device list: " + handle);
                return new ConnectionResult(true, handle, null);
            } else {
                // If we can't get handle, assume connection is successful with default handle 1
                // This is a fallback for devices that only return OK
                Log.d(TAG, "Could not get connection handle, using default handle 1");
                connectionHandle = 1;
                return new ConnectionResult(true, 1, "Connected but handle not found, using default");
            }
        } else {
            Log.e(TAG, "Failed to parse connect response");
            return new ConnectionResult(false, null, "Failed to parse response: " + responseStr);
        }
    }

    /**
     * Disconnect from the connected device
     * @return true if disconnected successfully
     */
    public boolean disconnect() {
        if (connectionHandle == null) {
            Log.w(TAG, "No active connection to disconnect");
            return false;
        }

        Log.d(TAG, "Disconnecting handle: " + connectionHandle);

        // Send AT+DISCONNECT command according to AT command set
        String cmd = "AT+DISCONNECT=1," + connectionHandle + "\r\n";
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        int ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send disconnect command, ret: " + ret);
            return false;
        }

        // Receive response
        byte[] response = new byte[256];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 20, 3000);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

        connectionHandle = null;
        return responseStr.contains("OK") || responseStr.contains("DISCONNECTED");
    }

    /**
     * Scan for UUID channels on the connected device
     * @return UuidScanResult with list of available channels
     */
    public UuidScanResult scanUuidChannels() {
        if (connectionHandle == null) {
            return new UuidScanResult(false, null, "Not connected");
        }

        Log.d(TAG, "Scanning UUID channels");

        // Send AT+UUID_SCAN command
        String cmd = "AT+UUID_SCAN=1\r\n";
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        int ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            return new UuidScanResult(false, null, "Failed to send command: " + ret);
        }

        // Receive response
        byte[] response = new byte[2048];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 2000, 100);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

        if (ret != 0 || len[0] == 0) {
            return new UuidScanResult(false, null, "No response");
        }

        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

        // Parse response: "-CHAR:[num] UUID:[uuid],[properties];"
        List<UuidChannel> channels = parseUuidScanResponse(responseStr);
        return new UuidScanResult(true, channels, null);
    }

    /**
     * Set TRX channel for data communication
     * @param writeCh Write channel number
     * @param notifyCh Notify channel number
     * @param type Write type (0=without response, 1=with response)
     * @return true if set successfully
     */
    public boolean setTrxChannel(int writeCh, int notifyCh, int type) {
        if (connectionHandle == null) {
            Log.e(TAG, "Cannot set TRX channel: not connected");
            return false;
        }

        Log.d(TAG, String.format("Setting TRX channel: write=%d, notify=%d, type=%d",
            writeCh, notifyCh, type));

        // Send AT+TRX_CHAN command
        String cmd = String.format("AT+TRX_CHAN=%d,%d,%d,%d\r\n",
            connectionHandle, writeCh, notifyCh, type);
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        int ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send TRX channel command, ret: " + ret);
            return false;
        }

        // Receive response
        byte[] response = new byte[256];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 20, 3000);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

        return responseStr.contains("OK");
    }

    /**
     * Send data to the connected device
     * @param data Data to send
     * @param timeout Timeout in milliseconds
     * @return SendResult
     */
    public SendResult sendData(byte[] data, int timeout) {
        if (connectionHandle == null) {
            return new SendResult(false, "Not connected");
        }

        Log.d(TAG, "Sending data, size: " + data.length);

        // Send AT+SEND command
        String cmd = String.format("AT+SEND=%d,%d,%d\r\n",
            connectionHandle, data.length, timeout);
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        int ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            return new SendResult(false, "Failed to send command: " + ret);
        }

        // Wait for "INPUT_BLE_DATA:" prompt or direct OK
        byte[] response = new byte[256];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 1000, 256);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

        // Check if we got INPUT_BLE_DATA prompt
        boolean needSendData = responseStr.contains("INPUT_BLE_DATA");
        boolean okResponse = responseStr.contains("OK");

        if (!needSendData && !okResponse) {
            return new SendResult(false, "Unexpected response: " + responseStr);
        }

        // Send actual data only if prompted
        if (needSendData) {
            Log.i(TAG, "[AT DATA] >>> " + new String(data) + " (" + data.length + " bytes)");
            ret = At.Lib_ComSend(data, data.length);
            Log.d(TAG, "[AT DATA] Lib_ComSend returned: " + ret);

            if (ret != 0) {
                return new SendResult(false, "Failed to send data: " + ret);
            }

            // Wait for confirmation
        ret = At.Lib_ComRecvAT(response, len, 5000, 256);
            Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);
            responseStr = new String(response, 0, len[0]);
            Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));
        }

        if (responseStr.contains("OK") || responseStr.contains("SEND_OK")) {
            return new SendResult(true, null);
        } else {
            return new SendResult(false, "Send failed: " + responseStr);
        }
    }

    /**
     * Receive data from the connected device
     * @param timeout Timeout in milliseconds
     * @return ReceiveResult with received data
     */
    public ReceiveResult receiveData(int timeout) {
        if (connectionHandle == null) {
            return new ReceiveResult(false, null, "Not connected", false);
        }

        Log.d(TAG, "Waiting to receive data (timeout: " + timeout + "ms)");
        byte[] response = new byte[2048];
        int[] len = new int[1];
        int ret = At.Lib_ComRecvAT(response, len, timeout, 200);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

        if (ret != 0) {
            return new ReceiveResult(false, null, "Receive error: " + ret, false);
        }

        if (len[0] == 0) {
            Log.w(TAG, "[AT RSP] <<< (timeout, no data)");
            return new ReceiveResult(false, null, "Timeout", true);
        }

        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

        // Parse received data
        byte[] data = parseReceivedData(responseStr);
        if (data != null) {
            Log.d(TAG, "[AT DATA] Parsed " + data.length + " bytes");
            return new ReceiveResult(true, data, null, false);
        } else {
            // Return raw response if can't parse
            Log.d(TAG, "[AT DATA] Returning raw response (" + len[0] + " bytes)");
            return new ReceiveResult(true, response, null, false);
        }
    }

    /**
     * Check if connected to a device
     * @return true if connected
     */
    public boolean isConnected() {
        return connectionHandle != null;
    }

    /**
     * Get the current connection handle
     * @return connection handle or null if not connected
     */
    public Integer getConnectionHandle() {
        return connectionHandle;
    }

    // Parse connect response to extract handle
    private Integer parseConnectResponse(String response) {
        // Pattern: "[MAC] CONNECTED [handle]" or "CONNECTED [handle]"
        Pattern pattern = Pattern.compile("CONNECTED\\s+(\\d+)");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse handle: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get connection handle from device list using AT+CNT_LIST
     * @return Connection handle if found, null otherwise
     */
    private Integer getConnectionHandleFromDeviceList() {
        Log.d(TAG, "Querying connected devices with AT+CNT_LIST");
        
        // Send AT+CNT_LIST command to get connected devices
        String cmd = "AT+CNT_LIST\r\n";
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        int ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);
        
        if (ret != 0) {
            Log.e(TAG, "Failed to send AT+CNT_LIST command");
            return null;
        }
        
        // Receive response
        byte[] response = new byte[256];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 3000, 256);
        Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);
        
        if (ret != 0 || len[0] == 0) {
            Log.e(TAG, "Failed to receive AT+CNT_LIST response");
            return null;
        }
        
        String responseStr = new String(response, 0, len[0]);
        Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));
        
        // Parse response:
        // Support multiple formats:
        // 1. Single line: AT+CNT_LIST=1*(FF:1C:2B:D1:4C:BD) OK
        // 2. Multi line: AT+CNT_LIST=
        //                2 (53:42:A4:F6:01:F5)
        //                1 (59:2D:6F:0B:87:3D)
        //                OK
        
        // First, check if there's any handle in the response
        // Use simple pattern without \s for Java 11 compatibility
        Pattern pattern = Pattern.compile("(\\d+)[ ]*\\(");
        Matcher matcher = pattern.matcher(responseStr);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse handle from device list: " + e.getMessage());
            }
        }
        
        // If no handle found, check if response contains OK (meaning connected but format unknown)
        if (responseStr.contains("OK")) {
            // Return first available handle (1) as default since we know we're connected
            Log.d(TAG, "AT+CNT_LIST returned OK but no handle found, using default handle 1");
            return 1;
        }
        
        return null;
    }

    // Parse UUID scan response
    private List<UuidChannel> parseUuidScanResponse(String response) {
        List<UuidChannel> channels = new ArrayList<>();

        // Pattern: "-CHAR:[num] UUID:[uuid],[properties];"
        Pattern pattern = Pattern.compile("-CHAR:(\\d+)\\s+UUID:([^,]+),([^;]+);");
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            try {
                int channelNum = Integer.parseInt(matcher.group(1));
                String uuid = matcher.group(2).trim();
                String properties = matcher.group(3).trim();
                channels.add(new UuidChannel(channelNum, uuid, properties));
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse UUID channel: " + e.getMessage());
            }
        }

        return channels;
    }

    // Parse received data from response
    private byte[] parseReceivedData(String response) {
        // Look for AT+SEND response pattern: "+RECEIVED:<handle>,<length> <data> OK"
        // Example: "+RECEIVED:1,10 OUTPUT_BLE_DATA 123456789A OK"
        Pattern pattern = Pattern.compile("\\+RECEIVED:([0-9]+),([0-9]+)\\s+([^\\r\\n]+)\\s+OK");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String data = matcher.group(3);
            return data.getBytes();
        }

        // Also check for direct data response without prefix
        if (response.contains("OK")) {
            // Return everything before OK, trimming whitespace
            int okIndex = response.indexOf("OK");
            String data = response.substring(0, okIndex).trim();
            if (!data.isEmpty()) {
                return data.getBytes();
            }
        }

        return null;
    }

    // Convert hex string to byte array
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

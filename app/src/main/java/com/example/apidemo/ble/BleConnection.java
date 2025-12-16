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
    private List<UuidChannel> discoveredChannels = new ArrayList<>(); // Store channels from connection

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

    public boolean setMasterMode(int timeout) {
        Log.d(TAG, "\nSetting Master Mode...");
        String roleCmd = "AT+ROLE=1\r\n";
        Log.i(TAG, "[AT CMD] >>> " + roleCmd.trim());
        int ret = At.Lib_ComSend(roleCmd.getBytes(), roleCmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send ROLE command, ret: " + ret);
            return false;
        }

        byte[] roleResponse = new byte[256];
        int[] roleLen = new int[1];
        ret = At.Lib_ComRecvAT(roleResponse, roleLen, timeout, 256);
        String roleResponseStr = new String(roleResponse, 0, roleLen[0]);
        Log.i(TAG, "[AT RSP] <<< " + roleResponseStr.replace("\r\n", "\\r\\n"));

        if (!roleResponseStr.contains("OK")) {
            Log.e(TAG, "Failed to set Master mode");
            return false;
        }

        return true;
    }

    public boolean setPairingMode(int timeout) {
        Log.d(TAG, "\nSetting Pairing Mode (Just Works)...");
        String pairCmd = "AT+MASTER_PAIR=3\r\n";
        Log.i(TAG, "[AT CMD] >>> " + pairCmd.trim());
        int ret = At.Lib_ComSend(pairCmd.getBytes(), pairCmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send pairing mode command, ret: " + ret);
            return false;
        }

        byte[] pairResponse = new byte[256];
        int[] pairLen = new int[1];
        At.Lib_ComRecvAT(pairResponse, pairLen, timeout, 256);
        String pairResponseStr = new String(pairResponse, 0, pairLen[0]);
        Log.i(TAG, "[AT RSP] <<< " + pairResponseStr.replace("\r\n", "\\r\\n"));

        if (!pairResponseStr.contains("OK")) {
            Log.e(TAG, "Failed to set pairing mode");
            return false;
        }

        return true;
    }

    public boolean setUuidScanMode(int timeout) {
        Log.d(TAG, "\nEnabling UUID Scan (for auto UUID discovery)...");
        String uuidScanCmd = "AT+UUID_SCAN=1\r\n";
        Log.i(TAG, "[AT CMD] >>> " + uuidScanCmd.trim());
        int ret = At.Lib_ComSend(uuidScanCmd.getBytes(), uuidScanCmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret != 0) {
            Log.e(TAG, "Failed to send UUID_SCAN command, ret: " + ret);
            return false;
        }

        byte[] uuidScanResponse = new byte[128];
        int[] uuidScanLen = new int[1];
        At.Lib_ComRecvAT(uuidScanResponse, uuidScanLen, timeout, 128);
        String uuidScanResponseStr = new String(uuidScanResponse, 0, uuidScanLen[0]);
        Log.i(TAG, "[AT RSP] <<< " + uuidScanResponseStr.replace("\r\n", "\\r\\n"));

        if (!uuidScanResponseStr.contains("OK")) {
            Log.e(TAG, "UUID scan may have failed: " + uuidScanResponseStr);
            return false;
        }

        return true;
    }
    /**
     * Connect to a BLE device following BLE_GATT_Connection_Guide.md Steps 2-4
     *
     * Step 2: Set Master Mode (AT+ROLE=1)
     * Step 3: BLE Scan (AT+OBSERVER) - Already done in BeaconActivity
     * Step 4: Connect to Device (AT+CONNECT)
     *
     * Note: Step 1 (Command Mode Entry with +++) is excluded as it may already be in command mode
     * Note: Step 5 (UUID Scan) is performed in sendDataComplete() when actually needed
     *
     * @param macAddress MAC address in format XX:XX:XX:XX:XX:XX
     * @return ConnectionResult with handle on success
     */
    public ConnectionResult connectToDevice(String macAddress) {
        Log.d(TAG, "=== BLE Connection Process Started ===");
        Log.d(TAG, "Target MAC: " + macAddress);

        try {
//            // ====================================================================
//            // Debug: Check current connection list before connecting
//            // ====================================================================
//            Log.d(TAG, "\n[Debug] Checking current connection list...");
//            String cntListCmd = "AT+CNT_LIST\r\n";
//            Log.i(TAG, "[AT CMD] >>> " + cntListCmd.trim());
//            int ret = At.Lib_ComSend(cntListCmd.getBytes(), cntListCmd.length());
//
//            String cntResponseStr = null;
//            if (ret == 0) {
//                byte[] cntResponse = new byte[256];
//                int[] cntLen = new int[1];
//                ret = At.Lib_ComRecvAT(cntResponse, cntLen, 2000, 256);
//                cntResponseStr = new String(cntResponse, 0, cntLen[0]);
//                Log.i(TAG, "[AT RSP] <<< " + cntResponseStr.replace("\r\n", "\\r\\n"));
//            }
//
//            // ====================================================================
//            // Debug: Check BLE module status (may not be supported on all modules)
//            // ====================================================================
//            Log.d(TAG, "\n[Debug] Checking BLE module status...");
//            String statusCmd = "AT+STATUS?\r\n";
//            Log.i(TAG, "[AT CMD] >>> " + statusCmd.trim());
//            ret = At.Lib_ComSend(statusCmd.getBytes(), statusCmd.length());
//
//            if (ret == 0) {
//                byte[] statusResponse = new byte[256];
//                int[] statusLen = new int[1];
//                ret = At.Lib_ComRecvAT(statusResponse, statusLen, 2000, 256);
//                String statusResponseStr = new String(statusResponse, 0, statusLen[0]);
//                Log.i(TAG, "[AT RSP] <<< " + statusResponseStr.replace("\r\n", "\\r\\n"));
//
//                if (statusResponseStr.contains("ERROR")) {
//                    Log.w(TAG, "AT+STATUS not supported on this module (ignoring)");
//                }
//            }
//
//            // ====================================================================
//            // Disconnect any existing connections to ensure clean state
//            // ====================================================================
//            if (cntResponseStr != null && !cntResponseStr.trim().equals("AT+CNT_LIST=\r\nOK")) {
//                Log.d(TAG, "\n[Cleanup] Found existing connection(s), disconnecting...");
//
//                // Parse existing handles and disconnect them
//                Pattern handlePattern = Pattern.compile("(\\d+)\\s*\\(");
//                Matcher handleMatcher = handlePattern.matcher(cntResponseStr);
//
//                while (handleMatcher.find()) {
//                    try {
//                        int existingHandle = Integer.parseInt(handleMatcher.group(1));
//                        Log.d(TAG, "Disconnecting existing handle: " + existingHandle);
//
//                        boolean disconnected = false;
//
//                        // Strategy 1: Try AT+DISCONNECT=0,handle (slave disconnect - master initiates)
//                        String discCmd1 = "AT+DISCONNECT=1," + existingHandle + "\r\n";
//                        Log.i(TAG, "[AT CMD] >>> " + discCmd1.trim());
//                        ret = At.Lib_ComSend(discCmd1.getBytes(), discCmd1.length());
//
//                        if (ret == 0) {
//                            byte[] discResponse = new byte[256];
//                            int[] discLen = new int[1];
//                            ret = At.Lib_ComRecvAT(discResponse, discLen, 1000, 256);
//                            String discResponseStr = new String(discResponse, 0, discLen[0]);
//                            Log.i(TAG, "[AT RSP] <<< " + discResponseStr.replace("\r\n", "\\r\\n"));
//
//                            if (discResponseStr.contains("OK") || discResponseStr.contains("DISCONNECTED")) {
//                                Log.d(TAG, "✓ Successfully disconnected handle " + existingHandle + " (method 1)");
//                                disconnected = true;
//                            }
//                        }
//
//                        // Strategy 2: If first method failed, try AT+DISCE=handle
//                        if (!disconnected) {
//                            Thread.sleep(300);
//                            String discCmd2 = "AT+DISCE=" + existingHandle + "\r\n";
//                            Log.i(TAG, "[AT CMD] >>> " + discCmd2.trim() + " (trying alternative)");
//                            ret = At.Lib_ComSend(discCmd2.getBytes(), discCmd2.length());
//
//                            if (ret == 0) {
//                                byte[] discResponse2 = new byte[256];
//                                int[] discLen2 = new int[1];
//                                ret = At.Lib_ComRecvAT(discResponse2, discLen2, 2000, 256);
//                                String discResponseStr2 = new String(discResponse2, 0, discLen2[0]);
//                                Log.i(TAG, "[AT RSP] <<< " + discResponseStr2.replace("\r\n", "\\r\\n"));
//
//                                if (discResponseStr2.contains("OK") || discResponseStr2.contains("DISCONNECTED")) {
//                                    Log.d(TAG, "✓ Successfully disconnected handle " + existingHandle + " (method 2)");
//                                    disconnected = true;
//                                }
//                            }
//                        }
//
//                        if (!disconnected) {
//                            Log.w(TAG, "All disconnect methods failed for handle " + existingHandle + " - will try module reset");
//                        }
//
//                        // Wait between disconnects
//                        Thread.sleep(500);
//                    } catch (Exception e) {
//                        Log.w(TAG, "Failed to disconnect handle: " + e.getMessage());
//                    }
//                }
//
//                // Wait for disconnections to complete
//                Log.d(TAG, "Waiting for disconnections to complete...");
//                Thread.sleep(1000);
//
//                // ================================================================
//                // Strategy 3: If disconnects failed, force reset by switching roles
//                // Setting ROLE=0 will disconnect all active connections
//                // ================================================================
//                Log.d(TAG, "\n[Cleanup - Force Reset] Switching to Slave mode to clear all connections...");
//                String roleResetCmd = "AT+ROLE=0\r\n";
//                Log.i(TAG, "[AT CMD] >>> " + roleResetCmd.trim());
//                ret = At.Lib_ComSend(roleResetCmd.getBytes(), roleResetCmd.length());
//
//                if (ret == 0) {
//                    byte[] roleResetResponse = new byte[256];
//                    int[] roleResetLen = new int[1];
//                    ret = At.Lib_ComRecvAT(roleResetResponse, roleResetLen, 1000, 256);
//                    String roleResetResponseStr = new String(roleResetResponse, 0, roleResetLen[0]);
//                    Log.i(TAG, "[AT RSP] <<< " + roleResetResponseStr.replace("\r\n", "\\r\\n"));
//
//                    if (roleResetResponseStr.contains("OK")) {
//                        Log.d(TAG, "✓ BLE module reset to Slave mode (all connections cleared)");
//                        Thread.sleep(500);
//
//                        // Now verify connections are cleared
//                        String verifyClearCmd = "AT+CNT_LIST\r\n";
//                        Log.i(TAG, "[AT CMD] >>> " + verifyClearCmd.trim());
//                        ret = At.Lib_ComSend(verifyClearCmd.getBytes(), verifyClearCmd.length());
//
//                        if (ret == 0) {
//                            byte[] verifyClearResponse = new byte[256];
//                            int[] verifyClearLen = new int[1];
//                            ret = At.Lib_ComRecvAT(verifyClearResponse, verifyClearLen, 2000, 256);
//                            String verifyClearResponseStr = new String(verifyClearResponse, 0, verifyClearLen[0]);
//                            Log.i(TAG, "[AT RSP] <<< " + verifyClearResponseStr.replace("\r\n", "\\r\\n"));
//
//                            if (verifyClearResponseStr.contains("NULL")) {
//                                Log.d(TAG, "✓ Confirmed: All connections cleared");
//                            } else {
//                                Log.w(TAG, "Connections may still exist: " + verifyClearResponseStr);
//                            }
//                        }
//                    } else {
//                        Log.w(TAG, "Failed to reset module: " + roleResetResponseStr);
//                    }
//                }
//            }

            // ====================================================================
            // Step 2: Set Master Mode (AT+ROLE=1)
            // ====================================================================
            if (!setMasterMode(1000)) {
                return new ConnectionResult(false, null, "Failed to set Master mode");
            }

            // ====================================================================
            // Step 3: Set Pairing Mode (AT+MASTER_PAIR=3)
            // ====================================================================
            if (!setPairingMode(3000)) {
                return new ConnectionResult(false, null, "Failed to set pairing mode");
            }

            // ====================================================================
            // Step 4-1.5: Enable UUID Scan (AT+UUID_SCAN=1) - BEFORE CONNECT!
            // Must be enabled BEFORE AT+CONNECT to auto-print UUIDs when connecting
            // ====================================================================
            if (!setUuidScanMode(2000)) {
                return new ConnectionResult(false, null, "Failed to set uuid scan mode");
            }

            // ====================================================================
            // Step 4-2: Connect to Device (AT+CONNECT)
            // ====================================================================
            Log.d(TAG, "\n[Step 4-2] Connecting to Device...");

            // Simple MAC address validation (length check only to avoid regex issues)
            if (macAddress == null || macAddress.length() != 17) {
                Log.e(TAG, "Invalid MAC address: " + macAddress);
                return new ConnectionResult(false, null, "Invalid MAC address");
            }
            Log.d(TAG, "Target MAC validated: " + macAddress);

            String connectCmd = "AT+CONNECT=," + macAddress + "\r\n";
            Log.i(TAG, "[AT CMD] >>> " + connectCmd.trim());

            int ret = At.Lib_ComSend(connectCmd.getBytes(), connectCmd.length());
            Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

            if (ret != 0) {
                Log.e(TAG, "Failed to send connect command, ret: " + ret);
                return new ConnectionResult(false, null, "Failed to send CONNECT: " + ret);
            }

            // Wait for Connection Response
            byte[] connectResponse = new byte[2048];
            int[] connectLen = new int[1];
            ret = At.Lib_ComRecvAT(connectResponse, connectLen, 3000, 2048);
            Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + connectLen[0]);

            if (ret != 0 || connectLen[0] == 0) {
                Log.e(TAG, "Failed to receive connect response, ret: " + ret);
                return new ConnectionResult(false, null, "No connection response from device");
            }

            String connectResponseStr = new String(connectResponse, 0, connectLen[0]);
            Log.i(TAG, "[AT RSP] <<< " + connectResponseStr.replace("\r\n", "\\r\\n"));

            // ====================================================================
            // Check for immediate DISCONNECTED in the same response
            // ====================================================================
            if (connectResponseStr.contains("DISCONNECTED")) {
                Log.e(TAG, "✗ Connection failed - Device immediately disconnected");
                Log.e(TAG, "Possible causes:");
                Log.e(TAG, "  1. Pairing/bonding requirement mismatch");
                Log.e(TAG, "  2. Device rejected connection (security, whitelist, etc.)");
                Log.e(TAG, "  3. Connection parameters not acceptable");
                Log.e(TAG, "  4. Device already connected to another master");
                return new ConnectionResult(false, null,
                    "Device disconnected immediately after connection. Check pairing mode and device security settings.");
            }

            // ====================================================================
            // Parse and store UUID channels from connection response
            // When AT+UUID_SCAN=1 is enabled before connection, CHAR data is
            // automatically included in the CONNECTED response
            // ====================================================================
            if (connectResponseStr.contains("-CHAR:")) {
                Log.d(TAG, "✓ UUID characteristics found in connection response");
                discoveredChannels = parseUuidScanResponse(connectResponseStr);
                Log.d(TAG, "✓ Stored " + discoveredChannels.size() + " characteristics for later use");
                for (UuidChannel channel : discoveredChannels) {
                    Log.d(TAG, "  - CH" + channel.channelNum + " UUID:" + channel.uuid +
                          " (" + channel.properties + ")");
                }
            } else {
                Log.w(TAG, "No CHAR data in connection response - performing manual UUID scan...");
                discoveredChannels.clear();
                
                // Manual UUID scan - critical fix for cases where CHAR data not included in response
                try {
                    Log.d(TAG, "Executing manual UUID scan with AT+UUID_SCAN command...");
                    UuidScanResult scanResult = scanUuidChannels();
                    if (scanResult.isSuccess() && scanResult.getChannels() != null && !scanResult.getChannels().isEmpty()) {
                        discoveredChannels = scanResult.getChannels();
                        Log.d(TAG, "✓ Manual UUID scan succeeded - found " + discoveredChannels.size() + " characteristics");
                        for (UuidChannel channel : discoveredChannels) {
                            Log.d(TAG, "  - CH" + channel.channelNum + " UUID:" + channel.uuid +
                                  " (" + channel.properties + ")");
                        }
                    } else {
                        Log.e(TAG, "✗ Manual UUID scan failed: " + scanResult.getError());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "✗ Manual UUID scan exception: " + e.getMessage());
                }
            }

            // Parse connection handle
            Integer handle = parseConnectResponse(connectResponseStr);
            if (handle == null && connectResponseStr.contains("OK")) {
                // Try to get handle from device list
                Log.d(TAG, "Connect OK received, querying device list for handle...");
                handle = getConnectionHandleFromDeviceList();
            }
            if (handle == null) {
                // Fallback to default handle
                Log.d(TAG, "Using default handle 1");
                handle = 1;
            }

            Log.d(TAG, "✓ Initial connection established with handle: " + handle);

            // ====================================================================
            // Step 4-3: Wait for connection to stabilize and verify it's still connected
            // ====================================================================
            Log.d(TAG, "\n[Step 4-3] Waiting for connection to stabilize...");
            Thread.sleep(1000); // Wait 1 second for connection to stabilize

            // Verify connection is still active
            Log.d(TAG, "\n[Step 4-4] Verifying connection stability...");
            String verifyCmd = "AT+CNT_LIST\r\n";
            Log.i(TAG, "[AT CMD] >>> " + verifyCmd.trim());
            ret = At.Lib_ComSend(verifyCmd.getBytes(), verifyCmd.length());

            if (ret == 0) {
                byte[] verifyResponse = new byte[256];
                int[] verifyLen = new int[1];
                ret = At.Lib_ComRecvAT(verifyResponse, verifyLen, 2000, 256);
                String verifyResponseStr = new String(verifyResponse, 0, verifyLen[0]);
                Log.i(TAG, "[AT RSP] <<< " + verifyResponseStr.replace("\r\n", "\\r\\n"));

                // Check if our handle is still in the connected list
                if (!verifyResponseStr.contains(String.valueOf(handle))) {
                    Log.e(TAG, "✗ Connection verification failed - Handle " + handle + " not in connected list");
                    return new ConnectionResult(false, null,
                        "Connection lost during stabilization. Device may have disconnected.");
                }
            }

            connectionHandle = handle;
            Log.d(TAG, "✓ Connection verified and stable with handle: " + handle);
            Log.d(TAG, "=== BLE Connection Process Completed Successfully ===");
            return new ConnectionResult(true, handle, null);

        } catch (Exception e) {
            Log.e(TAG, "Connection error: " + e.getMessage());
            e.printStackTrace();
            return new ConnectionResult(false, null, "Connection error: " + e.getMessage());
        }
    }

    /**
     * Disconnect from the connected device
     * Following BLE_GATT_Connection_Guide.md Step 10
     *
     * Step 10: Disconnect (AT+DISCONNECT)
     *
     * @return true if disconnected successfully
     */
    public boolean disconnect() {
        Log.d(TAG, "=== BLE Disconnect Process Started ===");

        if (connectionHandle == null) {
            Log.w(TAG, "No active connection to disconnect");
            return false;
        }

        Log.d(TAG, "Disconnecting handle: " + connectionHandle);

        try {
            // ====================================================================
            // Step 10: Disconnect (AT+DISCONNECT=1,handle)
            // Parameter: 0=Slave disconnect (Master initiates), 1=Master disconnect
            // ====================================================================
            Log.d(TAG, "\n[Step 10] Disconnecting from Device...");
            String cmd = "AT+DISCONNECT=1," + connectionHandle + "\r\n";
//            String cmd = "AT+DISCONNECT\r\n";
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
            ret = At.Lib_ComRecvAT(response, len, 3000, 256);
            Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

            String responseStr = new String(response, 0, len[0]);
            Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));

            boolean success = responseStr.contains("OK") || responseStr.contains("DISCONNECTED");

            if (success) {
                Log.d(TAG, "✓ Disconnected successfully");
                Log.d(TAG, "=== BLE Disconnect Process Completed Successfully ===");
            } else {
                Log.e(TAG, "Disconnect failed: " + responseStr);
            }

            connectionHandle = null;
            discoveredChannels.clear(); // Clear stored channels on disconnect
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Disconnect error: " + e.getMessage());
            e.printStackTrace();
            connectionHandle = null;
            discoveredChannels.clear(); // Clear stored channels on error
            return false;
        }
    }

    /**
     * Send data to connected device following BLE_GATT_Connection_Guide.md Steps 5-9
     *
     * Step 5: Enable UUID Scan (AT+UUID_SCAN=1)
     * Step 6: Check Connection Handle (AT+CNT_LIST)
     * Step 7: Set TRX Channel (AT+TRX_CHAN)
     * Step 8: Set Transparent Transmission Handle (AT+TTM_HANDLE)
     * Step 9: Send Data (AT+SEND)
     *
     * @param data Data to send (e.g., "order_id=123456745")
     * @param timeout Timeout in milliseconds (recommended: 2000ms)
     * @return SendResult with success status
     */
    public SendResult sendDataComplete(String data, int timeout) {
        Log.d(TAG, "=== BLE Data Send Process Started ===");
        Log.d(TAG, "Data: " + data + " (" + data.length() + " bytes)");

        if (connectionHandle == null) {
            Log.e(TAG, "Not connected to any device");
            return new SendResult(false, "Not connected");
        }

        try {
            int ret; // Return value for AT command operations

            // ====================================================================
            // Step 5: Use stored UUID channels from connection
            // If channels are empty, attempt manual UUID scan
            // ====================================================================
            Log.d(TAG, "\n[Step 5] Using stored UUID characteristics from connection...");

            if (discoveredChannels.isEmpty()) {
                Log.e(TAG, "No characteristics available - attempting manual UUID scan...");
                
                // Attempt manual UUID scan if no characteristics available
                try {
                    Log.d(TAG, "Executing manual UUID scan in sendDataComplete...");
                    UuidScanResult scanResult = scanUuidChannels();
                    if (scanResult.isSuccess() && scanResult.getChannels() != null && !scanResult.getChannels().isEmpty()) {
                        discoveredChannels = scanResult.getChannels();
                        Log.d(TAG, "✓ Manual UUID scan succeeded - found " + discoveredChannels.size() + " characteristics");
                        for (UuidChannel channel : discoveredChannels) {
                            Log.d(TAG, "  - CH" + channel.channelNum + " UUID:" + channel.uuid +
                                  " (" + channel.properties + ")");
                        }
                    } else {
                        Log.e(TAG, "✗ Manual UUID scan failed: " + scanResult.getError());
                        return new SendResult(false, "No GATT characteristics available. Manual scan failed: " + scanResult.getError());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "✗ Manual UUID scan exception: " + e.getMessage());
                    return new SendResult(false, "No GATT characteristics available. Exception: " + e.getMessage());
                }
            }

            Log.d(TAG, "✓ Using " + discoveredChannels.size() + " stored characteristics:");
            for (UuidChannel channel : discoveredChannels) {
                Log.d(TAG, "  - CH" + channel.channelNum + " UUID:" + channel.uuid +
                      " (" + channel.properties + ")");
            }

            List<UuidChannel> channels = discoveredChannels;

            // ====================================================================
            // Step 6: Check Connection Handle (AT+CNT_LIST)
            // ====================================================================
//            Log.d(TAG, "\n[Step 6] Checking Connection Handle...");
//            String cntListCmd = "AT+CNT_LIST\r\n";
//            Log.i(TAG, "[AT CMD] >>> " + cntListCmd.trim());
//            ret = At.Lib_ComSend(cntListCmd.getBytes(), cntListCmd.length());
//            Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);
//
//            if (ret != 0) {
//                Log.e(TAG, "Failed to send CNT_LIST command");
//                return new SendResult(false, "Failed to check connection: " + ret);
//            }
//
//            byte[] cntListResponse = new byte[512];
//            int[] cntListLen = new int[1];
//            ret = At.Lib_ComRecvAT(cntListResponse, cntListLen, 3000, 512);
//            String cntListResponseStr = new String(cntListResponse, 0, cntListLen[0]);
//            Log.i(TAG, "[AT RSP] <<< " + cntListResponseStr.replace("\r\n", "\\r\\n"));
//
//            if (!cntListResponseStr.contains(String.valueOf(connectionHandle))) {
//                Log.e(TAG, "Connection handle " + connectionHandle + " not found in device list");
//                return new SendResult(false, "Device not connected");
//            }

            // ====================================================================
            // Step 7: Set TRX Channel (AT+TRX_CHAN)
            // ====================================================================
            Log.d(TAG, "\n[Step 7] Setting TRX Channel...");
            Log.d(TAG, "Available channels for selection: " + channels.size());
            for (UuidChannel channel : channels) {
                Log.d(TAG, "  - CH" + channel.channelNum + ": UUID=" + channel.uuid + ", Properties=" + channel.properties);
            }

            // Special handling for mCandle GATT Service UUIDs
            // Service UUID: 0000fff0-0000-1000-8000-00805f9b34fb
            // Write UUID: 0000fff1-0000-1000-8000-00805f9b34fb
            // Read UUID: 0000fff2-0000-1000-8000-00805f9b34fb

            // Channel selection for mCandle BLE App
            UuidChannel writeChannel = null;
            UuidChannel notifyChannel = null;

            // ====================================================================
            // Step 1: Try to find mCandle specific UUIDs (FFF1/FFF2)
            // Support multiple representations: fff1, f1ff (little-endian)
            // ====================================================================
            Log.d(TAG, "→ Step 1: Searching for mCandle-specific UUIDs (FFF1/FFF2)...");

            for (UuidChannel channel : channels) {
                String uuidLower = channel.uuid.toLowerCase();

                // Check for FFF1 (Write) - normalized search
                if (uuidLower.contains("fff1") || uuidLower.contains("f1ff")) {
                    if (channel.properties.contains("Write")) {
                        writeChannel = channel;
                        Log.d(TAG, "✓ Found mCandle write channel (FFF1): CH" + channel.channelNum +
                              ", UUID:" + channel.uuid + ", Properties:" + channel.properties);
                    }
                }

                // Check for FFF2 (Notify/Indicate) - normalized search (fixed typo: was f1ff, now f2ff)
                if (uuidLower.contains("fff2") || uuidLower.contains("f2ff")) {
                    if (channel.properties.contains("Notify") || channel.properties.contains("Indicate")) {
                        notifyChannel = channel;
                        Log.d(TAG, "✓ Found mCandle notify channel (FFF2): CH" + channel.channelNum +
                              ", UUID:" + channel.uuid + ", Properties:" + channel.properties);
                    }
                }
            }

            // ====================================================================
            // Step 2: If FFF1/FFF2 not found, use 128-bit custom UUID strategy
            // Exclude standard 16-bit UUIDs (2Axx, 2Bxx, etc.)
            // ====================================================================
            if (writeChannel == null || notifyChannel == null) {
                Log.d(TAG, "→ Step 2: FFF1/FFF2 not found, searching for 128-bit custom UUIDs...");

                // Helper: Detect standard vs custom UUIDs
                // - Standard 16-bit UUIDs are stored as 4-character shorthand (e.g., "052A", "292B")
                // - Custom 128-bit UUIDs are stored as 32-character hex (e.g., "2581E88C...")
                for (UuidChannel channel : channels) {
                    String uuidClean = channel.uuid.replaceAll("-", "").toLowerCase();

                    // Strategy 1: Check UUID length
                    // If less than 32 characters, it's a shorthand 16-bit standard UUID
                    boolean isShorthandUuid = uuidClean.length() < 32;

                    // Strategy 2: For 32-char UUIDs, check if it follows standard pattern
                    // Standard: 0000XXXX-0000-1000-8000-00805f9b34fb (32 chars without hyphens)
                    boolean isStandardPattern = false;
                    if (uuidClean.length() == 32) {
                        isStandardPattern = uuidClean.matches("0000[0-9a-f]{4}00001000800000805f9b34fb");
                    }

                    // Combined check: Skip if standard UUID
                    if (isShorthandUuid || isStandardPattern) {
                        Log.d(TAG, "  - Skipping standard UUID: CH" + channel.channelNum +
                              ", UUID:" + channel.uuid + " (" +
                              (isShorthandUuid ? "16-bit shorthand" : "16-bit extended") + ")");
                        continue;
                    }

                    // This is a custom 128-bit UUID - consider it as a candidate
                    Log.d(TAG, "  - Found custom 128-bit UUID: CH" + channel.channelNum +
                          ", UUID:" + channel.uuid + ", Properties:" + channel.properties);

                    // Select TX channel with smart prioritization
                    // Priority 1: Write channels WITHOUT Notify/Indicate (pure TX channels like CH35)
                    // Priority 2: Write channels WITH Notify/Indicate (like CH34, as fallback)
                    boolean hasWrite = channel.properties.contains("Write Without Response") ||
                                      channel.properties.contains("Write");
                    boolean hasNotify = channel.properties.contains("Notify") ||
                                       channel.properties.contains("Indicate");

                    if (hasWrite) {
                        // Strategy: Always prefer pure TX channels (Write only, no Notify/Indicate)
                        if (!hasNotify) {
                            // This is a pure TX channel - use it immediately, even if we already have a candidate
                            if (writeChannel != null) {
                                Log.d(TAG, "  - Replacing previous TX candidate with pure TX channel");
                            }
                            writeChannel = channel;
                            Log.d(TAG, "✓ Selected pure TX channel (Write only): CH" + channel.channelNum +
                                  ", UUID:" + channel.uuid + ", Properties:" + channel.properties);
                        } else if (writeChannel == null) {
                            // This channel has both Write and Notify/Indicate
                            // Only use it tentatively if we haven't found any TX channel yet
                            writeChannel = channel;
                            Log.d(TAG, "✓ Tentatively selected TX channel (has RX properties): CH" + channel.channelNum +
                                  ", UUID:" + channel.uuid + ", Properties:" + channel.properties);
                        }
                    }

                    // Select RX channel: prioritize Notify / Indicate
                    // Important: Skip channel if already selected as TX
                    if (notifyChannel == null) {
                        if (channel.properties.contains("Notify") ||
                            channel.properties.contains("Indicate")) {
                            // Don't reuse TX channel for RX
                            if (writeChannel == null || channel.channelNum != writeChannel.channelNum) {
                                notifyChannel = channel;
                                Log.d(TAG, "✓ Selected custom RX channel: CH" + channel.channelNum +
                                      ", UUID:" + channel.uuid + ", Properties:" + channel.properties);
                            } else {
                                Log.d(TAG, "  - Skipping CH" + channel.channelNum + " for RX (already selected as TX)");
                            }
                        }
                    }

                    // Continue scanning to find better candidates (don't break early)
                    // This ensures we find pure TX channel (CH35) even if mixed channel (CH34) comes first
                }
            }
            
            // ====================================================================
            // Step 3: Final validation and fallback
            // ====================================================================
            Log.d(TAG, "→ Step 3: Validating channel selection...");

            // Ensure we have at least a write channel
            if (writeChannel == null) {
                Log.e(TAG, "❌ No suitable write channel found!");
                Log.e(TAG, "   Searched for: FFF1 UUID or custom 128-bit UUID with Write property");
                Log.e(TAG, "   Excluded: Standard 16-bit UUIDs (2Axx, 2Bxx, 180x, etc.)");
                return new SendResult(false, "No write channel found - only standard UUIDs available");
            }

            // Ensure notify channel has Notify/Indicate property
            boolean notifyChannelValid = notifyChannel != null &&
                                       (notifyChannel.properties.contains("Notify") ||
                                        notifyChannel.properties.contains("Indicate"));

            if (!notifyChannelValid) {
                Log.w(TAG, "⚠ No RX channel found via FFF2 or custom UUID search");
                Log.w(TAG, "  → Attempting final fallback: ANY non-standard Notify/Indicate channel...");

                // Last resort: Find ANY channel with Notify/Indicate (but still exclude standard UUIDs)
                for (UuidChannel channel : channels) {
                    String uuidClean = channel.uuid.replaceAll("-", "").toLowerCase();

                    // Skip standard UUIDs even in final fallback (same logic as Step 2)
                    boolean isShorthandUuid = uuidClean.length() < 32;
                    boolean isStandardPattern = false;
                    if (uuidClean.length() == 32) {
                        isStandardPattern = uuidClean.matches("0000[0-9a-f]{4}00001000800000805f9b34fb");
                    }

                    if (isShorthandUuid || isStandardPattern) {
                        continue; // Skip standard UUIDs
                    }

                    // Use any custom UUID with Notify/Indicate
                    if (channel.properties.contains("Notify") || channel.properties.contains("Indicate")) {
                        notifyChannel = channel;
                        Log.w(TAG, "✓ Final fallback: Using custom Notify/Indicate channel: CH" +
                              channel.channelNum + ", UUID:" + channel.uuid + ", Properties:" + channel.properties);
                        notifyChannelValid = true;
                        break;
                    }
                }

                // If STILL no valid notify channel, use write channel as last resort
                if (!notifyChannelValid) {
                    notifyChannel = writeChannel;
                    Log.e(TAG, "⚠ CRITICAL: No Notify/Indicate channel found - using TX channel for both!");
                    Log.e(TAG, "  - Using: CH" + writeChannel.channelNum);
                    Log.e(TAG, "  - This may fail on Android 15 if device requires separate RX channel");
                }
            }
            
            // Determine write type (0=Without Response, 1=With Response)
            int writeType = writeChannel.properties.contains("Write Without Response") ? 0 : 1;
            writeType = 1;

            // ====================================================================
            // Step 4: Log final channel selection summary
            // ====================================================================
            Log.d(TAG, "═══════════════════════════════════════════════════════");
            Log.d(TAG, "→ FINAL TRX CHANNEL CONFIGURATION");
            Log.d(TAG, "═══════════════════════════════════════════════════════");
            Log.d(TAG, "TX (Write) Channel:");
            Log.d(TAG, "  - CH" + writeChannel.channelNum);
            Log.d(TAG, "  - UUID: " + writeChannel.uuid);
            Log.d(TAG, "  - Properties: " + writeChannel.properties);
            Log.d(TAG, "  - Write Type: " + writeType + " (" + (writeType == 0 ? "No ACK" : "With ACK") + ")");
            Log.d(TAG, "");
            Log.d(TAG, "RX (Notify) Channel:");
            Log.d(TAG, "  - CH" + notifyChannel.channelNum);
            Log.d(TAG, "  - UUID: " + notifyChannel.uuid);
            Log.d(TAG, "  - Properties: " + notifyChannel.properties);
            Log.d(TAG, "  - Valid: " + (notifyChannelValid ? "✓ YES" : "✗ NO (using fallback)"));
            Log.d(TAG, "═══════════════════════════════════════════════════════");
            
            // Use the channel numbers for AT+TRX_CHAN command
            int writeCh = writeChannel.channelNum;
            int notifyCh = notifyChannel.channelNum;

            // Set TRX Channel
            Log.d(TAG, "Configuring TRX channels...");
            String trxCmd = String.format("AT+TRX_CHAN=%d,%d,%d,%d\r\n",
                connectionHandle, writeCh, notifyCh, writeType);
            Log.i(TAG, "[AT CMD] >>> " + trxCmd.trim());
            ret = At.Lib_ComSend(trxCmd.getBytes(), trxCmd.length());
            Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

            if (ret != 0) {
                Log.e(TAG, "Failed to send TRX_CHAN command");
                return new SendResult(false, "Failed to set TRX channel: " + ret);
            }

            byte[] trxResponse = new byte[256];
            int[] trxLen = new int[1];
            ret = At.Lib_ComRecvAT(trxResponse, trxLen, 3000, 256);
            String trxResponseStr = new String(trxResponse, 0, trxLen[0]);
            Log.i(TAG, "[AT RSP] <<< " + trxResponseStr.replace("\r\n", "\\r\\n"));

            if (!trxResponseStr.contains("OK")) {
                Log.e(TAG, "Failed to set TRX channel");
                return new SendResult(false, "TRX channel response: " + trxResponseStr);
            }

            // ====================================================================
            // Step 8: Set Transparent Transmission Handle (AT+TTM_HANDLE)
            // ====================================================================
//            Log.d(TAG, "\n[Step 8] Setting Transparent Transmission Handle...");
//            String ttmCmd = "AT+TTM_HANDLE=" + connectionHandle + "\r\n";
//            Log.i(TAG, "[AT CMD] >>> " + ttmCmd.trim());
//            ret = At.Lib_ComSend(ttmCmd.getBytes(), ttmCmd.length());
//            Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);
//
//            if (ret != 0) {
//                Log.e(TAG, "Failed to send TTM_HANDLE command");
//                return new SendResult(false, "Failed to set TTM handle: " + ret);
//            }
//
//            byte[] ttmResponse = new byte[256];
//            int[] ttmLen = new int[1];
//            ret = At.Lib_ComRecvAT(ttmResponse, ttmLen, 3000, 256);
//            String ttmResponseStr = new String(ttmResponse, 0, ttmLen[0]);
//            Log.i(TAG, "[AT RSP] <<< " + ttmResponseStr.replace("\r\n", "\\r\\n"));
//
//            if (!ttmResponseStr.contains("OK")) {
//                Log.e(TAG, "Failed to set TTM handle");
//                return new SendResult(false, "TTM handle response: " + ttmResponseStr);
//            }

            // ====================================================================
            // Step 9: Send Data (AT+SEND)
            // ====================================================================
            Log.d(TAG, "\n[Step 9] Sending Data...");
            byte[] dataBytes = data.getBytes();
            int dataLength = dataBytes.length;

            // Step 9-1: Send AT+SEND command
            String sendCmd = String.format("AT+SEND=%d,%d,%d\r\n",
                connectionHandle, dataLength, timeout);
            Log.i(TAG, "[AT CMD] >>> " + sendCmd.trim());
            ret = At.Lib_ComSend(sendCmd.getBytes(), sendCmd.length());
            Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

            if (ret != 0) {
                Log.e(TAG, "Failed to send AT+SEND command");
                return new SendResult(false, "Failed to send command: " + ret);
            }

            // Step 9-2: Wait for "INPUT_BLE_DATA:" prompt
            byte[] sendResponse = new byte[256];
            int[] sendLen = new int[1];
            ret = At.Lib_ComRecvAT(sendResponse, sendLen, 1000, 256);
            String sendResponseStr = new String(sendResponse, 0, sendLen[0]);
            Log.i(TAG, "[AT RSP] <<< " + sendResponseStr.replace("\r\n", "\\r\\n"));

            if (!sendResponseStr.contains("INPUT_BLE_DATA:" + dataLength)) {
                Log.e(TAG, "Module not ready for data input");
                return new SendResult(false, "Unexpected response: " + sendResponseStr);
            }

            // Step 9-3: Send actual data (NO CRLF!)
            Log.d(TAG, "⏳ Module ready, sending data...");
            Log.i(TAG, "[AT DATA] >>> " + data + " (" + dataLength + " bytes, NO CRLF)");
            ret = At.Lib_ComSend(dataBytes, dataLength);
            Log.d(TAG, "[AT DATA] Lib_ComSend returned: " + ret);

            if (ret != 0) {
                Log.e(TAG, "Failed to send data");
                return new SendResult(false, "Failed to send data: " + ret);
            }

            // Step 9-4: Wait for send confirmation
            Thread.sleep(300);
            byte[] confirmResponse = new byte[256];
            int[] confirmLen = new int[1];
            ret = At.Lib_ComRecvAT(confirmResponse, confirmLen, 5000, 4);
            String confirmResponseStr = new String(confirmResponse, 0, confirmLen[0]);
            Log.i(TAG, "[AT RSP] <<< " + confirmResponseStr.replace("\r\n", "\\r\\n"));

            if (confirmResponseStr.contains("OK") || confirmResponseStr.contains("SEND_OK")) {
                Log.d(TAG, "✓ Data sent successfully");
                Log.d(TAG, "=== BLE Data Send Process Completed Successfully ===");
                return new SendResult(true, null);
            } else {
                Log.e(TAG, "Send failed");
                return new SendResult(false, "Send failed: " + confirmResponseStr);
            }

        } catch (InterruptedException e) {
            Log.e(TAG, "Send interrupted: " + e.getMessage());
            return new SendResult(false, "Send interrupted: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Send error: " + e.getMessage());
            e.printStackTrace();
            return new SendResult(false, "Send error: " + e.getMessage());
        }
    }

    /**
     * Scan for UUID channels on the connected device
     * According to AT command protocol, UUID_SCAN=1 enables the feature, but we need to use a different approach
     * to actually get the UUIDs. Let's implement a more robust scanning method.
     * @return UuidScanResult with list of available channels
     */
    public UuidScanResult scanUuidChannels() {
        if (connectionHandle == null) {
            return new UuidScanResult(false, null, "Not connected");
        }

        Log.d(TAG, "Scanning UUID channels");
        
        List<UuidChannel> channels = new ArrayList<>();
        
        // Approach 1: Try to get UUIDs using AT+UUID command (if supported)
        String cmd = "AT+UUID=?\r\n";
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        int ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret == 0) {
            // Receive response
            byte[] response = new byte[2048];
            int[] len = new int[1];
            ret = At.Lib_ComRecvAT(response, len, 2000, 2048);
            Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

            if (ret == 0 && len[0] > 0) {
                String responseStr = new String(response, 0, len[0]);
                Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));
                
                // Parse response for UUID channels
                List<UuidChannel> parsedChannels = parseUuidScanResponse(responseStr);
                if (!parsedChannels.isEmpty()) {
                    channels.addAll(parsedChannels);
                    return new UuidScanResult(true, channels, null);
                }
            }
        }
        
        // Approach 2: Try AT+CHAR command (alternative for some modules)
        cmd = "AT+CHAR=?\r\n";
        Log.i(TAG, "[AT CMD] >>> " + cmd.trim());
        ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
        Log.d(TAG, "[AT CMD] Lib_ComSend returned: " + ret);

        if (ret == 0) {
            // Receive response
            byte[] response = new byte[2048];
            int[] len = new int[1];
            ret = At.Lib_ComRecvAT(response, len, 2000, 2048);
            Log.d(TAG, "[AT CMD] Lib_ComRecvAT returned: " + ret + ", length: " + len[0]);

            if (ret == 0 && len[0] > 0) {
                String responseStr = new String(response, 0, len[0]);
                Log.i(TAG, "[AT RSP] <<< " + responseStr.replace("\r\n", "\\r\\n"));
                
                // Parse response for UUID channels
                List<UuidChannel> parsedChannels = parseUuidScanResponse(responseStr);
                if (!parsedChannels.isEmpty()) {
                    channels.addAll(parsedChannels);
                    return new UuidScanResult(true, channels, null);
                }
            }
        }
        
        // Approach 3: For mCandle app, we know the fixed UUIDs, so we can manually create them
        // Service UUID: 0000fff0-0000-1000-8000-00805f9b34fb
        // Write UUID: 0000fff1-0000-1000-8000-00805f9b34fb
        // Read UUID: 0000fff2-0000-1000-8000-00805f9b34fb
        Log.d(TAG, "Using manual UUID mapping for mCandle app...");
        
        // Create channels manually based on mCandle app's fixed UUIDs
        // Note: Channel numbers may vary by module, but we'll use common values
        channels.add(new UuidChannel(0, "F1FF", "Write Without Response,Write"));
        channels.add(new UuidChannel(1, "F2FF", "Read,Notify"));
        
        Log.d(TAG, "✓ Created manual UUID channels for mCandle app");
        for (UuidChannel channel : channels) {
            Log.d(TAG, "  - CH" + channel.channelNum + " UUID:" + channel.uuid + " (" + channel.properties + ")");
        }
        
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
        String cmd = String.format("AT+TRX_CHAN=%d,%d,%d,%d\r\n",connectionHandle, writeCh, notifyCh, type);
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
        ret = At.Lib_ComRecvAT(response, len, 2000, 3000);
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
        Log.d(TAG, "Connect Return String: " + response);
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
        byte[] response = new byte[2048];
        int[] len = new int[1];
        ret = At.Lib_ComRecvAT(response, len, 3000, 2048);
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
        // Support for 128-bit UUIDs with hyphens
        Pattern pattern = Pattern.compile("-CHAR:(\\d+)\\s+UUID:([^,]+),([^;]+);");
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            try {
                int channelNum = Integer.parseInt(matcher.group(1));
                String uuid = matcher.group(2).trim();
                String properties = matcher.group(3).trim();
                
                // Log the full UUID for debugging - very important for 128-bit UUIDs
                Log.d(TAG, "Found UUID channel: CH" + channelNum + ", UUID:" + uuid + ", Properties:" + properties);
                
                channels.add(new UuidChannel(channelNum, uuid, properties));
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse UUID channel: " + e.getMessage());
                Log.e(TAG, "Raw match: " + matcher.group(0));
            }
        }

        return channels;
    }

    // Parse received data from response
    private byte[] parseReceivedData(String response) {
        Log.d(TAG, "Raw response for parsing: " + response.replace("\r\n", "\\r\\n"));
        
        // Look for AT+SEND response pattern: "+RECEIVED:<handle>,<length> <data> OK"
        // Example: "+RECEIVED:1,10 OUTPUT_BLE_DATA 123456789A OK"
        Pattern pattern = Pattern.compile("\\+RECEIVED:([0-9]+),([0-9]+)\\s+([^\\r\\n]+)\\s+OK");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String data = matcher.group(3);
            Log.d(TAG, "✓ Parsed +RECEIVED data: " + data);
            return data.getBytes();
        }

        // Handle JSON response from mCandle BLE App
        if (response.contains("{") && response.contains("}")) {
            // Extract JSON part from response
            int startIndex = response.indexOf("{");
            int endIndex = response.lastIndexOf("}") + 1;
            if (startIndex < endIndex) {
                String jsonData = response.substring(startIndex, endIndex);
                Log.d(TAG, "✓ Parsed JSON response: " + jsonData);
                return jsonData.getBytes();
            }
        }

        // Also check for direct data response without prefix
        if (response.contains("OK")) {
            // Return everything before OK, trimming whitespace
            int okIndex = response.indexOf("OK");
            String data = response.substring(0, okIndex).trim();
            if (!data.isEmpty()) {
                Log.d(TAG, "✓ Parsed direct data: " + data);
                return data.getBytes();
            }
        }
        
        // If no parsing succeeded, return raw response if it has content
        response = response.trim();
        if (!response.isEmpty()) {
            Log.d(TAG, "✓ Returning raw response: " + response);
            return response.getBytes();
        }

        Log.w(TAG, "No data parsed from response");
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

package com.example.apidemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.example.apidemo.adapter.DeviceAdapter;
import com.example.apidemo.ble.BleConnection;
import com.example.apidemo.ble.Device;
import com.example.apidemo.ble.DividerItemDecoration;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.bartwell.exfilepicker.utils.Utils;
import vpos.apipackage.At;
import vpos.apipackage.Beacon;
import vpos.apipackage.Com;

public class BeaconActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x06;
    private static final int SCAN_DATA_PROMPT_MSG = 0x08;
    private static final int STOP_SCAN_DATA_PROMPT_MSG = 0x10;

    private TextView tv_msg;

    private boolean mStartFlag = false;
    private boolean mEnableFlag = true;
    private String customUUID = "0x0112233445566778899AABBCCDDEEFF0";

    private boolean mMasterFlag = false;
    public  boolean startScan =false;
    private RecyclerView recyclerView;
    private DeviceAdapter deviceAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beacon);

        initView();
        initData();
        initEvent();
    }

    private void initView() {
        tv_msg = findViewById(R.id.tv_msg);

        SwitchCompat flipSwitch = findViewById(R.id.BeaconMaster);
        flipSwitch.setTextOn("MASTER");
        flipSwitch.setTextOff("BEACON");
        recyclerView = findViewById(R.id.recycler_view);
        deviceAdapter = new DeviceAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this));
        recyclerView.setAdapter(deviceAdapter);

        // Set click listener for device list
        deviceAdapter.setOnDeviceClickListener(device -> {
            showBleConnectDialog(device);
        });

        flipSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mMasterFlag = b;
            }
        });
    }

    private void initData() {
        new Thread(() -> {
            String[] mac = new String[1];
            if(0 == At.Lib_GetAtMac(mac)) {
                runOnUiThread(() -> tv_msg.setText(String.format("Hello Beacon-%s !", mac[0])));
            }
        }).start();

        List<Device> newDeviceList = new ArrayList<>();
//        newDeviceList.add(new Device("Device 1", "00:11:22:33:44:55", -60, "0000180F-0000-1000-8000-00805F9B34FB"));
//        newDeviceList.add(new Device("Device 2", "AA:BB:CC:DD:EE:FF", -70, "0000180A-0000-1000-8000-00805F9B34FB"));
        deviceAdapter.setDeviceList(newDeviceList);
    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.btn_beacon_query).setOnClickListener(this);
        findViewById(R.id.btn_beacon_config).setOnClickListener(this);
        findViewById(R.id.btn_beacon_start).setOnClickListener(this);
        findViewById(R.id.btn_beacon_stop).setOnClickListener(this);
        findViewById(R.id.btn_master_scan).setOnClickListener(this);
        findViewById(R.id.btn_master_scanStop).setOnClickListener(this);
        findViewById(R.id.btn_master_scan_config).setOnClickListener(this);
        tv_msg.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        if (!mEnableFlag) {
            new AlertDialog.Builder(this)
                    .setTitle("Disable Beacon?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", (dialogInterface, which) -> {
                        super.finish();
                    })
                    .setNegativeButton("No", (dialogInterface, which) -> {
                        new Thread(() -> {
                            if (mMasterFlag) {
                                At.Lib_EnableMaster(true);
                            } else {
                                At.Lib_EnableBeacon(true);
                            }
                        }).start();
                        super.finish();
                    })
                    .show();
            return;
        }
        if(startScan) {
            startScan = false;
            At.Lib_AtStopScan();
        }
        super.finish();
    }

    public void SendPromptMsg(String strInfo) {
        Message msg = new Message();
        msg.what = RECORD_PROMPT_MSG;
        //Log.e("TAG", "debug crash position:echo9" );
        Bundle b = new Bundle();
        b.putString("MSG", strInfo);
        msg.setData(b);
        //Log.e("TAG", "debug crash position:echo10" );
        promptHandler.sendMessage(msg);
    }

    public void SendPromptScanMsg(String strInfo) {
        Message msg = new Message();
//        Log.e("TAG", "debug crash position:echo11" );
        msg.what = SCAN_DATA_PROMPT_MSG;
        Bundle b = new Bundle();
        b.putString("MSG", strInfo);
        msg.setData(b);
//        Log.e("TAG", "debug crash position:echo11" +strInfo);
        promptHandler.sendMessage(msg);
//        Log.e("TAG", "debug crash position:echo13" );
    }

    public void SendPromptScanStopMsg(String strInfo) {
        Message msg = new Message();
        msg.what = STOP_SCAN_DATA_PROMPT_MSG;
        Bundle b = new Bundle();
        b.putString("MSG", strInfo);
        msg.setData(b);
        promptHandler.sendMessage(msg);

    }
    @SuppressLint("HandlerLeak")
    private Handler promptHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            String strInfo = b.getString("MSG");

            switch (msg.what) {
                case RECORD_PROMPT_MSG:
                    if (strInfo.equals("")) {
                        tv_msg.setText("");
                        tv_msg.scrollTo(0, 0);
                    } else {
//                        tv_msg.append(strInfo);
                        tv_msg.setText(strInfo);

                        int offset = tv_msg.getLineCount() * tv_msg.getLineHeight();
                        if (offset > tv_msg.getHeight())
                            tv_msg.scrollTo(0, offset - tv_msg.getHeight());
                    }
                    break;
                case STOP_SCAN_DATA_PROMPT_MSG:
                    if (strInfo.equals("")) {
                        tv_msg.setText("");
                        tv_msg.scrollTo(0, 0);
                    } else {
//                        tv_msg.append(strInfo);
                        tv_msg.setText(strInfo);

                        int offset = tv_msg.getLineCount() * tv_msg.getLineHeight();
                        if (offset > tv_msg.getHeight())
                            tv_msg.scrollTo(0, offset - tv_msg.getHeight());
                    }
                    deviceAdapter.clearDeviceList();
                    promptHandler.removeCallbacksAndMessages(null);
                    break;
                case SCAN_DATA_PROMPT_MSG:
//                    Log.e("TAG", "debug crash position:echo1" );
                    if (strInfo.equals("")||strInfo.length()<6) {
//                        tv_msg.setText("");
//                        tv_msg.scrollTo(0, 0);
//                        Log.e("TAG", "debug crash position:echo2" );
                        deviceAdapter.removeDisappearDevice();
                    } else {
//                        tv_msg.append(strInfo);
//                        tv_msg.setText(strInfo);
                        try {
                            Log.e("TAG", "handleMessage: "+ strInfo);
                            JSONArray jsonArray = new JSONArray(strInfo);
//                            Log.e("TAG", "debug crash position:echo3" );
                            List<Device> newDeviceList = new ArrayList<>();
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject deviceJson = jsonArray.getJSONObject(i);

                                if (!deviceJson.has("MAC") || !deviceJson.has("RSSI")) {
                                    Log.w("JSON Parse", "Missing required fields in device: "+deviceJson);
                                    continue;
                                }
                                // ��ȫ����ת��
                                String mac = deviceJson.getString("MAC");
                                int rssi = deviceJson.optInt("RSSI", -999); // ʹ��optInt�ṩĬ��ֵ

                                // ��Χ��Ч��У��
                                if(rssi < -120 || rssi > 20) {
                                    Log.w("RSSI Range", "Invalid RSSI value: "+rssi+" for MAC: "+mac);
                                    continue;
                                }

                                // Ƕ�׶���ȫ����
                                String deviceName = null;
                                String uuid = null;
                                JSONObject advObj = deviceJson.optJSONObject("ADV");
                                JSONObject rspObj = deviceJson.optJSONObject("RSP");

                                if(advObj != null) {
                                    deviceName = advObj.optString("Device Name", null);
                                    uuid = advObj.optString("Service UUIDs", null);
                                }
                                if(rspObj != null && uuid == null) {
                                    uuid = rspObj.optString("Service UUIDs", null);
                                }
                                if(rspObj != null && deviceName == null) {
                                    deviceName = rspObj.optString("Device Name", null);
                                }


                                // ʱ�����ȫ��ȡ
                                long timestamp = deviceJson.optLong("Timestamp", System.currentTimeMillis());

                                deviceAdapter.updateDevice(new Device(
                                        deviceName,
                                        mac,
                                        rssi,
                                        uuid,
                                        timestamp
                                ));

                            }

                        } catch (JSONException e) {
                            Log.e("TAG", "Handler promptHandler 0000: JSONException"+e.getMessage() );
//                            throw new RuntimeException(e);
                            break;
                        }
                        int offset = tv_msg.getLineCount() * tv_msg.getLineHeight();
                        if (offset > tv_msg.getHeight())
                            tv_msg.scrollTo(0, offset - tv_msg.getHeight());
//                        Log.e("TAG", "debug crash position:echo8" );
                    }
                    break;
                default:
                    break;
            }
        }
    };
    private static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        if(len%2==1)
            len--;
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
    private static String bytesToHex(byte[] bytes,int len) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<len;i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
    public static JSONObject parseAdvertisementData(byte[] advertisementData) throws JSONException {
//        Map<String, String> parsedData = new HashMap<>();
//        byte[] advertisementData =new byte[advertiseData.length()/2];
        JSONObject parsedData = new JSONObject();
        int offset = 0;
        while (offset < advertisementData.length) {
            int length = advertisementData[offset++] & 0xFF;
            if (length == 0) break;

            int type = advertisementData[offset] & 0xFF;
            offset++;

            byte[] data = new byte[length - 1];
            if(length-1>advertisementData.length-offset)//data format issue.
            {
                return null;
            }
            System.arraycopy(advertisementData, offset, data, 0, length - 1);
            offset += length - 1;

            switch (type) {
                case 0x01: // Flags
                    parsedData.put("Flags", bytesToHex(data));
                    break;
                case 0x02: // Incomplete List of 16-bit Service Class UUIDs
                case 0x03: // Complete List of 16-bit Service Class UUIDs
                    parsedData.put("Service UUIDs", bytesToHex(data));
                    break;
                case 0x04: // Incomplete List of 32-bit Service Class UUIDs
                case 0x05: // Complete List of 32-bit Service Class UUIDs
                    parsedData.put("Service UUIDs", bytesToHex(data));
                    break;
                case 0x06: // Incomplete List of 128-bit Service Class UUIDs
                case 0x07: // Complete List of 128-bit Service Class UUIDs
                    parsedData.put("Service UUIDs", bytesToHex(data));
                    break;
                case 0x08: // Shortened Local Name
                case 0x09: // Complete Local Name
                    parsedData.put("Device Name", new String(data));
                    break;
                case 0x0A: // Complete Local Name
//                    byte [] tx_power=hexStringToByteArray(new String(data));
                    parsedData.put("TX Power Level", data[0]);
                    break;
                case 0xFF: // Manufacturer Specific Data
                    parsedData.put("Manufacturer Data", bytesToHex(data));
                    break;
                default:
                    parsedData.put("Unknown Data (" + type + ")", bytesToHex(data));
                    break;
            }
        }

        return parsedData;
    }

private static JSONObject parsePayload(String payload) {
    JSONObject result = new JSONObject();
    int index = 0;

    while (index < payload.length()) {
        // ��������Ƿ�Խ��
        if (index + 2 > payload.length()) {
            break;
        }
        int length = Integer.parseInt(payload.substring(index, index + 2), 16);
        index += 2;

        // ��������Ƿ�Խ��
        if (index + 2 > payload.length()) {
            break;
        }
        int type = Integer.parseInt(payload.substring(index, index + 2), 16);
        index += 2;

        // ��������Ƿ�Խ��
        if (index + length * 2 > payload.length()) {
            break;
        }
        String data = payload.substring(index, index + length * 2);
        index += length * 2;

        try {
            result.put("Type " + type, data);
        } catch (JSONException e) {
            Log.e("TAG", "parsePayload:Type "+e.getMessage() );
//            throw new RuntimeException(e);
            return null;
        }
    }

    return result;
}

    Runnable  recvScanData = new Runnable (){
        byte[] recvData =new byte[2048];
        int[] recvDataLen =new int[2];
        String lineLeft="";
        public void run() {
            while(startScan)
            {
                int ret = At.Lib_ComRecvAT(recvData, recvDataLen, 20, 2048);
                Log.e("TAG", "runLib_ComRecvAT: recvDataLen"+recvDataLen[0] );
                Log.e("TAG", "Lib_ComRecvAT recvData: "+bytesToHex(recvData,recvDataLen[0]));
                Map<String, JSONObject> deviceMap = new HashMap<>();
                boolean startProcessing = false;
                // String buff= lineLeft+new String(recvData);
                String buff= lineLeft+new String(recvData, 0, recvDataLen[0]);
                // String []data=buff.split("\r\n|\r|\n");
                String []data=buff.split("\\r\\n|\\r|\\n", -1); // ����λ�������ַ���
                //Log.e("TAG", "debug crash position:echo21" );
                int lineCount=data.length;
                // if(lineCount>0)//each time response data left last line ,for maybe data not recv all.
                //     lineLeft = data[lineCount-1];
                // else
                //     lineLeft="";
                // �������һ��δ�������
                lineLeft = (data.length > 0) ? data[data.length-1] : "";
                //for (String line : data)
                for (int i=0;i<lineCount-1;i++)
                {
                    String line =data[i];
//                    Log.e("TAG", "debug crash position:echo22" );
                    if (line.startsWith("MAC:")) {
                        startProcessing = true;
                        String[] parts = line.split(",",3);
                        if(parts.length < 3) {
                            continue;
                        }
                        
                        String mac = parts[0].split(":",2)[1].trim();
                        String rssi = parts[1].split(":")[1].trim();
                        int irssi =0;
                        try {
                            irssi = Integer.parseInt(rssi); // ��֤ RSSI �Ƿ�Ϊ��Ч����
                        } catch (NumberFormatException e) {
                            Log.e("TAG", "Invalid RSSI value: " + rssi);
                            continue;
                        }
                        String payload = parts[2].split(":",2)[1].trim();
                        if((payload.length()>62)||(payload.length()%2!=0))
                            continue;
//                        Log.e("TAG", "debug crash position:echo20" );
                        JSONObject device;
                        if (deviceMap.containsKey(mac)) {
                            device = deviceMap.get(mac);
                        } else {
                            device = new JSONObject();
                            try {
                                device.put("MAC", mac);
                            } catch (JSONException e) {
                                Log.e("TAG", "Handler runLib_ComRecvAT mac 0000: JSONException"+e );
                                //throw new RuntimeException(e);
								 continue;
                            }
                            deviceMap.put(mac, device);
                        }
//                        Log.e("TAG", "debug crash position:echo19" );
                        if (parts[2].startsWith("RSP")) {

                            try {
                                assert device != null;
                                device.put("RSP_org", payload);
                                device.put("RSP", parseAdvertisementData(hexStringToByteArray(payload)));
                            } catch (JSONException e) {
                                Log.e("TAG", "Runnable 444: JSONException"+e );
//                                throw new RuntimeException(e);
                                continue;
                            }

                        } else if (parts[2].startsWith("ADV")) {
                            //device.put("ADV", parsePayload(payload));
                            try {
                                assert device != null;
                                device.put("ADV_org", payload);
                                device.put("ADV", parseAdvertisementData(hexStringToByteArray(payload)));
                            } catch (JSONException e) {
                                Log.e("TAG", "Runnable 333: JSONException"+e );
//                                throw new RuntimeException(e);
                                continue;
                            }
                        }
                        //Log.e("TAG", "debug crash position:echo18" );
                        try {
                            assert device != null;
                           // Log.e("TAG", "debug crash position:echo18"+rssi );
                            device.put("RSSI", irssi);
                        } catch (JSONException e) {
                            Log.e("TAG", "Runnable 222: JSONException"+e.getMessage() );
//                            throw new RuntimeException(e);
                            continue;
                        }
//                        Log.e("TAG", "debug crash position:echo17" );
                        // ���ʱ����ֶ�
                        try {
//                                long curr_time=System.currentTimeMillis();
                            device.put("Timestamp", System.currentTimeMillis());
                        } catch (JSONException e) {
                            //Log.e("TAG", "Runnable 000: JSONException"+e );
//                            throw new RuntimeException(e);
                            continue;
                        }
//                        Log.e("TAG", "debug crash position:echo16" );
                    } else if (startProcessing) {
                        // ����Ѿ���ʼ����MAC���ݣ���������MAC��ͷ�����ݣ�������
                        continue;
                    }
//                    Log.e("TAG", "debug crash position:echo14---"+);

                }

                // ������ת��ΪJSON����

                JSONArray jsonArray = new JSONArray(deviceMap.values());
                try {
//                        SendPromptMsg("" + jsonArray.toString(4));
//                    Log.e("TAG", "debug crash position:echo14" );
                    //if(jsonArray.)
                    SendPromptScanMsg("" + jsonArray.toString(4));
//                    Log.e("TAG", "debug crash position:echo15" );
                } catch (JSONException e) {
                    Log.e("TAG", "Runnable 111: JSONException"+ e.getMessage() );
//                    throw new RuntimeException(e);
                }
            };
        }

    };
    private boolean isValidMacAddress(String macAddress) {
        // �򵥵�MAC��ַ��ʽ��֤�������Ը���ʵ����������޸�
        return macAddress.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }

    private boolean isValidManufacturerId(String manufacturerId) {
        // �򵥵�ManufacturerId��ʽ��֤�������Ը���ʵ����������޸�
        return manufacturerId.matches("^[0-9A-Fa-f]+$");
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();

        if (R.id.btn_beacon_config == viewId) {
            if (mStartFlag) {
                Log.i("unique Start", "start---------->flag=" + mStartFlag);
                return;
            }
            mStartFlag = true;
            SendPromptMsg("");

            View inputLayout = LayoutInflater.from(this).inflate(R.layout.item_beacon_info, null);
            EditText etCompanyId = inputLayout.findViewById(R.id.etCompanyId);
            EditText etMajorUuid = inputLayout.findViewById(R.id.etMajorUuid);
            EditText etMinorUuid = inputLayout.findViewById(R.id.etMinorUuid);
            EditText etCustomUuid = inputLayout.findViewById(R.id.etCustomUuid);

            SharedPreferences sp = getSharedPreferences("beaconInfo", MODE_PRIVATE);
            etCompanyId.setText(sp.getString("companyId", "4C00"));
            etMajorUuid.setText(sp.getString("majorUuid", "0708"));
            etMinorUuid.setText(sp.getString("minorUuid", "0506"));
            etCustomUuid.setText(sp.getString("customUuid", "0112233445566778899AABBCCDDEEFF0"));

            new AlertDialog.Builder(this)
                    .setTitle("Config Beacon")
                    .setView(inputLayout)
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialogInterface, which) -> {
                        String companyId = etCompanyId.getText().toString().trim();
                        String majorUuid = etMajorUuid.getText().toString().trim();
                        String minorUuid = etMinorUuid.getText().toString().trim();
                        String customUuid = etCustomUuid.getText().toString().trim();

                        if (TextUtils.isEmpty(companyId)
                                || TextUtils.isEmpty(majorUuid)
                                || TextUtils.isEmpty(minorUuid)
                                || TextUtils.isEmpty(customUuid)) {
                            SendPromptMsg("Empty Field!\n");
                            mStartFlag = false;
                            return;
                        }

                        new Thread(() -> {
                            Beacon beacon = new Beacon(companyId, majorUuid, minorUuid, customUuid);
                            int ret = At.Lib_SetBeaconParams(beacon);
                            if (ret == 0) {
                                SendPromptMsg("Config beacon succeeded!\n"
                                        + "Company ID: 0x" + beacon.companyId + "\n"
                                        + "Major: 0x" + beacon.major + "\n"
                                        + "Minor: 0x" + beacon.minor + "\n"
                                        + "Custom UUID: 0x" + beacon.customUuid + "\n");

                                SharedPreferences.Editor editor = sp.edit();
                                editor.putString("companyId", companyId);
                                editor.putString("majorUuid", majorUuid);
                                editor.putString("minorUuid", minorUuid);
                                editor.putString("customUuid", customUuid);
                                editor.apply();
                            } else {
                                SendPromptMsg("Config beacon failed, return: " + ret + "\n");
                            }
                            mStartFlag = false;
                        }).start();
                    })
                    .setNegativeButton("Cancel", (dialogInterface, which) -> {
                        SendPromptMsg("Cancel Config Beacon.\n");
                        mStartFlag = false;
                    })
                    .show();
            return;
        }
        else if (R.id.btn_master_scan_config == viewId) {
            SendPromptMsg("SCAN CONFIG\n");
            int ret = 0;
            String[] mac = new String[1];
            startScan=true;

            View inputLayout = LayoutInflater.from(this).inflate(R.layout.item_scan_filter_info, null);
            EditText etMacAddress = inputLayout.findViewById(R.id.etMacAddress);
            EditText etBroadcastName = inputLayout.findViewById(R.id.etBroadcastName);
            EditText etRssi = inputLayout.findViewById(R.id.etRssi);
            EditText etManufacturerId = inputLayout.findViewById(R.id.etManufacturerId);
            EditText etData = inputLayout.findViewById(R.id.etData);

            SharedPreferences sp = getSharedPreferences("scanInfo", MODE_PRIVATE);
            etMacAddress.setText(sp.getString("macAddress", ""));
            etBroadcastName.setText(sp.getString("broadcastName", ""));
            etRssi.setText(sp.getString("rssi", "0"));
            etManufacturerId.setText(sp.getString("manufacturerId", ""));
            etData.setText(sp.getString("data", ""));

            new AlertDialog.Builder(this)
                    .setTitle("Config Scan Filter")
                    .setView(inputLayout)
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialogInterface, which) -> {
                        String macAddress = etMacAddress.getText().toString().trim();
                        String broadcastName = etBroadcastName.getText().toString().trim();
                        String rssi = etRssi.getText().toString().trim();
                        String manufacturerId = etManufacturerId.getText().toString().trim();
                        String data = etData.getText().toString().trim();

                        if (!TextUtils.isEmpty(macAddress)&&!isValidMacAddress(macAddress)) {
                            SendPromptMsg("Invalid MAC Address!\n");
//                            mStartFlag = false;
                            return;
                        }

//                        if (TextUtils.isEmpty(broadcastName)) {
//                            SendPromptMsg("Broadcast Name is required!\n");
//                            mStartFlag = false;
//                            return;
//                        }

                        if (TextUtils.isEmpty(rssi)) {
                            SendPromptMsg("RSSI is required!\n");
//                            mStartFlag = false;

//                            return;
                        }

                        if (TextUtils.isEmpty(manufacturerId) || !isValidManufacturerId(manufacturerId)) {
                            SendPromptMsg("Invalid ManufacturerId!\n");
//                            mStartFlag = false;
//                            return;
                        }

                        // �����ﴦ����������ݣ����籣�浽SharedPreferences�������������
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putString("macAddress", macAddress);
                        editor.putString("broadcastName", broadcastName);
                        editor.putString("rssi", rssi);
                        editor.putString("manufacturerId", manufacturerId);
                        editor.putString("data", data);
                        editor.apply();

                        // ����ִ����������
                    })
                    .setNegativeButton("Cancel", null)
                    .show();



//

            if (ret == 0) {
                SendPromptMsg("NEW DEVICE DISCOVERED: " + mac[0] + "\n");
            } else {
                SendPromptMsg("ERROR WHILE SCANNING, RET = " + ret + "\n");
            }
        }else if (R.id.btn_master_scan == viewId) {
            SendPromptMsg("SCANNING\n");
            int ret = 0;
            ret = At.Lib_EnableMaster(true);

            String[] mac = new String[1];
            startScan=true;
            SharedPreferences sp = getSharedPreferences("scanInfo", MODE_PRIVATE);
            Log.e("TAG", "onClick: "+"macAddress"+sp.getString("macAddress", ""));
            Log.e("TAG", "onClick: "+"broadcastName"+sp.getString("broadcastName", ""));
            Log.e("TAG", "onClick: "+"rssi"+sp.getString("rssi", ""));
            Log.e("TAG", "onClick: "+"manufacturerId"+sp.getString("manufacturerId", ""));
            Log.e("TAG", "onClick: "+"data"+sp.getString("data", ""));
            ret = At.Lib_AtStartNewScan(sp.getString("macAddress", ""), sp.getString("broadcastName", ""),
                    -Integer.parseInt(sp.getString("rssi", "0")),sp.getString("manufacturerId", ""),sp.getString("data", ""));
            if(ret==0) {
                // recvScanData.start();
                new Thread(recvScanData).start();
            }

            if (ret == 0) {
                SendPromptMsg("NEW DEVICE DISCOVERED: " + mac[0] + "\n");
            } else {
                SendPromptMsg("ERROR WHILE SCANNING, RET = " + ret + "\n");
            }
        }


        new Thread() {

            public void run() {
                if (mStartFlag) {
                    Log.i("unique Start", "start---------->flag=" + mStartFlag);
                    return;
                }
                mStartFlag = true;
                SendPromptMsg("");

                if (R.id.btn_beacon_query == viewId) {
                    Beacon beacon = new Beacon();
                    int ret = At.Lib_GetBeaconParams(beacon);
                    if (ret == 0) {
                        SendPromptMsg("Query beacon succeeded!\n"
                                + "Company ID: " + beacon.companyId + "\n"
                                + "Major: " + beacon.major + "\n"
                                + "Minor: " + beacon.minor + "\n"
                                + "Custom UUID: " + beacon.customUuid + "\n");
                    } else {
                        SendPromptMsg("Query beacon failed, return: " + ret + "\n");
                    }
                }

                else if (R.id.btn_beacon_start == viewId) {

                    int ret = 0;
                    if (mMasterFlag) {
                        ret = At.Lib_EnableMaster(true);
                    } else {
                        ret = At.Lib_EnableBeacon(true);
                    }

                    if (ret == 0) {
                        mEnableFlag = true;
                        if (mMasterFlag) {
                            SendPromptMsg("Start master succeeded!\n");
                        } else {
                            SendPromptMsg("Start beacon succeeded!\n");
                        }
                        SendPromptMsg("Note: Effective immediately; Power-off preservation.\n");
                    } else {
                        SendPromptMsg("Start beacon failed, return: " + ret + "\n");
                    }
                }
                else if (R.id.btn_beacon_stop == viewId) {
                    int ret = 0;
                    if (mMasterFlag) {
                        ret = At.Lib_EnableMaster(false);
                    } else {
                        ret = At.Lib_EnableBeacon(false);
                    }
                    if (ret == 0) {
                        mEnableFlag = false;
                        if (mMasterFlag) {
                            SendPromptMsg("Stop master succeeded!\n");
                        } else {
                            SendPromptMsg("Stop beacon succeeded!\n");
                        }
                        SendPromptMsg("Note: Effective immediately; Power-off preservation.\n");
                    } else {
                        SendPromptMsg("Stop beacon failed, return: " + ret + "\n");
                    }
                } else if (R.id.btn_master_scanStop == viewId) {
                    SendPromptMsg("SCANNING stop\n");
                    int ret = 0;
                    startScan=false;

//                    recvScanData.interrupt();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.e("TAG", "R.id.btn_beacon_start 0000: JSONException"+e );
                        throw new RuntimeException(e);
                    }
                    ret = At.Lib_AtStopScan();

                    if (ret == 0) {
                        SendPromptScanStopMsg("STOP SCAN SUCCESS: " + "\n");
                    } else {
                        SendPromptMsg("ERROR WHILE STOP SCANG, RET = " + ret + "\n");
                    }


                }

                mStartFlag = false;
            }
        }.start();
    }

    private void showBleConnectDialog(Device device) {
        // Stop scanning before connecting
        if (startScan) {
            startScan = false;
            At.Lib_AtStopScan();
        }

        // Inflate dialog layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ble_connect, null);

        // Get views
        TextView tvDeviceName = dialogView.findViewById(R.id.tvDeviceName);
        TextView tvDeviceMac = dialogView.findViewById(R.id.tvDeviceMac);
        TextView tvConnectionStatus = dialogView.findViewById(R.id.tvConnectionStatus);
        ProgressBar progressConnection = dialogView.findViewById(R.id.progressConnection);
        EditText etSendData = dialogView.findViewById(R.id.etSendData);
        Button btnConnect = dialogView.findViewById(R.id.btnConnect);
        Button btnSend = dialogView.findViewById(R.id.btnSend);
        Button btnDisconnect = dialogView.findViewById(R.id.btnDisconnect);
        Button btnClose = dialogView.findViewById(R.id.btnClose);
        TextView tvReceivedLog = dialogView.findViewById(R.id.tvReceivedLog);

        // Set device info
        String deviceName = device.getDeviceName();
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "Unknown Device";
        }
        tvDeviceName.setText(deviceName);
        tvDeviceMac.setText(device.getMacAddress());

        // Create BLE connection instance
        BleConnection bleConnection = new BleConnection();

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // StringBuilder for log
        StringBuilder logBuilder = new StringBuilder();

        // Helper method to append log
        Runnable appendLogRunnable = () -> {};

        // Connect button click
        btnConnect.setOnClickListener(v -> {
            progressConnection.setVisibility(View.VISIBLE);
            tvConnectionStatus.setText("연결 중...");
            tvConnectionStatus.setTextColor(Color.parseColor("#FF9800"));
            btnConnect.setEnabled(false);

            new Thread(() -> {
                BleConnection.ConnectionResult result = bleConnection.connectToDevice(device.getMacAddress());

                runOnUiThread(() -> {
                    progressConnection.setVisibility(View.GONE);

                    if (result.isSuccess()) {
                        tvConnectionStatus.setText("연결됨 (Handle: " + result.getHandle() + ")");
                        tvConnectionStatus.setTextColor(Color.parseColor("#4CAF50"));
                        btnSend.setEnabled(true);
                        btnDisconnect.setEnabled(true);
                        btnConnect.setEnabled(false);

                        logBuilder.append("Connected to ").append(device.getMacAddress()).append("\n");
                        logBuilder.append("Handle: ").append(result.getHandle()).append("\n");
                        tvReceivedLog.setText(logBuilder.toString());

                        // Scan UUID channels after connection
                        new Thread(() -> {
                            BleConnection.UuidScanResult uuidResult = bleConnection.scanUuidChannels();
                            runOnUiThread(() -> {
                                if (uuidResult.isSuccess() && uuidResult.getChannels() != null) {
                                    logBuilder.append("UUID Channels:\n");
                                    for (BleConnection.UuidChannel channel : uuidResult.getChannels()) {
                                        logBuilder.append("  CH").append(channel.channelNum)
                                                .append(": ").append(channel.uuid).append("\n");
                                    }
                                    tvReceivedLog.setText(logBuilder.toString());

                                    // Auto set TRX channel if channels found
                                    if (!uuidResult.getChannels().isEmpty()) {
                                        // Use first writable channel (this may need adjustment)
                                        for (BleConnection.UuidChannel channel: uuidResult.getChannels()) {
                                            if (channel.uuid.equals("AB90785634127298EFCDAB9078563412")) {
                                                bleConnection.setTrxChannel(channel.channelNum, channel.channelNum, 1);
                                                logBuilder.append("TRX Channel set\n");
                                                tvReceivedLog.setText(logBuilder.toString());
                                            }
                                        }
                                    }
                                }
                            });
                        }).start();

                    } else {
                        tvConnectionStatus.setText("연결 실패");
                        tvConnectionStatus.setTextColor(Color.parseColor("#F44336"));
                        btnConnect.setEnabled(true);

                        logBuilder.append("Connection failed: ").append(result.getError()).append("\n");
                        tvReceivedLog.setText(logBuilder.toString());
                    }
                });
            }).start();
        });

        // Send button click
        btnSend.setOnClickListener(v -> {
            String sendData = etSendData.getText().toString().trim();
            if (sendData.isEmpty()) {
                logBuilder.append("Error: No data to send\n");
                tvReceivedLog.setText(logBuilder.toString());
                return;
            }

            btnSend.setEnabled(false);

            new Thread(() -> {
                BleConnection.SendResult result = bleConnection.sendData(sendData.getBytes(), 3000);

                runOnUiThread(() -> {
                    btnSend.setEnabled(true);

                    if (result.isSuccess()) {
                        logBuilder.append("TX: ").append(sendData).append("\n");
                        etSendData.setText("");
                    } else {
                        logBuilder.append("Send failed: ").append(result.getError()).append("\n");
                    }
                    tvReceivedLog.setText(logBuilder.toString());
                });

                // Try to receive response
                        BleConnection.ReceiveResult recvResult = bleConnection.receiveData(2000);
                        if (recvResult.isSuccess() && recvResult.getData() != null) {
                            String receivedData = new String(recvResult.getData());
                            runOnUiThread(() -> {
                                logBuilder.append("RX: ").append(receivedData).append("\n");
                                tvReceivedLog.setText(logBuilder.toString());
                            });
                        } else if (!recvResult.isTimeout()) {
                            // Only log error if it's not a timeout
                            runOnUiThread(() -> {
                                logBuilder.append("Receive error: ").append(recvResult.getError()).append("\n");
                                tvReceivedLog.setText(logBuilder.toString());
                            });
                        }
            }).start();
        });

        // Disconnect button click
        btnDisconnect.setOnClickListener(v -> {
            new Thread(() -> {
                boolean success = bleConnection.disconnect();

                runOnUiThread(() -> {
                    if (success) {
                        tvConnectionStatus.setText("연결 안됨");
                        tvConnectionStatus.setTextColor(Color.parseColor("#F44336"));
                        btnSend.setEnabled(false);
                        btnDisconnect.setEnabled(false);
                        btnConnect.setEnabled(true);

                        logBuilder.append("Disconnected\n");
                        tvReceivedLog.setText(logBuilder.toString());
                    } else {
                        logBuilder.append("Disconnect failed\n");
                        tvReceivedLog.setText(logBuilder.toString());
                    }
                });
            }).start();
        });

        // Close button click
        btnClose.setOnClickListener(v -> {
            // Disconnect if still connected
            if (bleConnection.isConnected()) {
                new Thread(() -> {
                    bleConnection.disconnect();
                }).start();
            }
            dialog.dismiss();
        });

        dialog.show();
    }
}

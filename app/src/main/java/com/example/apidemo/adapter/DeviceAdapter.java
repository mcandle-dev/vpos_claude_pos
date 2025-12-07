package com.example.apidemo.adapter;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apidemo.R;
import com.example.apidemo.ble.Device;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int removeBleTime = 3*1000;//3s内没有重新扫描到,就从列表移除.
    private List<Device> deviceList;
    private OnDeviceClickListener clickListener;

    public interface OnDeviceClickListener {
        void onDeviceClick(Device device);
    }

    public DeviceAdapter() {
        this.deviceList = new ArrayList<>();
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
            return new DeviceViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DeviceViewHolder) {
            Device device = deviceList.get(position - 1); // 减去表头的位置
            ((DeviceViewHolder) holder).deviceNameTextView.setText(device.getDeviceName());
            ((DeviceViewHolder) holder).macAddressTextView.setText(device.getMacAddress());
            ((DeviceViewHolder) holder).rssiTextView.setText(String.valueOf(device.getRssi()));
            ((DeviceViewHolder) holder).serviceUuidTextView.setText(device.getServiceUuid());

            // Set click listener
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onDeviceClick(device);
                }
            });
        }
    }
    @Override
    public int getItemCount() {
        return deviceList.size()+1;
    }
    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        } else {
            return TYPE_ITEM;
        }
    }  // 添加 setDeviceList 方法

    public void setDeviceList(List<Device> deviceList) {
        this.deviceList = deviceList;
        notifyDataSetChanged();
    }
    public void addDevice(Device device) {
        for (int i = 0; i < deviceList.size(); i++) {
            if (deviceList.get(i).getMacAddress().equals(device.getMacAddress())) {
                // 更新已存在设备的数据
                deviceList.get(i).setRssi(device.getRssi()); // 假设Device类有setRssi方法
                deviceList.get(i).setDeviceName(device.getDeviceName()); // 可选，根据需要更新其他字段
                deviceList.get(i).setServiceUuid(device.getServiceUuid()); // 可选
                // ... 更新其他需要的字段 ...

                // 通知列表更新
                notifyItemChanged(i + 1); // +1 是因为我们有表头，所以实际位置要+1
                return; // 更新后直接返回，避免重复添加
            }
        }
        // 如果没有找到重复MAC，则添加新设备
        deviceList.add(device);
        notifyItemInserted(deviceList.size()); // 注意这里应该是 deviceList.size()，因为我们没有表头时才用 size()-1
    }
    @SuppressLint("NotifyDataSetChanged")
    public void clearDeviceList() {
        deviceList.clear();
        notifyDataSetChanged();
    }
    public void updateDevice(Device device) {
        int position = -1;
//        Log.e("TAG0", "updateDevice: size"+ deviceList.size()+"device.time"+device.getTimestamp());
        for (int i = 0; i < deviceList.size(); i++) {
            if (deviceList.get(i).getMacAddress().equals(device.getMacAddress())) {
//                Log.e("TAG", "updateDevice: "+"device.getMacAddress()"+device.getMacAddress());
                position = i;
                break;
            }
        }
        if (position != -1) {
//            Log.e("TAG0", "updateDevice: update"+"postion"+position+"mac"+ deviceList.get(position).getMacAddress());
            if(device.getDeviceName()==null||device.getDeviceName().isEmpty())
            {
                device.setDeviceName(deviceList.get(position).getDeviceName());
            }
            if(device.getServiceUuid()==null||device.getServiceUuid().isEmpty())
            {
                device.setServiceUuid(deviceList.get(position).getServiceUuid());
            }
            deviceList.set(position, device);
            notifyItemChanged(position+1);
        } else {
//            Log.e("TAG0", "updateDevice: add device mac"+ device.getMacAddress());
            deviceList.add(device);
//            Log.e("TAG0", "updateDevice: size"+ deviceList.size());
            notifyItemInserted(deviceList.size());
        }
        removeDisappearDevice();
    }
//     public void removeDisappearDevice() {
//         int position=0;
//         long current_timeStamp=System.currentTimeMillis();
//         for(int i=0;i<deviceList.size();i++){
//             Log.e("TAG0", "removeDisappearDevice: size"+ deviceList.size()+"curr time"+current_timeStamp+"old"+deviceList.get(i).getTimestamp());
//             if(current_timeStamp-deviceList.get(i).getTimestamp()>removeBleTime) {
//                 Log.e("TAG0", "removeDisappearDevice: size"+ deviceList.size()+"postion"+i);
//                 deviceList.remove(i);
//                 notifyItemRemoved(i);
//             }
//         }
// //        deviceList.remove(position);
// //        notifyItemRemoved(position);
//     }
public void removeDisappearDevice() {
    long current_timeStamp = System.currentTimeMillis();
    for (int i = deviceList.size() - 1; i >= 0; i--) {
        if (current_timeStamp - deviceList.get(i).getTimestamp() > removeBleTime) {
            deviceList.remove(i);
            notifyItemRemoved(i + 1);
        }
    }
}
    public void removeDevice(int position) {
        deviceList.remove(position);
        notifyItemRemoved(position+1);
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameTextView;
        TextView macAddressTextView;
        TextView rssiTextView;
        TextView serviceUuidTextView;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameTextView = itemView.findViewById(R.id.device_name_text_view);
            macAddressTextView = itemView.findViewById(R.id.mac_address_text_view);
            rssiTextView = itemView.findViewById(R.id.rssi_text_view);
            serviceUuidTextView = itemView.findViewById(R.id.service_uuid_text_view);
        }
    }
}

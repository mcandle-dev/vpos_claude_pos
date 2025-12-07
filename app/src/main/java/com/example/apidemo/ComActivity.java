package com.example.apidemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import vpos.apipackage.Com;

public class ComActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int MSG_WHAT_OPEN = 0;
    private static final int MSG_WHAT_SEND = 1;
    private static final int MSG_WHAT_RECV = 2;
    private static final int MSG_WHAT_CLOSE = 3;
    private static final int MSG_WHAT_HINT = 4;

    private EditText etSend;
    private TextView tvMsg;
    private Button btnOpen;
    private Button btnSend;
    private Button btnRecv;
    private Button btnClose;

    private boolean isComOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_com);

        initView();
        initData();
        initEvent();
    }

    private void initView() {
        etSend = findViewById(R.id.etSend);
        tvMsg = findViewById(R.id.tvMsg);
        btnOpen = findViewById(R.id.btnOpen);
        btnSend = findViewById(R.id.btnSend);
        btnRecv = findViewById(R.id.btnRecv);
        btnClose = findViewById(R.id.btnClose);
    }

    private void initData() {
        etSend.setText("Hello, I'm client!");
    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        btnOpen.setOnClickListener(this);
        btnSend.setOnClickListener(this);
        btnRecv.setOnClickListener(this);
        btnClose.setOnClickListener(this);
        btnSend.setEnabled(false);
        btnRecv.setEnabled(false);
        btnClose.setEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void SendPromptMsg(String strInfo) {
        Message msg = new Message();
        msg.what = MSG_WHAT_HINT;
        Bundle b = new Bundle();
        b.putString("MSG", strInfo);
        msg.setData(b);
        promptHandler.sendMessage(msg);
    }

    public void SendPromptMsg(int what, boolean enable) {
        Message msg = new Message();
        msg.what = what;
        Bundle b = new Bundle();
        b.putString("MSG", enable ? "1" : "0");
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
                case MSG_WHAT_OPEN:
                    btnOpen.setEnabled(false);
                    btnSend.setEnabled(true);
                    btnRecv.setEnabled(true);
                    btnClose.setEnabled(true);
                    isComOpen = true;
                    break;
                case MSG_WHAT_SEND:
                    if (Integer.parseInt(strInfo) == 0) {
                        btnRecv.setEnabled(false);
                        btnClose.setEnabled(false);
                    } else {
                        btnRecv.setEnabled(true);
                        btnClose.setEnabled(true);
                    }
                    break;
                case MSG_WHAT_RECV:
                    if (Integer.parseInt(strInfo) == 0) {
                        btnSend.setEnabled(false);
                        btnClose.setEnabled(false);
                    } else {
                        btnSend.setEnabled(true);
                        btnClose.setEnabled(true);
                    }
                    break;
                case MSG_WHAT_CLOSE:
                    btnOpen.setEnabled(true);
                    btnSend.setEnabled(false);
                    btnRecv.setEnabled(false);
                    btnClose.setEnabled(false);
                    isComOpen = false;
                    break;
                case MSG_WHAT_HINT:
                    tvMsg.setText(strInfo);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void finish() {
        if (isComOpen)
            new Thread(Com::Lib_ComClose).start();
        super.finish();
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();

        new Thread() {
            public void run() {
                if (R.id.btnOpen == viewId) {
                    int ret = Com.Lib_ComOpen();
                    if (ret == 0)
                        SendPromptMsg(MSG_WHAT_OPEN, true);
                    else
                        SendPromptMsg("Com open failed, return: " + ret);
                }
                else if (R.id.btnClose == viewId) {
                    int ret = Com.Lib_ComClose();
                    if (ret == 0)
                        SendPromptMsg(MSG_WHAT_CLOSE, false);
                    else
                        SendPromptMsg("Com close failed, return: " + ret);
                }
                else if (R.id.btnSend == viewId) {
                    SendPromptMsg(MSG_WHAT_SEND, false);

                    String send = etSend.getText().toString().trim();
                    if (TextUtils.isEmpty(send)) {
                        SendPromptMsg("Empty send data!");
                    }
                    send += "\n";

                    int ret = Com.Lib_ComSend(send.getBytes(), send.getBytes().length);
                    if (ret == 0)
                        SendPromptMsg("Com send succeeded.");
                    else
                        SendPromptMsg("Com send failed, return: " + ret);

                    SendPromptMsg(MSG_WHAT_SEND, true);
                }
                else if (R.id.btnRecv == viewId) {
                    SendPromptMsg(MSG_WHAT_RECV, false);

                    byte[] recv = new byte[100];
                    int[] recvLen = new int[1];
                    int ret = Com.Lib_ComRecv(recv, recvLen, 100, recv.length);
                    if (ret == 0)
                        SendPromptMsg("len: " + recvLen[0] + ", recv: " + new String(recv));
                    else
                        SendPromptMsg("Com recv failed, return: " + ret);

                    SendPromptMsg(MSG_WHAT_RECV, true);
                }
            }
        }.start();
    }
}
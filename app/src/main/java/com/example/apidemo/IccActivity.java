package com.example.apidemo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

import vpos.apipackage.APDU_RESP;
import vpos.apipackage.APDU_SEND;
import vpos.apipackage.Icc;
import vpos.util.ByteUtil;

public class IccActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x02;

    private Button btn_read;
    private TextView tv_msg;

    private boolean mStartFlag = false;
    //check card timer
    private Timer mCheckCardTimer;
    private TimerTask mCheckCardTask;
    private static int CHECK_CONNECT_STATUS_TIMEOUT = 10000;//10s
    private boolean timeout = false;
    private byte slot = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icc);

        initView();
        initData();
        initEvent();
    }

    private void initView() {
        btn_read = findViewById(R.id.btn_icc_read);
        tv_msg = findViewById(R.id.tv_msg);
    }

    private void initData() {

    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        btn_read.setOnClickListener(this);
        findViewById(R.id.btn_icc_psam1).setOnClickListener(this);
        findViewById(R.id.btn_icc_psam2).setOnClickListener(this);
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

    public void SendPromptMsg(String strInfo) {
        Message msg = new Message();
        msg.what = RECORD_PROMPT_MSG;
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
                        tv_msg.append(strInfo);

                        int offset = tv_msg.getLineCount() * tv_msg.getLineHeight();
                        if (offset > tv_msg.getHeight())
                            tv_msg.scrollTo(0, offset - tv_msg.getHeight());
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onDestroy() {
        mStartFlag = false;
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();

        if (R.id.btn_icc_psam1 == viewId) {
            if (mStartFlag)
                return;

            slot = 1;
            btn_read.callOnClick();
        }
        else if (R.id.btn_icc_psam2 == viewId) {
            if (mStartFlag)
                return;

            slot = 2;
            btn_read.callOnClick();
        }

        if (R.id.btn_icc_read == viewId) {
            new Thread() {
                public void run() {
                    int iRet = -1;
                    byte vccMode = 2;//1;
                    byte[] atr = new byte[40];

                    if (mStartFlag) {
                        Log.i("unique Start", "start---------->flag=" + mStartFlag);
                        return;
                    }
                    mStartFlag = true;
                    SendPromptMsg("");

                    if (slot == 0) {
                        SendPromptMsg("Please insert the card...\n");
                        mCheckCardTimer = new Timer();
                        mCheckCardTask = new TimerTask() {
                            @Override
                            public void run() {
                                timeout = true;
                            }
                        };
                        mCheckCardTimer.schedule(mCheckCardTask, CHECK_CONNECT_STATUS_TIMEOUT);
                        while (!timeout && mStartFlag) {
                            iRet = Icc.Lib_IccCheck(slot);
                            if (iRet == 0) {
                                mCheckCardTimer.cancel();
                                break;
                            }

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (timeout || !mStartFlag) {
                            timeout = false;
                            SendPromptMsg("ICC「check」failed, return: " + iRet + "\n");
                            mStartFlag = false;
                            return;
                        }

                        SendPromptMsg("ICC「check」succeeded!\n");
                    } else {
                        SendPromptMsg("Select PSAM " + slot + "\n");
                    }

                    iRet = Icc.Lib_IccOpen(slot, vccMode, atr);
                    if (iRet == 0) {
                        SendPromptMsg("ICC「open」succeeded!\n");
                        SendPromptMsg("Card「atr」: " + ByteUtil.bytearrayToHexString(atr, atr[0]) + "\n");
                    } else {
                        SendPromptMsg("ICC「open」failed, return: " + iRet + "\n");
                        Icc.Lib_IccClose(slot);
                        mStartFlag = false;
                        slot = 0;
                        return;
                    }

                    byte cmd[] = new byte[4];
                    cmd[0] = 0x00;
                    cmd[1] = (byte) 0xa4;
                    cmd[2] = 0x04;
                    cmd[3] = 0x00;
                    short lc = 0x0e;
                    short le = 256;
                    byte[] data = "1PAY.SYS.DDF01".getBytes();

                    APDU_SEND ApduSend = new APDU_SEND(cmd, lc, data, le);
                    byte[] resp = new byte[516];
                    iRet = Icc.Lib_IccCommand(slot, ApduSend.getBytes(), resp);
                    if (iRet == 0) {
                        APDU_RESP ApduResp = new APDU_RESP(resp);

                        SendPromptMsg("ICC「command」succeeded!\n");
                        SendPromptMsg("LenOut : " + ApduResp.LenOut + "\n");
                        SendPromptMsg("DataOut: " + ByteUtil.bytearrayToHexString(
                                ApduResp.DataOut, ApduResp.LenOut) + "\n");
                        SendPromptMsg("SWA, SWB: 0x" + ByteUtil.byteToHexString(ApduResp.SWA)
                                + ByteUtil.byteToHexString(ApduResp.SWB) + "\n");
                    } else {
                        SendPromptMsg("ICC「command」failed, return: " + iRet + "\n");
                        Icc.Lib_IccClose(slot);
                        mStartFlag = false;
                        slot = 0;
                        return;
                    }

                    SendPromptMsg("ICC test succeeded!!!\n");
                    Icc.Lib_IccClose(slot);
                    mStartFlag = false;
                    slot = 0;
                }
            }.start();
        }
    }
}
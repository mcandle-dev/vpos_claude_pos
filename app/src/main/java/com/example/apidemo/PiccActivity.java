package com.example.apidemo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

import vpos.apipackage.APDU_RESP;
import vpos.apipackage.APDU_SEND;
import vpos.apipackage.Picc;
import vpos.util.ByteUtil;

public class PiccActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x01;

    private TextView tv_msg;

    private boolean mStartFlag = false;
    //check card timer
    private Timer mCheckCardTimer;
    private TimerTask mCheckCardTask;
    private static int CHECK_CONNECT_STATUS_TIMEOUT = 10000;//10s
    private boolean timeout = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picc);

        initView();
        initData();
        initEvent();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initView() {
        tv_msg = findViewById(R.id.tv_msg);
    }

    private void initData() {

    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.btn_picc_read).setOnClickListener(this);
        tv_msg.setMovementMethod(ScrollingMovementMethod.getInstance());
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
                        if (offset > tv_msg.getHeight()) {
                            tv_msg.scrollTo(0, offset - tv_msg.getHeight());
                        }
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

        if (R.id.btn_picc_read == viewId) {
            new Thread() {
                public void run() {
                    int iRet;
                    byte[] cardType = new byte[3];
                    byte[] serialNo = new byte[50];
                    byte[] ATS = new byte[50];

                    if (mStartFlag) {
                        Log.i("unique Start", "start---------->flag=" + mStartFlag);
                        return;
                    }
                    mStartFlag = true;
                    SendPromptMsg("");

                    iRet = Picc.Lib_PiccOpen();
                    if (iRet != 0) {
                        SendPromptMsg("PICC「open」failed, return: " + iRet + "\n");
                        mStartFlag = false;
                        return;
                    }
                    SendPromptMsg("PICC「open」succeeded!\n");

                    SendPromptMsg("Please place the card...\n");
                    mCheckCardTimer = new Timer();
                    mCheckCardTask = new TimerTask() {
                        @Override
                        public void run() {
                            timeout = true;
                        }
                    };
                    mCheckCardTimer.schedule(mCheckCardTask, CHECK_CONNECT_STATUS_TIMEOUT);
                    while (!timeout && mStartFlag) {
                        iRet = Picc.Lib_PiccCheck(cardType, serialNo, ATS);
                        if (iRet == 0) {
                            mCheckCardTimer.cancel();
                            break;
                        }
                    }

                    if (timeout || !mStartFlag) {
                        timeout = false;
                        SendPromptMsg("PICC「check」failed, return: " + iRet + "\n");
                        Picc.Lib_PiccClose();
                        mStartFlag = false;
                        return;
                    }

                    SendPromptMsg("PICC「check」succeeded!\n");
                    SendPromptMsg("Card「type」: " + ByteUtil.bytesToString(cardType) + "\n");
                    SendPromptMsg("Card「SN」  : " + ByteUtil.bytearrayToHexString(serialNo, serialNo[0] + 1) + "\n");
                    SendPromptMsg("Card「ATS」 : " + ByteUtil.bytearrayToHexString(ATS, ATS[0] + 1) + "\n");

                    if (true) {
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
                        iRet = Picc.Lib_PiccCommand(ApduSend.getBytes(), resp);
                        if (iRet == 0) {
                            APDU_RESP ApduResp = new APDU_RESP(resp);

                            SendPromptMsg("PICC「command」succeeded!\n");
                            SendPromptMsg("LenOut : " + ApduResp.LenOut + "\n");
                            SendPromptMsg("DataOut: " + ByteUtil.bytearrayToHexString(
                                    ApduResp.DataOut, ApduResp.LenOut) + "\n");
                            SendPromptMsg("SWA, SWB: 0x" + ByteUtil.byteToHexString(ApduResp.SWA)
                                    + ByteUtil.byteToHexString(ApduResp.SWB) + "\n");
                        } else {
                            SendPromptMsg("PICC「command」failed, return: " + iRet + "\n");
                            Picc.Lib_PiccClose();
                            mStartFlag = false;
                            return;
                        }

                        SendPromptMsg("PICC test succeeded!!!\n");
                        Picc.Lib_PiccClose();
                    } else {
                        Picc.Lib_PiccClose();
                    }
                    mStartFlag = false;
                }
            }.start();
        }
    }
}
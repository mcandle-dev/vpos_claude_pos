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

import vpos.apipackage.Msr;

public class MsrActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x03;

    private TextView tv_msg;

    private boolean mStartFlag = false;
    //check card timer
    private Timer mCheckCardTimer;
    private TimerTask mCheckCardTask;
    private static int CHECK_CONNECT_STATUS_TIMEOUT = 10000;//10s
    private boolean timeout = false;

    private int totalCount = 0;
    private int successCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_msr);

        initView();
        initData();
        initEvent();
    }

    private void initView() {
        tv_msg = findViewById(R.id.tv_msg);
    }

    private void initData() {

    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.btn_msr_read).setOnClickListener(this);
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

        if (R.id.btn_msr_read == viewId) {
            new Thread() {
                public void run() {
                    int iRet;
                    byte[] track1 = new byte[256];
                    byte[] track2 = new byte[256];
                    byte[] track3 = new byte[256];

                    if (mStartFlag) {
                        Log.i("unique Start", "start---------->flag=" + mStartFlag);
                        return;
                    }
                    mStartFlag = true;
                    SendPromptMsg("");

                    iRet = Msr.Lib_MsrOpen();
                    if (iRet != 0) {
                        SendPromptMsg("MSR「open」failed, return: " + iRet + "\n");
                        mStartFlag = false;
                        return;
                    }
                    SendPromptMsg("MSR「open」succeeded!\n");

                    SendPromptMsg("Please swipe the card...\n");
                    mCheckCardTimer = new Timer();
                    mCheckCardTask = new TimerTask() {
                        @Override
                        public void run() {
                            timeout = true;
                        }
                    };
                    mCheckCardTimer.schedule(mCheckCardTask, CHECK_CONNECT_STATUS_TIMEOUT);
                    while (!timeout && mStartFlag) {
                        iRet = Msr.Lib_MsrCheck();
                        if (iRet == 0) {
                            mCheckCardTimer.cancel();
                            break;
                        }
                    }

                    if (timeout || !mStartFlag) {
                        timeout = false;
                        Msr.Lib_MsrClose();
                        SendPromptMsg("MSR「swipe」failed, return: " + iRet + "\n");
                        mStartFlag = false;
                        return;
                    }
                    totalCount++;
                    //SendPromptMsg("swipe count: " + totalCount + "\n");
                    SendPromptMsg("MSR「swipe」succeeded!\n");

                    iRet = Msr.Lib_MsrRead(track1, track2, track3);
                    if (iRet > 0) {
                        successCount++;
                        //SendSCountMsg("success count: " + successCount + "\n");

                        String string = "";
                        if (iRet <= 7) {
                            if ((iRet & 0x01) == 0x01)
                                string += "\ntrack 1: " + new String(track1).trim() + "\n";
                            if ((iRet & 0x02) == 0x02)
                                string += "\ntrack 2: " + new String(track2).trim() + "\n";
                            if ((iRet & 0x04) == 0x04)
                                string += "\ntrack 3: " + new String(track3).trim() + "\n";
                            string += "\nMSR test succeeded!!!\n";
                        } else {
                            string = "\nMSR [read] exception, return: " + iRet + "\n";
                        }
                        SendPromptMsg(string);
                        Msr.Lib_MsrClose();
                    } else {
                        //SendPromptMsg("success count: " + successCount + "\n");
                        SendPromptMsg("\nMSR「read」failed, return: " + iRet + "\n");
                        Msr.Lib_MsrClose();
                    }

                    SendPromptMsg("swipe   count: " + totalCount + "\n");
                    SendPromptMsg("success count: " + successCount + "\n");
                    mStartFlag = false;
                }
            }.start();
        }
    }
}
package com.example.apidemo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

import com.example.apidemo.barcode.BarcodeScanActivity;
import com.king.camera.scan.CameraScan;

public class ScanActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x07;

    private TextView tv_msg;
    private ActivityResultLauncher<Intent> multiCodeLauncher;

    private boolean mStartFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        initView();
        initData();
        initEvent();
    }

    private void initView() {
        tv_msg = findViewById(R.id.tv_msg);
    }

    private void initData() {
        multiCodeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        SendPromptMsg("Result: \n" + CameraScan.parseScanResult(result.getData()) + "\n");
                    } else {
                        SendPromptMsg("Cancel scan!\n");
                    }
                }
        );
    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.btn_scan_multicode).setOnClickListener(this);
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
    public void onClick(View view) {
        int viewId = view.getId();

        if (R.id.btn_scan_multicode == viewId) {
            SendPromptMsg("");
            ActivityOptionsCompat options = ActivityOptionsCompat
                    .makeCustomAnimation(this, R.anim.alpha_in, R.anim.alpha_out);
            multiCodeLauncher.launch(new Intent(this, BarcodeScanActivity.class), options);
        }
    }
}
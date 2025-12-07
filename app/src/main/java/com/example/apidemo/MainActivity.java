package com.example.apidemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initData();
        initEvent();
    }

    private void initView() {

    }

    private void initData() {

    }

    private void initEvent() {
        findViewById(R.id.btn_func_picc).setOnClickListener(this);
        findViewById(R.id.btn_func_icc).setOnClickListener(this);
        findViewById(R.id.btn_func_msr).setOnClickListener(this);
        findViewById(R.id.btn_func_sys).setOnClickListener(this);
        findViewById(R.id.btn_func_print).setOnClickListener(this);
        findViewById(R.id.btn_func_beacon).setOnClickListener(this);
        findViewById(R.id.btn_func_scan).setOnClickListener(this);
        findViewById(R.id.btn_func_com).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        Intent intent = new Intent();

        if (R.id.btn_func_picc == viewId) {
            intent.setClass(MainActivity.this, PiccActivity.class);
        } else if (R.id.btn_func_icc == viewId) {
            intent.setClass(MainActivity.this, IccActivity.class);
        } else if (R.id.btn_func_msr == viewId) {
            intent.setClass(MainActivity.this, MsrActivity.class);
        } else if (R.id.btn_func_sys == viewId) {
            intent.setClass(MainActivity.this, SysActivity.class);
        } else if (R.id.btn_func_print == viewId) {
            intent.setClass(MainActivity.this, PrintActivity.class);
        } else if (R.id.btn_func_beacon == viewId) {
            intent.setClass(MainActivity.this, BeaconActivity.class);
        } else if (R.id.btn_func_scan == viewId) {
            intent.setClass(MainActivity.this, ScanActivity.class);
        } else if (R.id.btn_func_com == viewId) {
            intent.setClass(MainActivity.this, ComActivity.class);
        }

        startActivity(intent);
    }
}
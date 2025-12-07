package com.example.apidemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import ru.bartwell.exfilepicker.ExFilePicker;
import ru.bartwell.exfilepicker.data.ExFilePickerResult;
import vpos.apipackage.Sys;
import vpos.util.APKVersionInfoUtil;

public class SysActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x04;
    private static final int EX_FILE_PICKER_RESULT = 0xfa01;

    private TextView tv_msg;
    private ExFilePicker exFilePicker;

    private String startDirectory = null;
    private boolean mStartFlag = false;
    private int mLoadAppSel = 0;
    private int mLocaleSel = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sys);

        initView();
        initData();
        initEvent();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (mStartFlag)
                Toast.makeText(this, "Busy...", Toast.LENGTH_SHORT).show();
            else
                finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mStartFlag) {
            Toast.makeText(this, "Busy...", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private void initView() {
        tv_msg = findViewById(R.id.tv_msg);

        exFilePicker = new ExFilePicker();
        exFilePicker.setCanChooseOnlyOneItem(true);
        exFilePicker.setShowOnlyExtensions("bin");
        // exFilePicker.setExceptExtensions("jpg");
        exFilePicker.setChoiceType(ExFilePicker.ChoiceType.FILES);
        exFilePicker.setNewFolderButtonDisabled(true);
        exFilePicker.setSortButtonDisabled(true);
        exFilePicker.setQuitButtonEnabled(true);
        // exFilePicker.setSortingType(ExFilePicker.SortingType.NAME_ASC);
        exFilePicker.setUseFirstItemAsUpEnabled(true);
    }

    private void initData() {

    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.btn_get_version).setOnClickListener(this);
        findViewById(R.id.btn_load_app).setOnClickListener(this);
        findViewById(R.id.btn_set_locale).setOnClickListener(this);
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
                        if (strInfo.contains("Progress:")
                                && tv_msg.getText().toString().contains("Progress:")) {
                            int index = tv_msg.getText().toString().lastIndexOf("Progress:");
                            tv_msg.setText(tv_msg.getText().toString().substring(0, index) + strInfo);
                        } else {
                            tv_msg.append(strInfo);
                        }

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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EX_FILE_PICKER_RESULT) {
            if (resultCode == Activity.RESULT_OK) {
                ExFilePickerResult result = ExFilePickerResult.getFromIntent(data);
                if (result != null && result.getCount() > 0) {
                    String path = result.getPath();

                    List<String> names = result.getNames();
                    for (int i = 0; i < names.size(); i++) {
                        File f = new File(path, names.get(i));
                        try {
                            Uri uri = Uri.fromFile(f);
                            startDirectory = path;

                            new Thread(() -> {
                                if (mStartFlag) {
                                    Log.i("unique Start", "start---------->flag=" + mStartFlag);
                                    return;
                                }
                                mStartFlag = true;

                                SendPromptMsg("");
                                SendPromptMsg("Selected: " + uri.getPath() + "\n");
                                SendPromptMsg("Loading app...\n");

                                byte[] binBuffer = readFile(uri.getPath());
                                if (binBuffer != null) {
                                    Sys.Lib_LoadApp(binBuffer, binBuffer.length, new Sys.ILoadListener() {
                                        @Override
                                        public void onFail(int errCode) {
                                            SendPromptMsg("Load app failed, return: "+ errCode + "\n");
                                        }

                                        @Override
                                        public void onProgress(int percent) {
                                            SendPromptMsg("Progress: " + percent + "%\n");
                                        }

                                        @Override
                                        public void onSuccess() {
                                            SendPromptMsg("Load app succeeded!\n");
                                        }
                                    });
                                } else {
                                    SendPromptMsg("Read bin file failed!\n");
                                }

                                mStartFlag = false;
                            }).start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                SendPromptMsg("");
                SendPromptMsg("No file selected!\n");
            }
        }
    }

    private byte[] readFile(String filePath) {
        if (filePath == null) {
            try {
                String[] files = getApplicationContext().getAssets().list("app");
                String binFile = null;
                if (files != null) {
                    for (String file : files) {
                        if (file.endsWith(".bin")) {
                            binFile = file;
                            break;
                        }
                    }
                }

                if (binFile != null) {
                    InputStream fis = getApplicationContext().getAssets().open("app/" + binFile);
                    int availableLen = fis.available();
                    byte[] binBuffer = new byte[availableLen];
                    SendPromptMsg(binFile + " - size: " + availableLen + "B\n");

                    if (availableLen != fis.read(binBuffer)) {
                        fis.close();
                        return null;
                    } else {
                        fis.close();
                        return binBuffer;
                    }
                } else
                    return null;
            } catch (Exception e) {
                SendPromptMsg("Exception: " + e + "\n");
                return null;
            }
        } else {
            try {
                FileInputStream fis = new FileInputStream(filePath);
                int availableLen = fis.available();
                byte[] binBuffer = new byte[availableLen];
                SendPromptMsg("File size: " + availableLen + "B\n");

                if (availableLen != fis.read(binBuffer)) {
                    fis.close();
                    return null;
                } else {
                    fis.close();
                    return binBuffer;
                }
            } catch (Exception e) {
                SendPromptMsg("Exception: " + e + "\n");
                return null;
            }
        }
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();

        if (R.id.btn_get_version == viewId) {
            new Thread() {
                public void run() {
                    if (mStartFlag) {
                        Log.i("unique Start", "start---------->flag=" + mStartFlag);
                        return;
                    }
                    mStartFlag = true;
                    SendPromptMsg("");

                    String[] version = new String[2];
                    int ret = Sys.Lib_GetContactCardInfo(version);
                    if (ret == 0) {
                        SendPromptMsg("Application version: " + APKVersionInfoUtil.getVersionName(SysActivity.this) + "\n"
                                + "Contact card boot version: " + version[0] + "\n"
                                + "Contact card app  version: " + version[1] + "\n");
                    } else {
                        SendPromptMsg("Application version: " + APKVersionInfoUtil.getVersionName(SysActivity.this) + "\n"
                                + "Contact card boot version: unknown\n"
                                + "Contact card app  version: unknown\n");
                    }

                    mStartFlag = false;
                }
            }.start();
        }
        else if (R.id.btn_load_app == viewId) {
            if (mStartFlag) {
                Log.i("unique Start", "start---------->flag=" + mStartFlag);
                return;
            }

            final String[] items = {"Asset", "Local"};
            new AlertDialog.Builder(this)
                    .setTitle("Load App From")
                    .setSingleChoiceItems(items, mLoadAppSel, (dialog, i) -> mLoadAppSel = i)
                    .setPositiveButton("OK", (dialog, i) -> {
                        if (mLoadAppSel == 0) {
                            new Thread() {
                                public void run() {
                                    if (mStartFlag)
                                        return;
                                    mStartFlag = true;
                                    SendPromptMsg("");

                                    SendPromptMsg("Selected: assets/app/xxx.bin\n");
                                    SendPromptMsg("Loading app...\n");

                                    byte[] binBuffer = readFile(null);
                                    if (binBuffer != null) {
                                        Sys.Lib_LoadApp(binBuffer, binBuffer.length, new Sys.ILoadListener() {
                                            @Override
                                            public void onFail(int errCode) {
                                                SendPromptMsg("Load app failed, return: "+ errCode + "\n");
                                            }

                                            @Override
                                            public void onProgress(int percent) {
                                                SendPromptMsg("Progress: " + percent + "%\n");
                                            }

                                            @Override
                                            public void onSuccess() {
                                                SendPromptMsg("Load app succeeded!\n");
                                            }
                                        });
                                    } else {
                                        SendPromptMsg("Read bin file failed!\n");
                                    }

                                    mStartFlag = false;
                                }
                            }.start();
                        } else if (mLoadAppSel == 1) {
                            if (TextUtils.isEmpty(startDirectory)) {
                                exFilePicker.setStartDirectory("/storage/emulated/0/Download");
                            } else {
                                exFilePicker.setStartDirectory(startDirectory);
                            }
                            exFilePicker.start(SysActivity.this, EX_FILE_PICKER_RESULT);
                        }
                        dialog.dismiss();
                    })
                    .show();
        }
        else if (R.id.btn_set_locale == viewId) {
            if (mStartFlag) {
                Log.i("unique Start", "start---------->flag=" + mStartFlag);
                return;
            }

            final String[] items = {"한국어", "中文", "English"};
            new AlertDialog.Builder(this)
                    .setTitle("System Language Setting")
                    .setSingleChoiceItems(items, mLocaleSel, (dialog, i) -> mLocaleSel = i)
                    .setPositiveButton("OK", (dialog, i) -> {
                        if (1 == mLocaleSel)
                            setSystemLocale(Locale.CHINA);
                        else if (2 == mLocaleSel)
                            setSystemLocale(Locale.ENGLISH);
                        else
                            setSystemLocale(Locale.KOREA);
                        dialog.dismiss();
                    })
                    .show();
        }
    }

    public void setSystemLocale(Locale locale) {
        try {
            Class<?> iActivityManager = Class.forName("android.app.IActivityManager");
            Class<?> activityManagerNative = Class.forName("android.app.ActivityManagerNative");
            Method getDefault = activityManagerNative.getDeclaredMethod("getDefault");
            Object objIActMag = getDefault.invoke(activityManagerNative);
            Method getConfiguration = iActivityManager.getDeclaredMethod("getConfiguration");
            Configuration config = (Configuration) getConfiguration.invoke(objIActMag);
            config.setLocale(locale);

            Class<?> clzConfig = Class.forName("android.content.res.Configuration");
            Field userSetLocale = clzConfig.getField("userSetLocale");
            userSetLocale.set(config, true);
            Class[] clzParams = {Configuration.class};

            Method updateConfiguration = iActivityManager.getDeclaredMethod("updateConfiguration", clzParams);
            updateConfiguration.invoke(objIActMag, config);
            BackupManager.dataChanged("com.android.providers.settings");
        } catch (Exception e) {
            Log.e("ERROR", "Exception: " + e);
        }
    }
}
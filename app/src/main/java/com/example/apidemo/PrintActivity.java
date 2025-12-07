package com.example.apidemo;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import vpos.apipackage.Print;

public class PrintActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int RECORD_PROMPT_MSG = 0x05;

    private TextView tv_msg;
    private EditText et_page;

    private boolean mStartFlag = false;
    private boolean mPrnInit = false;
    private byte nLevel = 1;
    private int nCut = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print);

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
        et_page = findViewById(R.id.et_page);
        tv_msg = findViewById(R.id.tv_msg);
    }

    private void initData() {

    }

    private void initEvent() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ((RadioGroup) findViewById(R.id.rg_gray)).setOnCheckedChangeListener((group, id) -> {
            if (id == R.id.rb_gray1)
                nLevel = 1;
            else if (id == R.id.rb_gray2)
                nLevel = 2;
            else if (id == R.id.rb_gray3)
                nLevel = 3;
            else if (id == R.id.rb_gray4)
                nLevel = 4;
        });
        ((RadioGroup) findViewById(R.id.rg_cutter)).setOnCheckedChangeListener((group, id) -> {
            if (id == R.id.rb_cut0)
                nCut = 0;
            else if (id == R.id.rb_cut1)
                nCut = 1;
        });
        findViewById(R.id.btn_print_test).setOnClickListener(this);
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
    public void onClick(View view) {
        int viewId = view.getId();

        if (R.id.btn_print_test == viewId) {
            new Thread() {
                public void run() {
                    int iRet;
                    long startTime, endTime;
                    String page;

                    if (mStartFlag) {
                        Log.i("unique Start", "start---------->flag=" + mStartFlag);
                        return;
                    }
                    mStartFlag = true;
                    SendPromptMsg("");

                    page = et_page.getText().toString().trim();
                    if (TextUtils.isEmpty(page) || Integer.parseInt(page) == 0) {
                        SendPromptMsg("Print「page」is empty!\n");
                        mStartFlag = false;
                        return;
                    }
                    if (Integer.parseInt(page) > 15) {
                        SendPromptMsg("Max print「page」is 15!\n");
                        mStartFlag = false;
                        return;
                    }

                    if (!mPrnInit) {
                        iRet = Print.Lib_PrnInit();
                        if (iRet != 0) {
                            SendPromptMsg("Print「Init」failed, return: " + iRet + "\n");
                            mStartFlag = false;
                            return;
                        }
                        mPrnInit = true;
                    }

                    iRet = Print.Lib_PrnCheckStatus();
                    if (iRet == -4002) {
                        SendPromptMsg("Print「CheckStatus」error: No Paper!\n");
                        mStartFlag = false;
                        return;
                    } else if (iRet == -4005) {
                        SendPromptMsg("Print「CheckStatus」error: Printer Too Hot!\n");
                        mStartFlag = false;
                        return;
                    } else if (iRet == -4012) {
                        SendPromptMsg("Print「CheckStatus」error: Printer Too Cold!\n");
                        mStartFlag = false;
                        return;
                    }

                    // Print.Lib_PrnSetGray(nLevel);

                    for (int i = 1; i <= Integer.parseInt(page); i++) {
                        iRet = Print.Lib_PrnSetFont(getAssets(), "fonts/BBFontUnicode.bin", (byte) 16, (byte) 16, (byte) 0x33);
                        if (iRet == 0) {
                            // SendPromptMsg("Print「Font」: BBFontUnicode, 16 * 16 * 0x33\n");

                            iRet = Print.Lib_PrnStr("123467890\n");
                            Print.Lib_PrnStr("abcdefghigklmnopqrstuvwxyz\n");
                            Print.Lib_PrnStr("ABCDEFGHIJKLMNOPQRSTUVWXYZ\n");
                            Print.Lib_PrnStr("안녕하세요\n");
                            Print.Lib_PrnStr("ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ\n");
                            Print.Lib_PrnStr("ㅏㅑㅓㅕㅗㅛㅜㅠㅡㅣ\n");
                            Print.Lib_PrnStr("ㄲㄸㅃㅉㅆ\n");
                            Print.Lib_PrnStr("ㅢㅚㅐㅟㅔㅒㅖㅘㅝㅙㅞ\n");
                            Print.Lib_PrnStr("국민의 모든 자유와 권리는 국가안전보장·질서유지 또는 공공복리를 위하여 필요한 경우에 한하여 법률로써 제한할 수 있으며! 법률이 헌법에 위반되는 여부가 재판의 전제가 된 경우에는 법원은 헌법재판소에 제청하여 그 심판에 의하여 재판한다! 국가는 근로의 의무의 내용과 조건을 민주주의원칙에 따라 법률로 정한다! 국가는 재해를 예방하고 그 위험으로부터 국민을 보호하기 위하여 노력하여야 한다! \n");
                            // SendPromptMsg("Print render string ... " + iRet + "\n");
                        }

                        iRet = Print.Lib_PrnSetFont(getAssets(), "fonts/BBFontUnicode.bin", (byte) 24, (byte) 24, (byte) 0x33);
                        if (iRet == 0) {
                            // SendPromptMsg("Print「Font」: BBFontUnicode, 24 * 24 * 0x33\n");

                            iRet = Print.Lib_PrnStr("123467890\n");
                            Print.Lib_PrnStr("abcdefghigklmnopqrstuvwxyz\n");
                            Print.Lib_PrnStr("ABCDEFGHIJKLMNOPQRSTUVWXYZ\n");
                            Print.Lib_PrnStr("안녕하세요\n");
                            Print.Lib_PrnStr("ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ\n");
                            Print.Lib_PrnStr("ㅏㅑㅓㅕㅗㅛㅜㅠㅡㅣ\n");
                            Print.Lib_PrnStr("ㄲㄸㅃㅉㅆ\n");
                            Print.Lib_PrnStr("ㅢㅚㅐㅟㅔㅒㅖㅘㅝㅙㅞ\n");
                            Print.Lib_PrnStr("국민의 모든 자유와 권리는 국가안전보장·질서유지 또는 공공복리를 위하여 필요한 경우에 한하여 법률로써 제한할 수 있으며! 법률이 헌법에 위반되는 여부가 재판의 전제가 된 경우에는 법원은 헌법재판소에 제청하여 그 심판에 의하여 재판한다! 국가는 근로의 의무의 내용과 조건을 민주주의원칙에 따라 법률로 정한다! 국가는 재해를 예방하고 그 위험으로부터 국민을 보호하기 위하여 노력하여야 한다! \n");
                            // SendPromptMsg("Print render string ... " + iRet + "\n");
                        }

                        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.metrolinx1bitdepth);
                        iRet = new Print().Lib_PrnBmp(bmp);
                        // SendPromptMsg("Print「Bitmap」\n");
                        // SendPromptMsg("Print render bitmap ... " + iRet + "\n");

                        Bitmap qrcode = BitmapFactory.decodeResource(getResources(), R.drawable.qrcode);
                        iRet = new Print().Lib_PrnBmp(qrcode);
                        // SendPromptMsg("Print「QRCode」\n");
                        // SendPromptMsg("Print render qrcode ... " + iRet + "\n");

                         Bitmap block = BitmapFactory.decodeResource(getResources(), R.drawable.block1bitdepth);
                         iRet = new Print().Lib_PrnBmp(block);
                        // SendPromptMsg("Print「Block」\n");
                        // SendPromptMsg("Print render block ... " + iRet + "\n");

                        Print.Lib_PrnStr("***** Page " + i + " *****\n");
                    }

                    Print.Lib_PrnStr("\n\n\n\n\n");

                    SendPromptMsg("Print「Start」:)\n");
                    startTime = System.currentTimeMillis();
                    iRet = Print.Lib_PrnStart();
                    endTime = System.currentTimeMillis();
                    if (iRet == -4002) {
                        SendPromptMsg("Print「Start」error: Printer No Paper!\n");
                        mStartFlag = false;
                        return;
                    } else if (iRet == -4005) {
                        SendPromptMsg("Print「Start」error: Printer Too Hot!\n");
                        mStartFlag = false;
                        return;
                    } else if (iRet == -4012) {
                        SendPromptMsg("Print「Start」error: Printer Too Cold!\n");
                        mStartFlag = false;
                        return;
                    } else {
                        SendPromptMsg("Gray: " + nLevel
                                + ", Cut: " + ((nCut == 0) ? "half" : "full")
                                + ", Time: " + (endTime - startTime) + "ms");
                    }

                    Print.Lib_PrnCutPaper(nCut);

                    mStartFlag = false;
                }
            }.start();
        }
    }
}
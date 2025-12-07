package com.example.apidemo.barcode;

import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.example.apidemo.R;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.king.camera.scan.AnalyzeResult;
import com.king.camera.scan.CameraScan;
import com.king.camera.scan.util.PointUtils;
import com.king.mlkit.vision.barcode.BarcodeCameraScanActivity;

import java.util.ArrayList;
import java.util.List;

public class BarcodeScanActivity extends BarcodeCameraScanActivity {

    private ImageView ivResult;

    @Override
    public void initUI() {
        super.initUI();

        ivResult = findViewById(R.id.ivResult);
    }

    @Override
    public void initCameraScan(@NonNull CameraScan<List<Barcode>> cameraScan) {
        super.initCameraScan(cameraScan);
        cameraScan.setPlayBeep(true)
                .setVibrate(true)
                .bindFlashlightView(ivFlashlight);
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_qrcode_scan;
    }

    @Override
    public void onBackPressed() {
        if (viewfinderView.isShowPoints()) { // 如果是结果点显示时，用户点击了返回键，则认为是取消选择当前结果，重新开始扫码
            ivResult.setImageResource(0);
            viewfinderView.showScanner();
            getCameraScan().setAnalyzeImage(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.alpha_in, R.anim.alpha_out);
    }

    @Override
    public void onScanResultCallback(@NonNull AnalyzeResult<List<Barcode>> result) {
        getCameraScan().setAnalyzeImage(false);
        List<Barcode> results = result.getResult();

        // 取预览当前帧图片并显示，为结果点提供参照
        ivResult.setImageBitmap(previewView.getBitmap());
        List<Point> points = new ArrayList<>();
        int width = result.getBitmapWidth();
        int height = result.getBitmapHeight();
        for (Barcode barcode : results) {
            Rect box = barcode.getBoundingBox();
            if (box != null) {
                // 将实际的结果中心点坐标转换成界面预览的坐标
                Point point = PointUtils.transform(
                        box.centerX(),
                        box.centerY(),
                        width,
                        height,
                        viewfinderView.getWidth(),
                        viewfinderView.getHeight()
                );
                points.add(point);
            }
        }
        // 设置Item点击监听
        viewfinderView.setOnItemClickListener(position -> {
            // 显示点击Item将所在位置扫码识别的结果返回
            Intent intent = new Intent();
            intent.putExtra(CameraScan.SCAN_RESULT, results.get(position).getDisplayValue());
            setResult(RESULT_OK, intent);
            finish();

            /*
             * 显示结果后，如果需要继续扫码，则可以继续分析图像
             */
//                ivResult.setImageResource(0);
//                viewfinderView.showScanner();
//                getCameraScan().setAnalyzeImage(true);
        });
        // 显示结果点信息
        viewfinderView.showResultPoints(points);

        if (results.size() == 1) { // 只有一个结果直接返回
            Intent intent = new Intent();
            intent.putExtra(CameraScan.SCAN_RESULT, results.get(0).getDisplayValue());
            setResult(RESULT_OK, intent);
            finish();
        }
    }
}
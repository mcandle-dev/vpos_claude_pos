package com.example.apidemo.receiver;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = BootReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        Log.e(TAG, "action: " + action);

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            wakeUpAndUnlock(context);

            Intent toIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
            toIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(toIntent);
        }
    }

    public static void wakeUpAndUnlock(Context context) {
        // 屏幕唤醒
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK,
                context.getPackageName() + ":bright"); //最后的参数是Logcat里用的Tag
        wl.acquire(60 * 1000L /*1 minutes*/);

        // 屏幕解锁
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock kl = km.newKeyguardLock("unlock"); //参数是Logcat里用的Tag
        kl.disableKeyguard();
    }
}

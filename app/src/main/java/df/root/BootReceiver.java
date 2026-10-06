package df.root;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

public class BootReceiver extends BroadcastReceiver implements IReporter {
    private static final String TAG = "dfroot";

    private Context mCtx;

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        if (mCtx != null) {
            com.nzs.mroot.util.AppLogger.append(mCtx, msg);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context deCtx = context.createDeviceProtectedStorageContext();
        this.mCtx = deCtx;
        if (new File("/dev/df").exists()) {
            Log.i(TAG, "boot: already hooked, skipping");
            com.nzs.mroot.util.AppLogger.appendLine(deCtx, "BootReceiver: already hooked, skipping");
            return;
        }
        Log.i(TAG, "boot: " + intent.getAction());
        com.nzs.mroot.util.AppLogger.appendLine(deCtx, "BootReceiver: " + intent.getAction());
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dfroot:boot");
        wl.acquire();
        new Thread(() -> {
            try {
                int rc = ExploitRunner.run(deCtx, this);
                Log.i(TAG, "boot: exploit rc=" + rc);
                com.nzs.mroot.util.AppLogger.appendLine(deCtx, "BootReceiver: exploit rc=" + rc);
            } catch (Exception e) {
                Log.e(TAG, "boot: exploit exception", e);
                com.nzs.mroot.util.AppLogger.appendLine(deCtx, "BootReceiver: exploit exception: " + e);
            } finally {
                wl.release();
            }
        }, "dfroot-boot").start();
    }
}

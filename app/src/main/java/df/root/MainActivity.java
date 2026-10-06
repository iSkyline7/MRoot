package df.root;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.nzs.mroot.databinding.ActivityMainBinding;
import com.nzs.mroot.R;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements IReporter {

    private static final String TAG = "dfroot";

    private ActivityMainBinding binding;
    private Context mDeCtx;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        mMain.post(() -> {
            binding.outputView.append(msg);
            binding.outputScroll.post(() -> binding.outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDeCtx = createDeviceProtectedStorageContext();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        if (!isSuManagerInstalled()) {
            new AlertDialog.Builder(this)
                .setTitle("SU Manager Required")
                .setMessage(
                    "SU Manager is not installed.\n\n" +
                    "Samsung devices: install from github.com/diabl0w/KernelSU\n\n" +
                    "Other devices: github.com/tiann/KernelSU, github.com/KernelSU-Next/KernelSU-Next, or github.com/KOWX712/KernelSU")
                .setCancelable(false)
                .setPositiveButton("Exit", (d, w) -> finish())
                .show();
            return;
        }

        if (new File("/dev/df").exists()) binding.btnRun.setEnabled(false);

        binding.btnRun.setOnClickListener(v -> {
            binding.btnRun.setEnabled(false);
            binding.outputView.setText("");
            mExec.execute(this::runExploit);
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isSuManagerInstalled() {
        return ExploitRunner.resolveManager(mDeCtx, this) != null;
    }

    private void runExploit() {
        try {
            int rc = ExploitRunner.run(mDeCtx, this);
            String msg = rc == 0 ? "DFRoot: SUCCESS"
                       : rc == 1 ? "DFRoot FAILED: ksud exited with error"
                       : rc == 2 ? "DFRoot FAILED: check logs"
                       : "DFRoot FAILED: failed to patch files";
            mMain.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            report("\nexception: " + e + "\n");
        } finally {
            mMain.post(() -> binding.btnRun.setEnabled(!new File("/dev/df").exists()));
        }
    }
}

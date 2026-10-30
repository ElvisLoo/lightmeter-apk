package com.jikegang.lightmeter;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.ChipGroup;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sm;
    private Sensor light;
    private TextView luxView, fcView, evView, evIsoView, recView, statusView;
    private int iso = 400;
    private double lastLux = 0;
    // 仅用于「恢复出来的」照度：先让界面显示上次的值，等真实回调来了再更新，
    // 避免转屏瞬间闪一屏 EV 0。
    private double pendingLux = -1;

    private final double[] F_ARR = {2.8, 4, 5.6, 8, 11, 16};
    private final double[] SHUTTERS = {
            1.0 / 1000, 1.0 / 500, 1.0 / 250, 1.0 / 125, 1.0 / 60, 1.0 / 30,
            1.0 / 15, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1, 2, 4, 8
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        // 转屏 / 进程被系统回收后恢复状态。三样都要：
        // 1) iso —— 不恢复会静默回到 XML 写死的 400，推荐快门整体偏 4 档，
        //    负片直接欠曝；
        // 2) lastLux —— 不恢复会先算出 EV 0 并闪一屏错误读数，
        //    等下一次传感器回调才纠正，用户容易在这半秒里读到错的快门；
        // 3) 提示语 —— 避免把「已恢复」误显示成「读取中」。
        if (b != null) {
            iso = b.getInt("iso", iso);
            lastLux = b.getDouble("lux", 0);
            if (lastLux > 0) pendingLux = lastLux;
        }

        luxView = findViewById(R.id.lux);
        fcView = findViewById(R.id.fc);
        evView = findViewById(R.id.ev);
        evIsoView = findViewById(R.id.evIso);
        recView = findViewById(R.id.rec);
        statusView = findViewById(R.id.status);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) light = sm.getDefaultSensor(Sensor.TYPE_LIGHT);

        ChipGroup cg = findViewById(R.id.isoGroup);
        int restoreId = isoToChipId(iso);
        if (restoreId != View.NO_ID) cg.check(restoreId);
        cg.setOnCheckedChangeListener((group, checkedId) -> {
            int[] map = {
                    R.id.iso100, 100, R.id.iso200, 200, R.id.iso400, 400,
                    R.id.iso800, 800, R.id.iso1600, 1600, R.id.iso3200, 3200
            };
            for (int i = 0; i < map.length; i += 2) {
                if (map[i] == checkedId) {
                    iso = map[i + 1];
                    break;
                }
            }
            updateReadout();
        });

        findViewById(R.id.btnCalc).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CalcActivity.class)));

        // 恢复态下先把上次读数画出来，不等传感器回调。
        if (pendingLux > 0) updateReadout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (light != null) {
            sm.registerListener(this, light, SensorManager.SENSOR_DELAY_UI);
            // 有恢复值时先说明是旧读数，避免用户拿转屏前的数值当实时结果。
            statusView.setText(lastLux > 0
                    ? "已恢复上次读数，等待传感器更新…"
                    : "对准光源，读取实时照度");
        } else {
            statusView.setText("⚠ 此设备无环境光传感器，无法测光");
            Toast.makeText(this, "未检测到环境光传感器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (light != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_LIGHT) {
            boolean firstAfterRestore = pendingLux > 0;
            lastLux = e.values[0];
            updateReadout();
            if (firstAfterRestore && pendingLux > 0) pendingLux = -1;
            if (firstAfterRestore && statusView != null) {
                statusView.setText("对准光源，读取实时照度");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {
    }

    private void updateReadout() {
        double lux = lastLux;
        double fc = lux / 10.764;
        double ev100 = (lux > 0) ? Math.log(lux / 2.5) / Math.log(2) : 0;
        double evIso = ev100 + Math.log(iso / 100.0) / Math.log(2);

        luxView.setText(String.format(Locale.US, "%.0f", lux));
        fcView.setText(String.format(Locale.US, "%.1f fc", fc));
        evView.setText(String.format(Locale.US, "EV %.1f", ev100));
        evIsoView.setText(String.format(Locale.US, "@ISO %d  EV %.1f", iso, evIso));

        // 入射光测光公式：N^2 / t = E * S / 250  →  t = N^2 * 250 / (E * S)
        double M = lux * iso / 250.0;
        StringBuilder sb = new StringBuilder();
        for (double f : F_ARR) {
            double t = (M > 0) ? f * f / M : 0;
            sb.append(String.format(Locale.US, "f/%.1f   →   %s\n", f, fmtShutter(t)));
        }
        recView.setText(sb.toString().trim());
    }

    String fmtShutter(double t) {
        if (t <= 0 || Double.isInfinite(t) || Double.isNaN(t)) return "—";
        double best = SHUTTERS[0], bestErr = Double.MAX_VALUE;
        for (double s : SHUTTERS) {
            double err = Math.abs(Math.log(t) - Math.log(s));
            if (err < bestErr) {
                bestErr = err;
                best = s;
            }
        }
        if (best < 1) return "1/" + (int) Math.round(1.0 / best);
        return String.format(Locale.US, "%.0f\"", best);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("iso", iso);
        // lastLux 不存的话，转屏后 readout 会按 0 lux 算，
        // 屏幕上先出现一轮 f/16 → 1/8000 这种离谱推荐。
        out.putDouble("lux", lastLux);
    }

    /** ISO 数值 → 对应 Chip 的 id；不在列表内返回 NO_ID */
    private int isoToChipId(int v) {
        switch (v) {
            case 100: return R.id.iso100;
            case 200: return R.id.iso200;
            case 400: return R.id.iso400;
            case 800: return R.id.iso800;
            case 1600: return R.id.iso1600;
            case 3200: return R.id.iso3200;
            default: return View.NO_ID;
        }
    }
}

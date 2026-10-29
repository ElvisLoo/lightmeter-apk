package com.jikegang.lightmeter;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class CalcActivity extends AppCompatActivity {

    private final double[] SHUTTERS = {
            1.0 / 1000, 1.0 / 500, 1.0 / 250, 1.0 / 125, 1.0 / 60, 1.0 / 30,
            1.0 / 15, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1, 2, 4, 8
    };
    private final double[] F_VALS = {2.8, 4, 5.6, 8, 11, 16, 22};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_calc);

        Spinner spinF = findViewById(R.id.spinF);
        Spinner spinF2 = findViewById(R.id.spinF2);
        ArrayAdapter<CharSequence> ad = ArrayAdapter.createFromResource(
                this, R.array.apertures, android.R.layout.simple_spinner_item);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinF.setAdapter(ad);
        spinF2.setAdapter(ad);

        EditText evEdit = findViewById(R.id.evEdit);
        TextView resShutter = findViewById(R.id.resShutter);
        findViewById(R.id.btnShutter).setOnClickListener(v -> {
            try {
                double ev = Double.parseDouble(evEdit.getText().toString().trim());
                double f = F_VALS[spinF.getSelectedItemPosition()];
                double t = f * f / Math.pow(2, ev);
                resShutter.setText("快门 ≈ " + fmtShutter(t));
            } catch (Exception ex) {
                resShutter.setText("请输入有效 EV");
            }
        });

        EditText shutterEdit = findViewById(R.id.shutterEdit);
        TextView resEv = findViewById(R.id.resEv);
        findViewById(R.id.btnEv).setOnClickListener(v -> {
            try {
                double t = parseShutter(shutterEdit.getText().toString());
                double f = F_VALS[spinF2.getSelectedItemPosition()];
                double ev = Math.log(f * f / t) / Math.log(2);
                resEv.setText(String.format(Locale.US, "EV@ISO100 ≈ %.1f", ev));
            } catch (Exception ex) {
                resEv.setText("请输入有效光圈/快门");
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 必须在 View 都绑定完、监听器挂好之后恢复，否则 setText 会触发计算副作用
        restoreState(b);
    }

    private double parseShutter(String s) {
        s = s.trim();
        if (s.contains("/")) {
            String[] p = s.split("/");
            return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
        }
        return Double.parseDouble(s);
    }

    private String fmtShutter(double t) {
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
        // 转屏或切到别的 App 再回来，Activity 会被重建。
        // 不保存的话输入框清空、结果变回 "--"，用户得从头再算一遍。
        out.putCharSequence("ev", ((EditText) findViewById(R.id.evEdit)).getText());
        out.putCharSequence("sh", ((EditText) findViewById(R.id.shutterEdit)).getText());
        out.putInt("f1", ((Spinner) findViewById(R.id.spinF)).getSelectedItemPosition());
        out.putInt("f2", ((Spinner) findViewById(R.id.spinF2)).getSelectedItemPosition());
        out.putCharSequence("resShutter", ((TextView) findViewById(R.id.resShutter)).getText());
        out.putCharSequence("resEv", ((TextView) findViewById(R.id.resEv)).getText());
    }

    /** 在 onCreate 末尾调用，恢复上次的输入与结果 */
    private void restoreState(Bundle b) {
        if (b == null) return;
        ((EditText) findViewById(R.id.evEdit)).setText(b.getCharSequence("ev", ""));
        ((EditText) findViewById(R.id.shutterEdit)).setText(b.getCharSequence("sh", ""));
        ((Spinner) findViewById(R.id.spinF)).setSelection(b.getInt("f1", 0));
        ((Spinner) findViewById(R.id.spinF2)).setSelection(b.getInt("f2", 0));
        ((TextView) findViewById(R.id.resShutter)).setText(b.getCharSequence("resShutter", "--"));
        ((TextView) findViewById(R.id.resEv)).setText(b.getCharSequence("resEv", "--"));
    }
}

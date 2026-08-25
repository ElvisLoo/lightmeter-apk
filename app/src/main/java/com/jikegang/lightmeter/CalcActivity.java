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
}

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
            1.0 / 15, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1, 2, 4, 8,
            // 与主界面保持同一组刻度；止于 8 秒会让长曝光被吸附到错误档位
            15, 30
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

        // ---- 长曝光补偿 ----
        Spinner spinFilm = findViewById(R.id.spinFilm);
        EditText longSec = findViewById(R.id.longSecEdit);
        TextView resRec = findViewById(R.id.resReciprocity);
        TextView noteRec = findViewById(R.id.noteReciprocity);

        String[] filmNames = new String[Reciprocity.PROFILES.length];
        for (int i = 0; i < filmNames.length; i++) filmNames[i] = Reciprocity.PROFILES[i].name;
        ArrayAdapter<String> filmAd = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filmNames);
        filmAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinFilm.setAdapter(filmAd);
        noteRec.setText(Reciprocity.PROFILES[0].note);

        spinFilm.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View v, int pos, long id) {
                if (pos >= 0 && pos < Reciprocity.PROFILES.length) {
                    noteRec.setText(Reciprocity.PROFILES[pos].note);
                }
                recomputeReciprocity(longSec, spinFilm, resRec);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        findViewById(R.id.btnReciprocity).setOnClickListener(v ->
                recomputeReciprocity(longSec, spinFilm, resRec));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 必须在 View 都绑定完、监听器挂好之后恢复，否则 setText 会触发计算副作用
        restoreState(b);
    }

    /**
     * 计算并展示补偿结果。
     * 单独抽出来，因为「换胶片」和「点按钮」两条路径都要刷新，
     * 否则用户切了胶片但结果还是上一个胶片的，会给出错误的曝光。
     */
    private void recomputeReciprocity(EditText secInput, Spinner filmSpin, TextView out) {
        if (secInput == null || filmSpin == null || out == null) return;
        String raw = secInput.getText() == null ? "" : secInput.getText().toString().trim();
        if (raw.isEmpty()) {
            out.setText("--");
            return;
        }
        double sec;
        try {
            sec = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            out.setText("请输入数字，例如 30");
            return;
        }
        if (!(sec > 0) || Double.isInfinite(sec)) {
            out.setText("请输入大于 0 的秒数");
            return;
        }
        // 超过 24 小时（86400 秒）的“理论曝光时间”几乎必然是误输入
        // （比如把毫秒当秒填）。直接拦下，比算出一个荒谬结果更友好。
        if (sec > 86400) {
            out.setText("时间过长，请确认单位是秒（上限 24 小时）");
            return;
        }

        int pos = filmSpin.getSelectedItemPosition();
        if (pos < 0 || pos >= Reciprocity.PROFILES.length) pos = 0;
        Reciprocity.Profile film = Reciprocity.PROFILES[pos];

        double corrected = Reciprocity.compensate(sec, film);
        if (!Reciprocity.needsCompensation(sec, film)) {
            out.setText("无需补偿：" + Reciprocity.formatDuration(sec));
            return;
        }
        double stops = Reciprocity.stopsOf(sec, film);
        out.setText(String.format(Locale.US, "%s（+%.1f 档）",
                Reciprocity.formatDuration(corrected), stops));
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
        // 倒易律区：不存的话转屏后输入框清空、胶片选择回到第一项，
        // 用户刚算到一半的长曝参数就没了
        EditText ls = findViewById(R.id.longSecEdit);
        if (ls != null) out.putCharSequence("longSec", ls.getText());
        Spinner sf = findViewById(R.id.spinFilm);
        if (sf != null) out.putInt("film", sf.getSelectedItemPosition());
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

        EditText ls = findViewById(R.id.longSecEdit);
        if (ls != null) ls.setText(b.getCharSequence("longSec", ""));
        Spinner sf = findViewById(R.id.spinFilm);
        if (sf != null) {
            int fp = b.getInt("film", 0);
            if (fp >= 0 && fp < Reciprocity.PROFILES.length) {
                sf.setSelection(fp);
                TextView note = findViewById(R.id.noteReciprocity);
                if (note != null) note.setText(Reciprocity.PROFILES[fp].note);
            }
        }
        // 恢复完再算一次，让结果显示回来
        if (ls != null && sf != null) {
            recomputeReciprocity(ls, sf, findViewById(R.id.resReciprocity));
        }
    }
}

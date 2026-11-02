package com.jikegang.lightmeter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 拍摄记录页。
 *
 * 数据存在应用私有目录的 shots.json 里：
 * 不需要存储权限，卸载即清空 —— 测光记录没什么敏感信息，
 * 但"不需要任何权限"这件事本身能少掉一个用户疑虑。
 */
public class LogActivity extends AppCompatActivity {

    private static final String FILE_NAME = "shots.json";

    private ShotLog log;
    private ListView list;
    private TextView emptyHint;
    private TextView curReadout;
    private EditText noteEdit;
    private ArrayAdapter<String> adapter;
    private final List<String> rows = new ArrayList<>();

    /** 从 Intent 带来的当前读数（主界面点"记一笔"时传入） */
    private double curLux;
    private double curEv;
    private int curIso;
    private double curApe;
    private String curShutter = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_log);

        log = new ShotLog();
        log.fromJson(readFile());

        list = findViewById(R.id.logList);
        emptyHint = findViewById(R.id.emptyHint);
        curReadout = findViewById(R.id.curReadout);
        noteEdit = findViewById(R.id.noteEdit);

        Intent extra = getIntent();
        curLux = extra.getDoubleExtra("lux", 0);
        curEv = extra.getDoubleExtra("ev", 0);
        curIso = extra.getIntExtra("iso", 0);
        curApe = extra.getDoubleExtra("ape", 0);
        curShutter = extra.getStringExtra("shutter") == null ? "" : extra.getStringExtra("shutter");

        showCurrentReadout();

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(getResources().getColor(R.color.text));
                tv.setTextSize(13.5f);
                tv.setPadding(8, 18, 8, 18);
                return tv;
            }
        };
        list.setAdapter(adapter);

        // 长按删除单条
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            if (pos < 0 || pos >= log.size()) return true;
            ShotLog.Entry e = log.all().get(pos);
            new AlertDialog.Builder(this)
                    .setTitle("删除这条记录？")
                    .setMessage(ShotLog.format(e))
                    .setPositiveButton("删除", (d, w) -> {
                        log.removeAt(pos);
                        save();
                        refresh();
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> onSave());
        findViewById(R.id.btnExport).setOnClickListener(v -> onExport());
        findViewById(R.id.btnClear).setOnClickListener(v -> onClear());
        findViewById(R.id.btnBackFromLog).setOnClickListener(v -> finish());

        refresh();
    }

    private void showCurrentReadout() {
        StringBuilder sb = new StringBuilder();
        sb.append("EV ").append(String.format(java.util.Locale.US, "%.1f", curEv));
        if (curLux > 0) {
            sb.append("  ·  ").append(String.format(java.util.Locale.US, "%.0f", curLux)).append(" lux");
        }
        if (curApe > 0) {
            sb.append("  ·  f/").append(ShotLog.trimNumber(curApe)).append(" ").append(curShutter);
        }
        if (curIso > 0) {
            sb.append("  ·  ISO").append(curIso);
        }
        curReadout.setText(sb.toString());
    }

    private void onSave() {
        String note = noteEdit.getText() == null ? "" : noteEdit.getText().toString().trim();
        ShotLog.Entry e = new ShotLog.Entry(System.currentTimeMillis(),
                curLux, curEv, curIso, curApe, curShutter, note);
        log.add(e);
        save();
        noteEdit.setText("");
        // 只清备注，不清读数 —— 同一场景常常要连记好几张
        refresh();
        Toast.makeText(this, "已记录", Toast.LENGTH_SHORT).show();
    }

    private void onExport() {
        if (log.isEmpty()) {
            Toast.makeText(this, "还没有记录", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = log.toText();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("测光记录", text));
            Toast.makeText(this, "已复制 " + log.size() + " 条到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void onClear() {
        if (log.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("清空全部记录？")
                .setMessage("共 " + log.size() + " 条，清空后无法恢复。")
                .setPositiveButton("清空", (d, w) -> {
                    log.clear();
                    save();
                    refresh();
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refresh() {
        rows.clear();
        for (ShotLog.Entry e : log.all()) {
            String line = ShotLog.format(e);
            if (!e.note.isEmpty()) line = line + "  #" + e.note;
            rows.add(line);
        }
        adapter.notifyDataSetChanged();
        boolean empty = rows.isEmpty();
        emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ---------- 存储 ----------

    /**
     * 读存档。
     *
     * 两个坑都踩过：
     *   1. InputStream.read(byte[]) 不保证一次读完，大文件会只读到前半截，
     *      解析失败后按「没有记录」处理 —— 用户以为记录全丢了，而且没有任何提示。
     *   2. 超过上限直接截断更危险：截断的 JSON 一定解析失败，同样是静默丢数据。
     *      这里改成明确放弃（并保留原文件不动），下次写入会覆盖成有效内容。
     */
    private static final int MAX_FILE_BYTES = 512 * 1024;

    private String readFile() {
        File f = new File(getFilesDir(), FILE_NAME);
        if (!f.exists()) return null;
        if (f.length() > MAX_FILE_BYTES) {
            // 异常大的存档说明有问题（正常 200 条约几十 KB）。
            // 不去读它，也不删它 —— 让用户下次保存时自然覆盖。
            return null;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            int total = 0;
            // 循环读到 EOF，不要假设一次 read 就结束
            while ((n = in.read(chunk)) > 0) {
                total += n;
                if (total > MAX_FILE_BYTES) return null;
                out.write(chunk, 0, n);
            }
            if (total == 0) return null;
            return out.toString("UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 写盘失败不打断用户：
     * 内存里有数据，界面照常显示，下次操作会再试一次。
     * 只为"存不上记录"就弹错误框，比悄悄失败更烦人。
     */
    private void save() {
        File f = new File(getFilesDir(), FILE_NAME);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(log.toJson().getBytes("UTF-8"));
        } catch (IOException ignored) {
            // 静默：下一次操作会重试
        }
    }
}

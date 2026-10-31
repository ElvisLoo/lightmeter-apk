package com.jikegang.lightmeter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拍摄记录：把每次测光结果存下来，事后能对回照片。
 *
 * 为什么需要：
 *   胶片拍完一卷通常要等一周才冲洗。等到看照片时，
 *   "这张是在室内窗边还是傍晚拍的"基本全靠猜。
 *   现场按一下记下来，事后才有一条对照线。
 *
 * 为什么不用数据库：
 *   记录量很小（一卷 36 张，一年也就几千条），
 *   JSON 存文件足够，还不引入 Room / SQLite 的构建复杂度与体积。
 *
 * 这个类只做纯逻辑（解析、追加、裁剪、格式化），
 * 不碰 Android API —— 文件读写由调用方负责，方便直接单测。
 */
final class ShotLog {

    /** 单条记录 */
    static final class Entry {
        final long at;           // 毫秒时间戳
        final double lux;
        final double ev;
        final int iso;
        final double aperture;
        final String shutter;    // 已经是人类可读形式（"1/125" / "2s"）
        final String note;

        Entry(long at, double lux, double ev, int iso, double aperture, String shutter, String note) {
            this.at = at;
            this.lux = lux;
            this.ev = ev;
            this.iso = iso;
            this.aperture = aperture;
            this.shutter = shutter == null ? "" : shutter;
            this.note = note == null ? "" : note;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("at", at);
            o.put("lux", lux);
            o.put("ev", ev);
            o.put("iso", iso);
            o.put("ape", aperture);
            o.put("shutter", shutter);
            o.put("note", note);
            return o;
        }

        static Entry fromJson(JSONObject o) {
            if (o == null) return null;
            long at = o.optLong("at", 0);
            // 没有时间戳的记录没有对照价值，直接丢弃
            if (at <= 0) return null;
            return new Entry(
                    at,
                    o.optDouble("lux", 0),
                    o.optDouble("ev", 0),
                    o.optInt("iso", 0),
                    o.optDouble("ape", 0),
                    o.optString("shutter", ""),
                    o.optString("note", "")
            );
        }
    }

    /** 最多保留多少条。一卷 36 张，留 200 条够覆盖好几卷。 */
    static final int MAX_ENTRIES = 200;

    private final List<Entry> entries = new ArrayList<>();

    List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    int size() {
        return entries.size();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 追加一条。超出上限时丢掉最老的。
     * 新的放在最前面：用户看记录总是关心刚拍的。
     */
    void add(Entry e) {
        if (e == null) return;
        entries.add(0, e);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    void clear() {
        entries.clear();
    }

    /** 删掉第 index 条（对应 UI 列表位置） */
    boolean removeAt(int index) {
        if (index < 0 || index >= entries.size()) return false;
        entries.remove(index);
        return true;
    }

    /** 序列化成 JSON 字符串，供落盘 */
    String toJson() {
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
            try {
                arr.put(e.toJson());
            } catch (JSONException ignored) {
                // 单条失败不该拖垮整个存档
            }
        }
        return arr.toString();
    }

    /**
     * 从 JSON 恢复。
     * 任何异常都当作"没有存档"处理 —— 记录丢了也不该让 App 打不开。
     */
    void fromJson(String json) {
        entries.clear();
        if (json == null || json.trim().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            List<Entry> parsed = new ArrayList<>();
            for (int i = 0; i < arr.length() && parsed.size() < MAX_ENTRIES; i++) {
                Entry e = Entry.fromJson(arr.optJSONObject(i));
                if (e != null) parsed.add(e);
            }
            entries.addAll(parsed);
        } catch (JSONException ignored) {
            // 存档损坏：保持空列表
        }
    }

    /**
     * 一条记录的可读文案。
     * 具体格式：月-日 时:分 · EV12.3 · f/8 1/125 · ISO400
     */
    static String format(Entry e) {
        if (e == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(formatTime(e.at));
        sb.append(" · EV").append(String.format(java.util.Locale.US, "%.1f", e.ev));
        if (e.aperture > 0) {
            sb.append(" · f/").append(trimNumber(e.aperture));
        }
        if (!e.shutter.isEmpty()) {
            sb.append(" ").append(e.shutter);
        }
        if (e.iso > 0) {
            sb.append(" · ISO").append(e.iso);
        }
        return sb.toString();
    }

    /** 去掉多余的 .0：f/8 而不是 f/8.0 */
    static String trimNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /** 时间戳 → "10-31 21:45" */
    static String formatTime(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.US, "%02d-%02d %02d:%02d",
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
    }

    /**
     * 导出成纯文本，方便粘贴到备忘录里。
     * 每行一条，带完整时间（含年份），因为导出的东西会被长期保存。
     */
    String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("测光记录（").append(entries.size()).append(" 条）\n\n");
        for (Entry e : entries) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(e.at);
            sb.append(String.format(java.util.Locale.US, "%04d-%02d-%02d %02d:%02d",
                    c.get(java.util.Calendar.YEAR),
                    c.get(java.util.Calendar.MONTH) + 1,
                    c.get(java.util.Calendar.DAY_OF_MONTH),
                    c.get(java.util.Calendar.HOUR_OF_DAY),
                    c.get(java.util.Calendar.MINUTE)));
            sb.append("  EV").append(String.format(java.util.Locale.US, "%.1f", e.ev));
            sb.append("  ").append(String.format(java.util.Locale.US, "%.0f", e.lux)).append("lux");
            if (e.aperture > 0) sb.append("  f/").append(trimNumber(e.aperture));
            if (!e.shutter.isEmpty()) sb.append("  ").append(e.shutter);
            if (e.iso > 0) sb.append("  ISO").append(e.iso);
            if (!e.note.isEmpty()) sb.append("  #").append(e.note);
            sb.append("\n");
        }
        return sb.toString();
    }
}

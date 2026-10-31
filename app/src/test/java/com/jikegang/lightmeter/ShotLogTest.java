package com.jikegang.lightmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 拍摄记录：本地单测（JVM 直接跑，不需要设备）。
 *
 * 重点验证的是"存档不能丢数据、也不能因为存档坏了就打不开 App"。
 * 这类功能出错的代价很高：用户是拿它对照一周后冲洗出来的照片的。
 */
public class ShotLogTest {

    private static ShotLog.Entry entry(long at, double ev) {
        return new ShotLog.Entry(at, 1000, ev, 400, 8, "1/125", "");
    }

    @Test
    public void 新增记录放在最前面() {
        ShotLog log = new ShotLog();
        log.add(entry(1000, 10));
        log.add(entry(2000, 11));
        assertEquals(2, log.size());
        assertEquals(2000, log.all().get(0).at, 0);
    }

    @Test
    public void 空记录不崩且状态正确() {
        ShotLog log = new ShotLog();
        assertTrue(log.isEmpty());
        assertEquals(0, log.size());
    }

    @Test
    public void 超过上限时丢弃最老的() {
        ShotLog log = new ShotLog();
        for (int i = 0; i < ShotLog.MAX_ENTRIES + 50; i++) {
            log.add(entry(1000 + i, 10));
        }
        assertEquals(ShotLog.MAX_ENTRIES, log.size());
        // 最新的仍在最前
        assertEquals(1000 + ShotLog.MAX_ENTRIES + 49, log.all().get(0).at, 0);
        // 最老的已经被挤掉
        for (ShotLog.Entry e : log.all()) {
            assertTrue(e.at > 1000 + 49);
        }
    }

    @Test
    public void 序列化后再读回内容一致() {
        ShotLog log = new ShotLog();
        log.add(new ShotLog.Entry(1700000000000L, 320, 12.3, 400, 8, "1/125", "窗边"));
        String json = log.toJson();

        ShotLog restored = new ShotLog();
        restored.fromJson(json);

        assertEquals(1, restored.size());
        ShotLog.Entry e = restored.all().get(0);
        assertEquals(1700000000000L, e.at);
        assertEquals(12.3, e.ev, 0.001);
        assertEquals(400, e.iso);
        assertEquals(8, e.aperture, 0.001);
        assertEquals("1/125", e.shutter);
        assertEquals("窗边", e.note);
    }

    @Test
    public void 损坏的存档当作空记录而不是崩溃() {
        ShotLog log = new ShotLog();
        log.add(entry(1000, 10));
        log.fromJson("{ 这不是合法 JSON");
        assertTrue("损坏时应清空而不是保留半截数据", log.isEmpty());
    }

    @Test
    public void 空字符串与null都能安全处理() {
        ShotLog log = new ShotLog();
        log.add(entry(1000, 10));
        log.fromJson(null);
        assertTrue(log.isEmpty());
        log.add(entry(2000, 11));
        log.fromJson("   ");
        assertTrue(log.isEmpty());
    }

    @Test
    public void 没有时间戳的记录被丢弃() {
        ShotLog log = new ShotLog();
        // 缺 at 字段：这种记录事后无法对照，留着只会让列表出现"1970年"
        log.fromJson("[{\"ev\":12},{\"at\":1700000000000,\"ev\":13}]");
        assertEquals(1, log.size());
    }

    @Test
    public void 删除指定记录() {
        ShotLog log = new ShotLog();
        log.add(entry(1000, 10));
        log.add(entry(2000, 11));
        assertTrue(log.removeAt(0));
        assertEquals(1, log.size());
        assertEquals(1000, log.all().get(0).at, 0);
    }

    @Test
    public void 删除越界索引返回false() {
        ShotLog log = new ShotLog();
        assertFalse(log.removeAt(0));
        assertFalse(log.removeAt(-1));
        log.add(entry(1000, 10));
        assertFalse(log.removeAt(5));
    }

    @Test
    public void 清空记录() {
        ShotLog log = new ShotLog();
        log.add(entry(1000, 10));
        log.clear();
        assertTrue(log.isEmpty());
    }

    @Test
    public void 格式化文案包含关键信息() {
        ShotLog.Entry e = new ShotLog.Entry(
                1700000000000L, 320, 12.3, 400, 8, "1/125", "test");
        String s = ShotLog.format(e);
        assertTrue(s.contains("EV12.3"));
        assertTrue(s.contains("f/8"));
        assertTrue(s.contains("1/125"));
        assertTrue(s.contains("ISO400"));
        // 整数光圈不该显示成 f/8.0
        assertFalse(s.contains("f/8.0"));
    }

    @Test
    public void 光圈为0时不显示f值() {
        ShotLog.Entry e = new ShotLog.Entry(1700000000000L, 100, 10, 400, 0, "", "");
        String s = ShotLog.format(e);
        assertFalse(s.contains("f/"));
    }

    @Test
    public void 数字去尾零() {
        assertEquals("8", ShotLog.trimNumber(8.0));
        assertEquals("5.6", ShotLog.trimNumber(5.6));
        assertEquals("2", ShotLog.trimNumber(2.0));
    }

    @Test
    public void 文本导出带完整年份() {
        ShotLog log = new ShotLog();
        log.add(entry(1700000000000L, 12.3));
        String text = log.toText();
        assertTrue("导出的文本会长期保存，必须带年份", text.contains("2023"));
        assertTrue(text.contains("EV12.3"));
    }

    @Test
    public void 损坏单条不影响其它条恢复() {
        ShotLog log = new ShotLog();
        log.fromJson("[{\"at\":1700000000000,\"ev\":10},null,{\"bad\":1},{\"at\":1700000060000,\"ev\":11}]");
        assertEquals(2, log.size());
    }
}

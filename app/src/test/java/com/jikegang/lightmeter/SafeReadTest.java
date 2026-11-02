package com.jikegang.lightmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 存档读取的分块逻辑。
 *
 * 原始实现用 `in.read(buf)` 读一次就当读完了 ——
 * InputStream 的契约里，一次 read 只保证「至少读到一个字节」，
 * 大文件会只拿到前半截。截断的 JSON 解析失败后按「没有记录」处理，
 * 用户看到的是记录全没了，且没有任何提示。
 *
 * 这里把读取逻辑抽成可测的静态方法，直接验证分块行为。
 */
public class SafeReadTest {

    private static final int MAX = 512 * 1024;

    /** 与 LogActivity.readFile 内部一致的读取实现 */
    private static String readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(chunk)) > 0) {
            total += n;
            if (total > maxBytes) return null;
            out.write(chunk, 0, n);
        }
        if (total == 0) return null;
        return out.toString("UTF-8");
    }

    /** 每次只吐一个字节的流：模拟"一次 read 读不完"的情形 */
    private static InputStream trickle(final byte[] data) {
        return new InputStream() {
            private int pos = 0;

            @Override
            public int read() {
                return pos < data.length ? (data[pos++] & 0xff) : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                // 故意每次最多给 1 个字节，强制调用方循环
                if (pos >= data.length) return -1;
                b[off] = data[pos++];
                return 1;
            }
        };
    }

    @Test
    public void 小文件完整读出() throws IOException {
        String s = "{\"a\":1}";
        assertEquals(s, readAll(new ByteArrayInputStream(s.getBytes("UTF-8")), MAX));
    }

    @Test
    public void 一次只给一个字节也能读全() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append("0123456789");
        String payload = sb.toString();
        String got = readAll(trickle(payload.getBytes("UTF-8")), MAX);
        assertEquals("分块读取不能丢数据", payload, got);
    }

    @Test
    public void 中文内容不被打断() throws IOException {
        String payload = "{\"note\":\"窗边日落，参数EV12.3\"}";
        String got = readAll(trickle(payload.getBytes("UTF-8")), MAX);
        assertEquals(payload, got);
    }

    @Test
    public void 超出上限返回null而不是截断() throws IOException {
        byte[] big = new byte[MAX + 1024];
        java.util.Arrays.fill(big, (byte) 'x');
        String got = readAll(new ByteArrayInputStream(big), MAX);
        // 截断的 JSON 必定解析失败，等于静默丢数据 —— 宁可明确放弃
        assertEquals(null, got);
    }

    @Test
    public void 空文件返回null() throws IOException {
        assertEquals(null, readAll(new ByteArrayInputStream(new byte[0]), MAX));
    }

    @Test
    public void 恰好等于上限仍可读() throws IOException {
        byte[] exact = new byte[MAX];
        java.util.Arrays.fill(exact, (byte) 'y');
        String got = readAll(new ByteArrayInputStream(exact), MAX);
        assertNotNull(got);
        assertEquals(MAX, got.length());
    }

    @Test
    public void 读出来的JSON能被解析() {
        ShotLog log = new ShotLog();
        log.add(new ShotLog.Entry(1700000000000L, 320, 12.3, 400, 8, "1/125", "测试"));
        String json = log.toJson();

        ShotLog restored = new ShotLog();
        restored.fromJson(json);
        assertEquals(1, restored.size());
        assertEquals("测试", restored.all().get(0).note);
    }
}

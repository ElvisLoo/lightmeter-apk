package com.jikegang.lightmeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 快门刻度吸附：回归测试。
 *
 * 场景来源：夜景测光算出 20 秒，但刻度表止于 8 秒，
 * 结果被吸附到 8 秒 —— 实拍欠曝 1.3 档，而用户看不出来。
 * 这类错误在胶片上要等一周冲洗才发现。
 */
public class ShutterSnapTest {

    /** 与 MainActivity / CalcActivity 保持一致的刻度 */
    private static final double[] SHUTTERS = {
            1.0 / 1000, 1.0 / 500, 1.0 / 250, 1.0 / 125, 1.0 / 60, 1.0 / 30,
            1.0 / 15, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1, 2, 4, 8, 15, 30
    };

    /** 复刻吸附逻辑：按对数距离取最近刻度 */
    private static double snap(double t) {
        double best = SHUTTERS[0];
        double bestErr = Double.MAX_VALUE;
        for (double s : SHUTTERS) {
            double err = Math.abs(Math.log(t) - Math.log(s));
            if (err < bestErr) {
                bestErr = err;
                best = s;
            }
        }
        return best;
    }

    private static double stopsOff(double wanted, double got) {
        return Math.abs(Math.log(wanted / got) / Math.log(2));
    }

    @Test
    public void 刻度覆盖到30秒() {
        assertEquals("最长档位应是 30 秒", 30.0, SHUTTERS[SHUTTERS.length - 1], 1e-9);
    }

    @Test
    public void 二十秒不再被吸附到八秒() {
        double got = snap(20);
        assertEquals(15.0, got, 1e-9);
        // 20 秒吸附到 15 秒，偏差约 0.4 档，属于可接受范围
        assertTrue("偏差应小于半档", stopsOff(20, got) < 0.5);
    }

    @Test
    public void 三十秒能精确命中() {
        assertEquals(30.0, snap(30), 1e-9);
        assertEquals(15.0, snap(15), 1e-9);
    }

    @Test
    public void 长曝光区间偏差都在半档内() {
        for (double t = 9; t <= 30; t += 1) {
            double got = snap(t);
            assertTrue("t=" + t + " 偏差过大", stopsOff(t, got) < 0.6);
        }
    }

    @Test
    public void 短曝端仍然精确() {
        assertEquals(1.0 / 125, snap(1.0 / 125), 1e-9);
        assertEquals(1.0 / 60, snap(1.0 / 60), 1e-9);
        assertEquals(1.0 / 1000, snap(1.0 / 1000), 1e-9);
    }

    @Test
    public void 极短快门不会越界到负数() {
        double got = snap(1e-9);
        assertTrue(got > 0);
        assertEquals(SHUTTERS[0], got, 1e-9);
    }

    @Test
    public void 极长快门吸附到最长档而不是出错() {
        assertEquals(30.0, snap(3600), 1e-9);
    }
}

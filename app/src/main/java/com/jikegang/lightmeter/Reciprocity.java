package com.jikegang.lightmeter;

/**
 * 倒易律失效（Reciprocity Failure）补偿计算。
 *
 * 背景：胶片的感光特性假设「光强 × 时间 = 常量」。
 * 但在弱光下（长曝光），这个关系会失效 —— 实际得到的密度低于预期，
 * 画面欠曝，同时反差升高、阴影压死。这叫倒易律失效。
 *
 * 经验规律：曝光时间超过某个阈值（各胶片不同，一般 1 秒左右）后，
 * 需要额外延长曝光时间来补偿。补偿量随时间的增加按指数增长，
 * 计算方式是 t_corrected = t^(1+p)，其中 p 是该胶片的补偿系数。
 *
 * 为什么把这部分单独成类：
 *   它是纯数学，与 Android 无关。放在这里可以脱离设备直接验证，
 *   也方便以后被别的界面复用（比如主测光页直接给建议快门）。
 */
final class Reciprocity {

    private Reciprocity() {}

    /**
     * 常见胶片的补偿参数（p 值）。
     * 数值来自厂商数据表与公开的实测经验值，属于「够用」而非「精确」——
     * 黑白负片的容差本来就大，配上包围曝光基本不会翻车。
     *
     * p = 0 表示该胶片在这个时长的范围内基本不需要补偿。
     */
    static final class Profile {
        final String name;
        final double thresholdSec;   // 超过这个秒数才开始补偿
        final double p;              // 补偿指数
        final String note;

        Profile(String name, double thresholdSec, double p, String note) {
            this.name = name;
            this.thresholdSec = thresholdSec;
            this.p = p;
            this.note = note;
        }
    }

    /** 内置胶片库。按常见程度排序，界面上第一条是默认。 */
    static final Profile[] PROFILES = new Profile[] {
            new Profile("柯达 Portra 400", 1.0, 0.10, "彩色负片，长曝轻微偏色，建议加 5~10 蓝"),
            new Profile("柯达 Tri-X 400", 1.0, 0.13, "黑白负片，宽容度大，超 10 秒可再补半档"),
            new Profile("伊尔福 HP5 Plus", 1.0, 0.15, "黑白负片，长曝表现好，阴影需提亮"),
            new Profile("富士 Acros 100", 120.0, 0.0, "几乎无倒易律失效，120 秒内无需补偿"),
            new Profile("柯达 Ektar 100", 1.0, 0.08, "彩色负片，长曝偏品红，需校色"),
            new Profile("反转片（通用）", 1.0, 0.20, "反转片对失效最敏感，且宽容度低"),
            new Profile("自定义", 1.0, 0.10, "按手头胶片的实测参数调整"),
    };

    /**
     * 计算补偿后的曝光时间。
     *
     * @param seconds 测光得到的理论曝光时间（秒）
     * @param profile 胶片参数
     * @return 修正后的秒数；输入非法时原样返回输入
     */
    static double compensate(double seconds, Profile profile) {
        if (profile == null) return seconds;
        if (!(seconds > 0) || Double.isInfinite(seconds)) return seconds;
        if (profile.p <= 0) return seconds;
        if (seconds <= profile.thresholdSec) return seconds;

        double corrected = Math.pow(seconds, 1.0 + profile.p);
        // 幂运算对超大输入会溢出成 Infinity。返回一个明确的极大有限值，
        // 而不是 Infinity —— 后者在界面上会显示成 "Infinity 秒"，
        // 对用户毫无意义，也容易被当成程序出错。
        if (!(corrected > 0) || Double.isInfinite(corrected)) {
            return 86400.0 * 365; // 一年，已经远超任何实际拍摄场景
        }
        return corrected;
    }

    /**
     * 补偿量换算成「档数」，用于告诉用户「要加几档」。
     * 摄影师对这个数字比秒数更有感觉。
     */
    static double stopsOf(double seconds, Profile profile) {
        double c = compensate(seconds, profile);
        if (!(seconds > 0) || !(c > 0) || Double.isInfinite(c)) return 0;
        return Math.log(c / seconds) / Math.log(2);
    }

    /** 是否需要补偿（超过阈值为真） */
    static boolean needsCompensation(double seconds, Profile profile) {
        if (profile == null) return false;
        if (!(seconds > 0)) return false;
        if (profile.p <= 0) return false;
        return seconds > profile.thresholdSec;
    }

    /**
     * 把秒数格式化成人能读的曝光时间。
     * 和相机上的刻度一致：小于 1 秒用 1/x，超过 60 秒用「x 分 y 秒」。
     */
    static String formatDuration(double seconds) {
        if (!(seconds > 0) || Double.isInfinite(seconds)) return "—";
        // 上限：超过 24 小时显示天数。不给上限时，一个误输入的极大值
        // 会打印出几十位数字的「分钟」，把界面撑成一坨。
        if (seconds >= 86400) {
            long days = Math.round(seconds / 86400.0);
            // 365 天以上已经不是摄影问题，而是输入错误。直接说清楚。
            if (days > 365) return "超过一年（请检查输入）";
            return days + " 天以上";
        }
        if (seconds < 1) {
            long denom = Math.round(1.0 / seconds);
            if (denom < 1) denom = 1;
            return "1/" + denom + " 秒";
        }
        if (seconds < 60) {
            // 一位小数足够，避免出现 3.7000000000000002 这种
            double r = Math.round(seconds * 10) / 10.0;
            // 59.99 会四舍五入成 60.0，和「1 分」重复但不矛盾，
            // 这里统一进位到分钟，避免出现「60.0 秒」这种相机上不存在的刻度。
            if (r >= 60.0) return "1 分";
            return String.format(java.util.Locale.US, "%.1f 秒", r);
        }
        long total = Math.round(seconds);
        long m = total / 60;
        long s = total % 60;
        if (s == 0) return m + " 分";
        return m + " 分 " + s + " 秒";
    }
}

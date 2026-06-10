package com.task.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务编号生成器
 * 格式：TASK + yyyyMMddHHmmss + 4位序列号
 * 示例：TASK202606091430220001
 */
public class TaskNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    /**
     * 生成任务编号
     */
    public static String generate() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        long sequence = SEQUENCE.incrementAndGet() % 10000;
        return String.format("TASK%s%04d", timestamp, sequence);
    }
}

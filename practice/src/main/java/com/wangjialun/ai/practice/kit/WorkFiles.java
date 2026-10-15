package com.wangjialun.ai.practice.kit;

import java.nio.file.Path;

/**
 * 练习数据根目录。和教材进程分开，避免写到 ToyAgent 的 wiki、events、sandbox。
 */
public final class WorkFiles {

    private WorkFiles() {
    }

    /** 可通过 -Dpractice.data 覆盖，默认落在仓库下的 practice/data。 */
    public static Path root() {
        return Path.of(System.getProperty("practice.data", "practice/data")).toAbsolutePath().normalize();
    }

    public static Path resolve(String... parts) {
        Path path = root();
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path;
    }
}

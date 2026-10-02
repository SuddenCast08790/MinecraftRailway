package org.slf4j;

/** 离线编译验证用的桩类。 */
public class LoggerFactory {
    public static Logger getLogger(String name) {
        return new Logger() {
            public void info(String msg, Object... a) {}
            public void warn(String msg, Object... a) {}
            public void error(String msg, Object... a) {}
        };
    }
}

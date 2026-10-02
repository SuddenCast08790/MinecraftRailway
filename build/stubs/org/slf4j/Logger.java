package org.slf4j;
public interface Logger {
    void info(String msg, Object... a);
    void warn(String msg, Object... a);
    void error(String msg, Object... a);
}

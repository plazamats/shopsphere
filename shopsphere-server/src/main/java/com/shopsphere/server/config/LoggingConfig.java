package com.shopsphere.server.config;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** One line per log record. Stack traces are only printed on the server, never sent to clients. */
public final class LoggingConfig {

    private LoggingConfig() { }

    public static void configure(Level level) {
        Logger root = LogManager.getLogManager().getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            private final DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            @Override
            public String format(LogRecord r) {
                StringBuilder sb = new StringBuilder();
                sb.append(time.format(ZonedDateTime.now(ZoneId.systemDefault()))).append(' ')
                  .append(String.format("%-7s", r.getLevel())).append(' ')
                  .append(shortName(r.getLoggerName())).append(" - ").append(formatMessage(r)).append('\n');
                if (r.getThrown() != null) {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    r.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                    sb.append(sw);
                }
                return sb.toString();
            }
        });
        root.addHandler(handler);
        root.setLevel(level);
    }

    private static String shortName(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(i + 1);
    }
}

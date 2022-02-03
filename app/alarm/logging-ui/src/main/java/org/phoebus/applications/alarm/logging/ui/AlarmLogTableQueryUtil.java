package org.phoebus.applications.alarm.logging.ui;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AlarmLogTableQueryUtil {

    static public Set<String> esFields = Stream.of(AlarmLogTableType.class.getDeclaredFields())
        .filter(f -> !f.isAnnotationPresent(JsonIgnore.class))
        .map(f -> f.getName())
        .collect(Collectors.toSet());

    static public String defaultField = "pv";

    // Ordered search keys
    public enum Keys {
        PV("pv"),
        SEVERITY("severity"),
        MESSAGE("message"),
        CURRENTSEVERITY("current_severity"),
        CURRENTMESSAGE("current_message"),
        USER("user"),
        HOST("host"),
        COMMAND("command"),
        STARTTIME("start"),
        ENDTIME("end");

        private final String name;

        Keys(String name) {
            this.name = name;
        };

        public String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}

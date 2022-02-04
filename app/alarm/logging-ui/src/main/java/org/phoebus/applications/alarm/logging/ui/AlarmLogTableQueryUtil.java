package org.phoebus.applications.alarm.logging.ui;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AlarmLogTableQueryUtil {

    static public Set<String> ES_FIELDS = Stream.of(AlarmLogTableType.class.getDeclaredFields())
        .filter(f -> !f.isAnnotationPresent(JsonIgnore.class))
        .map(f -> f.getName())
        .collect(Collectors.toSet());

    static public String DEFAULT_FIELD = "pv";

    static public enum FieldNames {
        CONFIG("config"),
        PV("pv"),
        SEVERITY("severity"),
        MESSAGE("message"),
        CURRENTSEVERITY("current_severity"),
        CURRENTMESSAGE("current_message"),
        USER("user"),
        HOST("host"),
        COMMAND("command"),
        MESSAGETIME("message_time"),
        TIME("time");

        private final String name;
        private final String prettyName;
        private final boolean isTime;


        FieldNames(String name) {
            this(name, name, false);
        }

        FieldNames(String name, String pretty, boolean isTime){
            this.name = name;
            this.prettyName = pretty;
            this.isTime = isTime;
        }

        public boolean isTime(){
            return isTime;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return prettyName;
        }

    }
}

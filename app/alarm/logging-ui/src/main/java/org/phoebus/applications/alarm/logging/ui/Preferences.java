package org.phoebus.applications.alarm.logging.ui;

import org.phoebus.framework.preferences.AnnotatedPreferences;
import org.phoebus.framework.preferences.Preference;

/** Alarm logging UI preferences
 *  @author Malte Gotz
 */

public class Preferences
{
    @Preference public static String[] hidden_columns;
    @Preference public static String es_host;
    @Preference public static int es_port;
    @Preference public static String es_index;
    @Preference public static int es_max_size;
    @Preference public static boolean es_sniff;

    static
    {
        AnnotatedPreferences.initialize(Preferences.class, "/alarm_logging_preferences.properties");
    }
}

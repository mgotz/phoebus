package org.phoebus.applications.alarm.logging.ui;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.sniff.Sniffer;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.framework.spi.AppResourceDescriptor;
import org.phoebus.ui.javafx.ImageCache;

import javafx.scene.image.Image;

public class AlarmLogTableApp implements AppResourceDescriptor {

    public static final Logger logger = Logger.getLogger(AlarmLogTableApp.class.getName());
    public static final String NAME = "alarm_log";
    public static final String DISPLAYNAME = "Alarm Log Table";

    public static final String SUPPORTED_SCHEMA = "alarmLog";

    public static final Image icon = ImageCache.getImage(AlarmLogTableApp.class, "/icons/alarmtable.png");

    private RestHighLevelClient client;
    private Sniffer sniffer;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return DISPLAYNAME;
    }

    @Override
    public AppInstance create() {
        return new AlarmLogTable(this);
    }
    /**
     * Support the launching of alarmLogtable using resource alarmLog://?<search_string>
     * e.g.
     * -resource alarmLog://?pv=SR*
     */
    @Override
    public AppInstance create(URI resource) {
        AlarmLogTable alarmLogTable = new AlarmLogTable(this, resource);
        return alarmLogTable;
    }

    @Override
    public boolean canOpenResource(String resource) {
        return URI.create(resource).getScheme().equals(SUPPORTED_SCHEMA);
    }

    @Override
    public void start() {
        try {
            client = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(Preferences.es_host, Preferences.es_port)));
            if (Preferences.es_sniff) {
                sniffer = Sniffer.builder(client.getLowLevelClient()).build();
                logger.log(Level.INFO, "ES Sniff feature is enabled");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to properly create the elastic rest client to: " + Preferences.es_host
                    + ":" + Preferences.es_port, e);
        }

    }

    @Override
    public void stop() {
        if (client != null) {
            try {
                if (sniffer != null) {
                    sniffer.close();
                }
                client.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to properly close the elastic rest client", e);
            }
        }

    }

    public RestHighLevelClient getClient() {
        return client;
    }


    /**
     * Build an alarmLog URI from the given (URL) query
     * @param query
     * @return URI
     */
    public static URI makeUri(String query) {
        try {
            return new URI(SUPPORTED_SCHEMA, "", "", query, "");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Can't create URI for query: " +  query);
        }
    }

    /**
     * Parse the query part of the URI into a lucene query string
     * @param resource URI to parse
     * @return lucen/elasticsearch query_string
     */
    static String parseUri(URI resource) {
        String luceneQueryString = "";
        String startTime = "*";
        String endTime = "*";
        List<SearchClause> extraClauses = new ArrayList<SearchClause>();

        for (String queryComponent : resource.getQuery().split("&")) {
            var keyVal = queryComponent.split("=", 2);
            final String key = keyVal.length > 1 ? keyVal[0] : "";
            final String val = keyVal.length > 1 ? keyVal[1] : keyVal[0];

            if (key.isEmpty()) {
                if (luceneQueryString.isEmpty()) {
                    luceneQueryString = val;
                } else {
                    logger.warning("Only a single query_string is supported, ignoring: " + val);
                }
            } else if (key.equals("start")) {
                startTime = val;
            } else if (key.equals("end")) {
                endTime = val;
            } else if (AlarmLogTableQueryUtil.ES_FIELDS.contains(key)) {
                extraClauses.add(new SearchClause(SearchClause.Negation.IS, key, val));
            } else {
                logger.fine("Ignoring unkown URI query key " + key);
            }
        }

        if (!startTime.equals("*") || !endTime.equals("*")) {
            extraClauses.add(new SearchClause(SearchClause.Negation.IS, "message_time", startTime, endTime, false));
        }

        final String extraQuery = SearchClause.listToQuery(extraClauses);
        final String separator = extraQuery.isBlank() || luceneQueryString.isBlank() ? "" : " ";
        final String combinedQuery = extraQuery + separator + luceneQueryString;
        return(combinedQuery);
    }

}

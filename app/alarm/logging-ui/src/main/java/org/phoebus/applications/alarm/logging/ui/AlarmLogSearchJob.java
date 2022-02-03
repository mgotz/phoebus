package org.phoebus.applications.alarm.logging.ui;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.temporal.TemporalAmount;
import javafx.collections.ObservableMap;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.search.QueryStringQueryParser;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.phoebus.applications.alarm.logging.ui.AlarmLogTableQueryUtil.Keys;
import org.phoebus.framework.jobs.Job;
import org.phoebus.framework.jobs.JobManager;
import org.phoebus.framework.jobs.JobMonitor;
import org.phoebus.framework.jobs.JobRunnable;
import org.phoebus.framework.preferences.PreferencesReader;
import org.phoebus.util.time.TimeParser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A Job to search for alarm messages logged by the alarm logging service
 * @author Kunal Shroff
 */
public class AlarmLogSearchJob implements JobRunnable {
    private final RestHighLevelClient client;
    private final String queryString;
    private final Consumer<List<AlarmLogTableType>> alarmMessageHandler;
    private final BiConsumer<String, Exception> errorHandler;

    private final ObjectMapper objectMapper;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.of("UTC"));
    private final PreferencesReader prefs = new PreferencesReader(AlarmLogTableApp.class,
            "/alarm_logging_preferences.properties");

    public static Job submit(RestHighLevelClient client,
                             final String queryString,
                             final Consumer<List<AlarmLogTableType>> alarmMessageHandler,
                             final BiConsumer<String, Exception> errorHandler) {
        return JobManager.schedule("searching alarm log messages for : " + queryString,
                new AlarmLogSearchJob(client, queryString, alarmMessageHandler, errorHandler));
    }

    private AlarmLogSearchJob(RestHighLevelClient client, String queryString,
            Consumer<List<AlarmLogTableType>> alarmMessageHandler, BiConsumer<String, Exception> errorHandler) {
        super();
        this.client = client;
        this.queryString = queryString;
        this.alarmMessageHandler = alarmMessageHandler;
        this.errorHandler = errorHandler;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void run(JobMonitor monitor) {
        monitor.beginTask("searching for alarm log entires : " + queryString);

        try {
            QueryParser luceneParser = new QueryParser("defaultField", new KeywordAnalyzer());
            luceneParser.setAllowLeadingWildcard(true);
            luceneParser.parse(queryString);
        }
        catch (ParseException e) {
            System.out.println("invalid query string: " + e.getMessage());
            // errorHandler.accept("failed to search: invalid query string ", e);
            // return;
        }

        QueryBuilder query = QueryBuilders.queryStringQuery(queryString);
        int size = prefs.getInt("es_max_size");

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder = sourceBuilder.query(query);
        sourceBuilder.size(size);
        sourceBuilder.sort("message_time", SortOrder.DESC);
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(sourceBuilder);
        List<AlarmLogTableType> result;
        try {
            result = Arrays.asList(client.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits()).stream()
                    .map(new Function<SearchHit, AlarmLogTableType>() {
                        @Override
                        public AlarmLogTableType apply(SearchHit hit) {
                            try {
                                JsonNode root = objectMapper.readTree(hit.getSourceAsString());
                                JsonNode time = ((ObjectNode) root).remove("time");
                                JsonNode message_time = ((ObjectNode) root).remove("message_time");
                                AlarmLogTableType alarmMessage = objectMapper.readValue(root.traverse(),
                                        AlarmLogTableType.class);
                                if (time != null) {
                                    Instant instant = LocalDateTime.parse(time.asText(), formatter)
					.atZone(ZoneId.of("UTC")).toInstant().atZone(ZoneId.systemDefault()).toInstant();
                                    alarmMessage.setInstant(instant);
                                }
                                if (message_time != null) {
                                    Instant instant = LocalDateTime.parse(message_time.asText(), formatter)
				        .atZone(ZoneId.of("UTC")).toInstant().atZone(ZoneId.systemDefault()).toInstant();
                                    alarmMessage.setMessage_time(instant);
                                }
                                if (alarmMessage.getPv() == null) {
                                    String config = alarmMessage.getConfig();
                                    String [] arrConfigStr = config.split("/");
                                    alarmMessage.setPv(arrConfigStr[arrConfigStr.length -1]);
                                }
                                return alarmMessage;
                            } catch (Exception e) {
                                errorHandler.accept("Failed to search for alarm logs ", e);
                                return null;
                            }
                        }
                    }).collect(Collectors.toList());
            alarmMessageHandler.accept(result);
        } catch (IOException e) {
            errorHandler.accept("Failed to search for alarm logs ", e);
        }
    }
}

package org.phoebus.applications.alarm.logging.ui;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.phoebus.framework.nls.NLS;
import org.phoebus.framework.persistence.Memento;
import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.ui.docking.DockItem;
import org.phoebus.ui.docking.DockPane;
import org.elasticsearch.client.RestHighLevelClient;

import javafx.fxml.FXMLLoader;

public class AlarmLogTable implements AppInstance {

    private final AlarmLogTableApp app;
    private DockItem tab;
    private AlarmLogTableController controller;

    AlarmLogTable(final AlarmLogTableApp app) {
        this(app, AlarmLogTableQueryUtil.DEFAULT_FIELD + ": *");
    }

    AlarmLogTable(final AlarmLogTableApp app, URI resource) {
        this(app, parseURI(resource));
    }

    AlarmLogTable(final AlarmLogTableApp app, String searchString) {
        this.app = app;
        try {
            ResourceBundle resourceBundle = NLS.getMessages(Messages.class);
            FXMLLoader loader = new FXMLLoader();
            loader.setResources(resourceBundle);
            loader.setLocation(this.getClass().getResource("AlarmLogTable.fxml"));
            loader.setControllerFactory(clazz -> {
                try {
                    if(clazz.isAssignableFrom(AlarmLogTableController.class)){
                        return clazz.getConstructor(RestHighLevelClient.class, String.class)
                                .newInstance(app.getClient(), searchString);
                    }
                    else {
                        return clazz.getConstructor().newInstance();
                    }
                } catch (Exception e) {
                    Logger.getLogger(AlarmLogTable.class.getName()).log(Level.SEVERE, "Failed to construct controller for Alarm Log Table View", e);
                }
                return null;
            });
            tab = new DockItem(this, loader.load());
            controller = loader.getController();
            tab.setOnClosed(event -> {
                controller.shutdown();
            });
            DockPane.getActiveDockPane().addTab(tab);
        } catch (IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Cannot load UI", e);
        }
    }

    @Override
    public AppDescriptor getAppDescriptor() {
        return app;
    }

    static String parseURI(URI resource) {
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
                    AlarmLogTableApp.logger.warning("Only a single query_string is supported, ignoring: " + val);
                }
            } else if (key.equals("start")) {
                startTime = val;
            } else if (key.equals("end")) {
                endTime = val;
            } else if (AlarmLogTableQueryUtil.ES_FIELDS.contains(key)) {
                extraClauses.add(new SearchClause(SearchClause.Negation.IS, key, val));
            } else {
                AlarmLogTableApp.logger.fine("Ignoring unkown URI query key " + key);
            }
        }

        if (!startTime.equals("*") || !endTime.equals("*")) {
            extraClauses.add(new SearchClause(SearchClause.Negation.IS, "message_time", startTime, endTime, false));
        }

        final String extraQuery = SearchClause.listToQuery(extraClauses);

        final String combinedQuery = extraQuery + " " + luceneQueryString;

        return(combinedQuery);
    }

    @Override
    public void save(Memento memento) {
        memento.setString("query_string", controller.query.getText());
        memento.setString("resize", controller.resize.getText());
        memento.setString("hidden_cols", controller.getHiddenCols().stream().collect(Collectors.joining(",")));
    }

    @Override
    public void restore(Memento memento) {
        var queryString = memento.getString("query_string");
        var resize = memento.getString("resize");
        var hiddenCols = memento.getString("hidden_cols");

        if (queryString.isPresent()) {
            controller.setQueryString(queryString.get());
        }
        if (resize.isPresent()) {
            controller.resize.setText(resize.get());
            controller.resize();
        }
        if (hiddenCols.isPresent()){
            controller.setHiddenCols(Set.of(hiddenCols.get().split(",")));
        }
    }

}

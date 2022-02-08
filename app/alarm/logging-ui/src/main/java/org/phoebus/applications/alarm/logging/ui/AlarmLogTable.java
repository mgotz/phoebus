package org.phoebus.applications.alarm.logging.ui;

import java.io.IOException;
import java.net.URI;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.phoebus.framework.nls.NLS;
import org.phoebus.framework.persistence.Memento;
import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.ui.docking.DockItemWithInput;
import org.phoebus.ui.docking.DockPane;
import org.elasticsearch.client.RestHighLevelClient;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;

public class AlarmLogTable implements AppInstance {

    private final AlarmLogTableApp app;
    private DockItemWithInput tab;
    private AlarmLogTableController controller;

    AlarmLogTable(final AlarmLogTableApp app) {
        this(app, AlarmLogTableQueryUtil.DEFAULT_FIELD + ": *");
    }

    AlarmLogTable(final AlarmLogTableApp app, URI resource) {
        this(app, AlarmLogTableApp.parseUri(resource));
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
            tab = new DockItemWithInput(this, loader.load(), AlarmLogTableApp.makeUri(searchString), null, null);
            Platform.runLater(() -> {tab.setLabel(app.getDisplayName());});
            controller = loader.getController();
            tab.addCloseCheck(()->controller.closeOkay());
            // tab.addClosedNotification(() -> {controller.shutdown();});
            controller.getQueryStringProperty().addListener((obs, oldVal, newVal) -> {
                tab.setInput(AlarmLogTableApp.makeUri(newVal));
                Platform.runLater(() -> {tab.setLabel(app.getDisplayName());});
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

    @Override
    public void save(Memento memento) {
        memento.setBoolean("query_builder_hidden", controller.getQueryBuilderHidden());
        memento.setString("hidden_cols", controller.getHiddenCols().stream().collect(Collectors.joining(",")));
    }

    @Override
    public void restore(Memento memento) {
        memento.getBoolean("query_builder_hidden").ifPresent(hide -> controller.hideQueryBuilder(hide));
        memento.getString("hidden_cols").ifPresent(cols -> controller.setHiddenCols(Set.of(cols.split(","))));;
    }
}

package org.phoebus.applications.alarm.logging.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.Duration;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.elasticsearch.client.RestHighLevelClient;
import org.phoebus.applications.alarm.model.SeverityLevel;
import org.phoebus.applications.alarm.ui.AlarmUI;
import org.phoebus.framework.jobs.Job;
import org.phoebus.ui.application.ContextMenuHelper;
import org.phoebus.ui.dialog.ExceptionDetailsErrorDialog;
import org.phoebus.util.time.TimestampFormats;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.phoebus.applications.alarm.logging.ui.AlarmLogTableApp.logger;

public class AlarmLogTableController {

    @FXML
    TableView<AlarmLogTableType> tableView;
    @FXML
    private QueryBuilderController queryBuilderController;
    @FXML
    TableColumn<AlarmLogTableType, String> configCol;
    @FXML
    TableColumn<AlarmLogTableType, String> pvCol;
    @FXML
    TableColumn<AlarmLogTableType, String> severityCol;
    @FXML
    TableColumn<AlarmLogTableType, String> messageCol;
    @FXML
    TableColumn<AlarmLogTableType, String> valueCol;
    @FXML
    TableColumn<AlarmLogTableType, String> timeCol;
    @FXML
    TableColumn<AlarmLogTableType, String> msgTimeCol;
    @FXML
    TableColumn<AlarmLogTableType, String> deltaTimeCol;
    @FXML
    TableColumn<AlarmLogTableType, String> currentSeverityCol;
    @FXML
    TableColumn<AlarmLogTableType, String> currentMessageCol;
    @FXML
    TableColumn<AlarmLogTableType, String> mode;
    @FXML
    TableColumn<AlarmLogTableType, String> commandCol;
    @FXML
    TableColumn<AlarmLogTableType, String> userCol;
    @FXML
    TableColumn<AlarmLogTableType, String> hostCol;
    @FXML
    Button resize;
    @FXML
    Button search;
    @FXML
    TextField query;
    @FXML
    GridPane ViewSearchPane;
    @FXML
    Label errorLabel;
    @FXML
    SplitPane topLevelNode;
    TableColumn<AlarmLogTableType, String> sortTableCol = null;
    SortType sortColType = null;

    // The query_string for the elastic search query
    private String searchString = AlarmLogTableQueryUtil.DEFAULT_FIELD + ":*";
    // Result
    private List<AlarmLogTableType> alarmMessages;

    private Job alarmLogSearchJob;
    private RestHighLevelClient searchClient;
    private double splitPosition = 0.3;

    @FXML
    private ProgressIndicator progressIndicator;
    private SimpleBooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty searchStringOkay = new SimpleBooleanProperty(true);

    public AlarmLogTableController(RestHighLevelClient client, String searchString) {
        setClient(client);
        setSearchString(searchString);
    }

    @FXML
    public void initialize() {
        resize.setText("<");
        tableView.getColumns().clear();
        configCol = new TableColumn<>("Config");
        configCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getConfig());
                    }
                });
        tableView.getColumns().add(configCol);

        pvCol = new TableColumn<>("PV");
        pvCol.setCellValueFactory(new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                return new SimpleStringProperty(alarmMessage.getValue().getPv());
            }
        });
        tableView.getColumns().add(pvCol);

        severityCol = new TableColumn<>("Severity");
        severityCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getSeverity());
                    }
                });
        severityCol.setCellFactory(alarmLogTableTypeStringTableColumn -> new TableCell<AlarmLogTableType, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty  ||  item == null)
                {
                    setText("");
                    setTextFill(Color.BLACK);
                }
                else
                {
                    setText(item);
                    setTextFill(AlarmUI.getColor(parseSeverityLevel(item)));
                }
            }
        });
        tableView.getColumns().add(severityCol);

        messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getMessage());
                    }
                });
        tableView.getColumns().add(messageCol);

        valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getValue());
                    }
                });
        tableView.getColumns().add(valueCol);

        timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        if (alarmMessage.getValue().getTime() != null) {
                            String time = TimestampFormats.MILLI_FORMAT.format(alarmMessage.getValue().getInstant());
                            return new SimpleStringProperty(time);
                        }
                        return null;
                    }
                });
        tableView.getColumns().add(timeCol);

        msgTimeCol = new TableColumn<>("Message Time");
        msgTimeCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        String time = TimestampFormats.MILLI_FORMAT.format(alarmMessage.getValue().getMessage_time());
                        return new SimpleStringProperty(time);
                    }
                });
        tableView.getColumns().add(msgTimeCol);

        deltaTimeCol = new TableColumn<>("Time Delta");
        deltaTimeCol.setCellValueFactory(
                alarmMessage -> {
                    java.time.Duration delta = java.time.Duration.between(alarmMessage.getValue().getMessage_time(), Instant.now());
                    return new SimpleStringProperty(delta.toHours() + ":" + delta.toMinutesPart() + ":" + delta.toSecondsPart()
                            + "." + delta.toMillisPart());
                });
        tableView.getColumns().add(deltaTimeCol);

        currentSeverityCol = new TableColumn<>("Current Severity");
        currentSeverityCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getCurrent_severity());
                    }
                });
        currentSeverityCol.setCellFactory(alarmLogTableTypeStringTableColumn -> new TableCell<AlarmLogTableType, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty  ||  item == null)
                {
                    setText("");
                    setTextFill(Color.BLACK);
                }
                else
                {
                    setText(item);
                    setTextFill(AlarmUI.getColor(parseSeverityLevel(item)));
                }
            }
        });
        tableView.getColumns().add(currentSeverityCol);

        currentMessageCol = new TableColumn<>("Current Message");
        currentMessageCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getCurrent_message());
                    }
                });
        tableView.getColumns().add(currentMessageCol);

        commandCol = new TableColumn<>("Command");
        commandCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        String action = alarmMessage.getValue().getCommand();
                        if (action != null) {
                            return new SimpleStringProperty(action);
                        }
                        boolean en = alarmMessage.getValue().isEnabled();
                        if (alarmMessage.getValue().getUser() != null && alarmMessage.getValue().getHost() != null) {
                            if (en == false) {
                                return new SimpleStringProperty("Disabled");
                            } else if (en == true) {
                                return new SimpleStringProperty("Enabled");
                            }
                        }
                        return null;
                    }
                });
        tableView.getColumns().add(commandCol);

        userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getUser());
                    }
                });
        tableView.getColumns().add(userCol);

        hostCol = new TableColumn<>("Host");
        hostCol.setCellValueFactory(
                new Callback<CellDataFeatures<AlarmLogTableType, String>, ObservableValue<String>>() {
                    @Override
                    public ObservableValue<String> call(CellDataFeatures<AlarmLogTableType, String> alarmMessage) {
                        return new SimpleStringProperty(alarmMessage.getValue().getHost());
                    }
                });
        tableView.getColumns().add(hostCol);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        setHiddenCols(Set.of(Preferences.hidden_columns));

        query.setText(searchString);

        query.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent keyEvent) {
                if (keyEvent.getCode() == KeyCode.ENTER) {
                    search();
                }
            }
        });

	    progressIndicator.visibleProperty().bind(searchInProgress);

        tableView.disableProperty().bind(searchInProgress.or(searchStringOkay.not()));

        // searchInProgress.addListener((observable, oldValue, newValue) -> {
        //     tableView.setDisable(newValue.booleanValue());
        // });

        search.disableProperty().bind(searchInProgress);
        errorLabel.visibleProperty().bind(searchStringOkay.not());
        search();
    }

    public void setHiddenCols(Set<String> hiddenCols) {
        for(TableColumn<AlarmLogTableType, ?> col : tableView.getColumns()) {
            if (hiddenCols.contains(col.getText())) {
                col.setVisible(false);
                logger.fine("hiding column " + col.getText());
            } else {
                col.setVisible(true);
            }
        }
    }

    public Collection<String> getHiddenCols(){
        return tableView.getColumns().filtered(col -> !col.isVisible()).stream()
            .map(col -> col.getText()).collect(Collectors.toList());
    }

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> runningTask;

    private void startPeriodicSearch() {
        logger.info("Starting a periodic search for alarm messages : " + searchString);
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        if (searchStringOkay.get()) {
            runningTask = executor.scheduleAtFixedRate(periodicSearch, 0, 10, TimeUnit.SECONDS);
        } else {
            searchInProgress.set(false);
        }
    }

    private Runnable periodicSearch = () -> {
        if (alarmLogSearchJob != null) {
            alarmLogSearchJob.cancel();
        }
        sortTableCol = null;
        sortColType = null;
        if (!tableView.getSortOrder().isEmpty()) {
            sortTableCol = (TableColumn) tableView.getSortOrder().get(0);
            sortColType = sortTableCol.getSortType();
        }
        alarmLogSearchJob = AlarmLogSearchJob.submit(searchClient, searchString,
                result -> Platform.runLater(() -> {
                    setAlarmMessages(result);
                    searchInProgress.set(false);
                    }), (url, ex) -> {
                    searchInProgress.set(false);
                    logger.log(Level.WARNING, "Shutting down alarm log message scheduler.", ex);
                    runningTask.cancel(true);
                });
    };

    public void setSearchString(String searchString) {
        try {
            QueryParser luceneParser = new QueryParser(AlarmLogTableQueryUtil.DEFAULT_FIELD, new KeywordAnalyzer());
            luceneParser.setAllowLeadingWildcard(true);
            luceneParser.parse(searchString);
            this.searchString = searchString;
            searchStringOkay.set(true);
        }
        catch (ParseException e) {
            errorLabel.setText(Messages.InvalidQuery + "\n" + e.getMessage());
            searchStringOkay.set(false);
        }
    }

    public List<AlarmLogTableType> getAlarmMessages() {
        return alarmMessages;
    }

    public void setAlarmMessages(List<AlarmLogTableType> alarmMessages) {
        this.alarmMessages = alarmMessages;
        tableView.setItems(FXCollections.observableArrayList(this.alarmMessages));
        if (sortTableCol != null) {
            tableView.getSortOrder().add(sortTableCol);
            sortTableCol.setSortType(sortColType);
            sortTableCol.setSortable(true);
        }
    }

    public void setClient(RestHighLevelClient client) {
        this.searchClient = client;
    }

    public void setSearchClauses(Collection<SearchClause> searchClauses) {
        queryBuilderController.setSearchClauses(searchClauses);
        query.setText(queryBuilderController.getQueryString());
        search();
    }

    public void setQueryString(String queryString) {
        query.setText(queryString);
        queryBuilderController.setQueryString(queryString);
        search();
    }

    /**
     * A Helper method which returns the appropriate {@link SeverityLevel} matching the
     * string level
     * @param level
     */
    private static SeverityLevel parseSeverityLevel(String level) {
        switch (level.toUpperCase()) {
            case "MINOR_ACK":
                return SeverityLevel.MINOR_ACK;
            case "MAJOR_ACK":
                return SeverityLevel.MAJOR_ACK;
            case "INVALID_ACK":
                return SeverityLevel.INVALID_ACK;
            case "UNDEFINED_ACK":
                return SeverityLevel.UNDEFINED_ACK;
            case "MINOR":
                return SeverityLevel.MINOR;
            case "MAJOR":
                return SeverityLevel.MAJOR;
            case "INVALID":
                return SeverityLevel.INVALID;
            case "UNDEFINED":
                return SeverityLevel.UNDEFINED;

            default:
                return SeverityLevel.OK;
        }

    }

    // Keeps track of when the animation is active. Multiple clicks will be ignored
    // until a give resize action is completed
    private AtomicBoolean moving = new AtomicBoolean(false);

    @FXML
    public void resize() {
        if (!moving.compareAndExchangeAcquire(false, true)) {
            if (resize.getText().equals(">")) {
                query.setDisable(false);
                query.setText(queryBuilderController.getQueryString());

                Duration cycleDuration = Duration.millis(400);
                queryBuilderController.getPane().setMinWidth(0);
                splitPosition = topLevelNode.getDividerPositions()[0];
                KeyValue kv = new KeyValue(topLevelNode.getDividers().get(0).positionProperty(), 0);
                Timeline timeline = new Timeline(new KeyFrame(cycleDuration, kv));
                timeline.play();
                timeline.setOnFinished(event -> {
                    resize.setText("<");
                    queryBuilderController.getPane().setMaxWidth(0);
                    queryBuilderController.setDisable(true);
                    moving.set(false);
                });

            } else {
                queryBuilderController.setQueryString(query.getText());
                queryBuilderController.setDisable(false);
                query.setDisable(true);

                queryBuilderController.getPane().setMaxWidth(Pane.USE_COMPUTED_SIZE);
                Duration cycleDuration = Duration.millis(400);
                KeyValue kv = new KeyValue(topLevelNode.getDividers().get(0).positionProperty(), splitPosition);
                Timeline timeline = new Timeline(new KeyFrame(cycleDuration, kv));
                timeline.play();
                timeline.setOnFinished(event -> {
                    resize.setText(">");
                    queryBuilderController.getPane().setMinWidth(Pane.USE_COMPUTED_SIZE);
                    moving.set(false);
                });
            }
        }
    }

    @FXML
    void updateQuery() {
        if (query.isDisabled()) {
            query.setText(queryBuilderController.getQueryString());
        }
        if (query.getText().isBlank()) {
            query.setText("*");
        }
        setSearchString(query.getText());
    }

    @FXML
    public void search() {
        searchInProgress.set(true);
        tableView.getSortOrder().clear();
        updateQuery();
        startPeriodicSearch();
    }

    @FXML
    public void createContextMenu() {
        final ContextMenu contextMenu = new ContextMenu();
        MenuItem configurationInfo = new MenuItem("Configuration Info");
        configurationInfo.setOnAction( actionEvent -> {
            List<String> configs = tableView.getSelectionModel().getSelectedItems()
                    .stream().map(e -> {
                        try {
                            URI uri = new URI(e.getConfig().replace(" ", "%20"));
                            return uri.getSchemeSpecificPart();
                        } catch (URISyntaxException ex) {
                            ex.printStackTrace();
                        }
                        return null;
                    })
                    .collect(Collectors.toList());
            // TODO place holder method for showing additional alarm info
            AlarmLogConfigSearchJob.submit(searchClient,
                    configs.get(0),
                    result -> Platform.runLater(() -> {
                        Alert alarmInfo = new Alert(Alert.AlertType.INFORMATION);
                        alarmInfo.setTitle("Alarm information");
                        alarmInfo.setHeaderText(null);
                        alarmInfo.setResizable(true);
                        alarmInfo.setContentText(result.get(0));
                        alarmInfo.show();
                    }),
                    (url, ex) -> ExceptionDetailsErrorDialog.openError("Alarm Log Info Error", ex.getMessage(), ex)
            );

        });
        contextMenu.getItems().add(configurationInfo);

        contextMenu.getItems().add(new SeparatorMenuItem());
        ContextMenuHelper.addSupportedEntries(tableView, contextMenu);

        tableView.setContextMenu(contextMenu);

    }

    public void shutdown() {
        if (runningTask != null) {
            runningTask.cancel(true);
        }
    }

}

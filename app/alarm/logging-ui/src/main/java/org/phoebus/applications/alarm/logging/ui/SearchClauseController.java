package org.phoebus.applications.alarm.logging.ui;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;

import org.phoebus.ui.dialog.PopOver;
import org.phoebus.ui.time.TimeRelativeIntervalPane;
import org.phoebus.util.time.TimeParser;
import org.phoebus.util.time.TimeRelativeInterval;
import org.phoebus.util.time.TimestampFormats;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import static org.phoebus.ui.time.TemporalAmountPane.Type.TEMPORAL_AMOUNTS_AND_NOW;

public class SearchClauseController extends VBox {

    @FXML private ChoiceBox<SearchClause.Negation> occur_CB;
    @FXML private ChoiceBox<MatchEnum> matchType_CB;
    @FXML private TextField term1_TF;
    @FXML private TextField term2_TF;
    @FXML private Label toLabel;
    @FXML private TextField fieldName_TF;
    @FXML private CheckBox wildcard_CHK;
    PopOver timeSearchPopover;
    TimeRelativeIntervalPane timeSelectionPane = new TimeRelativeIntervalPane(TEMPORAL_AMOUNTS_AND_NOW);

    private ChangeListener<Boolean> showPopover = ((obs, oldVal, newVal) -> {
        if (newVal) {
            setTimePaneStart();
            setTimePaneEnd();
            timeSearchPopover.show(this);
        } else if (timeSearchPopover.isShowing()) {
            timeSearchPopover.hide();
        }
    });


    private StringProperty fieldName = new SimpleStringProperty();

    private SearchClause clause;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.of("UTC"));

    public SearchClauseController(){
        this(new ArrayList<String>());
    }

    public SearchClauseController(Collection<String> nameChoices) {
        this(new SearchClause(SearchClause.Negation.IS, getFirstElement(nameChoices), "*"), nameChoices);
    }

    public SearchClauseController(SearchClause clause) {
        this(clause, new ArrayList<String>());
    }

    public SearchClauseController(SearchClause clause, Collection<String> nameChoices) {
        // set clause first, to avoid null references, but call setClause, after
        // listener setup to ensure correct display.
        this.clause = clause;
        setupGui(nameChoices);
        connectGuiListeners();
        setClause(clause);

    }

    private static String getFirstElement(Collection<String> choices){
        String first = "";
        try {first = choices.iterator().next();}
        catch (NoSuchElementException e) {}
        return first;
    }

    private void setupGui(Collection<String> nameChoices) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("SearchClauseView.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        if (nameChoices.isEmpty()) {
            fieldName = fieldName_TF.textProperty();

        }
        else {
            ChoiceBox<String> nameBox = new ChoiceBox<String>(FXCollections.observableArrayList(nameChoices));
            nameBox.valueProperty().bindBidirectional(fieldName);
            this.getChildren().remove(fieldName_TF);
            this.getChildren().add(0, nameBox);
            HBox.setHgrow(nameBox, Priority.ALWAYS);
        }

        occur_CB.getItems().add(SearchClause.Negation.IS);
        occur_CB.getItems().add(SearchClause.Negation.IS_NOT);

        matchType_CB.getItems().addAll(MatchEnum.values());

        makeTimePopOver();
    }

    private void connectGuiListeners() {
        // on invisible, make these also unmanged to resize everything else
        term2_TF.managedProperty().bind(term2_TF.visibleProperty());
        toLabel.managedProperty().bind(toLabel.visibleProperty());

        occur_CB.getSelectionModel().selectedItemProperty().addListener((observable, oldVal, newVal) -> {
            clause.setOccur(newVal);
        });

        fieldName.addListener((observable, oldVal, newVal) -> {
            clause.setFieldName(newVal);
        });

        term1_TF.textProperty().addListener((obs, oldVal, newVal) -> {
            clause.setTerm1(convertInput(newVal));
        });

        term2_TF.textProperty().addListener((obs, oldVal, newVal) -> {
            clause.setTerm2(convertInput(newVal));
        });

        matchType_CB.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            setVisibility(newVal);
        });

        wildcard_CHK.selectedProperty().addListener((obs, oldVal, newVal) -> {
            clause.setWildcardLiteral(!newVal);
        });
    }

    private void makeTimePopOver(){
        VBox timeBox = new VBox();
        HBox hbox = new HBox();
        hbox.setSpacing(5);
        hbox.setAlignment(Pos.CENTER_RIGHT);
        Button apply = new Button();
        apply.setText("Apply");
        apply.setPrefWidth(80);
        apply.setOnAction((event) -> {
            TimeRelativeInterval interval = timeSelectionPane.getInterval();
            if (interval.isStartAbsolute()) {
                term1_TF.setText(TimestampFormats.MILLI_FORMAT.format(interval.getAbsoluteStart().get()));
            } else {
                term1_TF.setText(TimeParser.format(interval.getRelativeStart().get()));
            }
            if (interval.isEndAbsolute()) {
                term2_TF.setText(TimestampFormats.MILLI_FORMAT.format(interval.getAbsoluteEnd().get()));
            } else {
                term2_TF.setText(TimeParser.format(interval.getRelativeEnd().get()));
            }
            if (timeSearchPopover.isShowing())
                timeSearchPopover.hide();
        });
        Button cancel = new Button();
        cancel.setText("Cancel");
        cancel.setPrefWidth(80);
        cancel.setOnAction((event) -> {
            if (timeSearchPopover.isShowing())
                timeSearchPopover.hide();
        });
        hbox.getChildren().addAll(apply, cancel);
        timeBox.getChildren().addAll(timeSelectionPane, hbox);
        timeSearchPopover = new PopOver(timeBox);
    }

    void setTimePaneStart() {
        Object startTime = TimeParser.parseInstantOrTemporalAmount(term1_TF.getText());
        if (startTime == null) {
            System.out.println("start is null");
            timeSelectionPane.setStart(Duration.ofHours(8));
        } else if (startTime instanceof Instant) {
            System.out.println("start is Instant: " + startTime.toString());
            timeSelectionPane.setStart((Instant) startTime);
        } else if (startTime instanceof TemporalAmount) {
            System.out.println("start is amount: " + startTime.toString());
            timeSelectionPane.setStart((TemporalAmount) startTime);
        }
    }

    void setTimePaneEnd () {
        Object endTime = TimeParser.parseInstantOrTemporalAmount(term2_TF.getText());
        if (endTime == null) {
            timeSelectionPane.setEnd(Duration.ZERO);
        } else if (endTime instanceof Instant) {
            timeSelectionPane.setEnd((Instant) endTime);
        } else if (endTime instanceof TemporalAmount) {
            timeSelectionPane.setEnd((TemporalAmount) endTime);
        }
    }

    String convertInput(String rawInput){
        String convertedInput = rawInput;
        if (matchType_CB.getSelectionModel().getSelectedItem() == MatchEnum.DATERANGE){
            Object time = TimeParser.parseInstantOrTemporalAmount(rawInput);
            if (time instanceof Instant) {
                convertedInput =  formatter.format((Instant)time);
            } else if (time instanceof TemporalAmount) {
                convertedInput = formatter.format(Instant.now().minus((TemporalAmount)time));
            }
        }
        return convertedInput;
    }

    void setVisibility(MatchEnum matchType) {
        term1_TF.focusedProperty().removeListener(showPopover);
        term2_TF.focusedProperty().removeListener(showPopover);
        switch (matchType) {
            case TERM:
                clause.setTerm2("");
                toLabel.setVisible(false);
                term2_TF.setVisible(false);
                // wildcard_CHK.setVisible(true);
                break;
            case RANGE:
                if (term2_TF.getText().isEmpty()){
                    term2_TF.textProperty().set("*");
                }
                toLabel.setVisible(true);
                term2_TF.setVisible(true);
                // wildcard_CHK.setVisible(false);
                // wildcard_CHK.setSelected(true);
                break;
            case DATERANGE:
                term1_TF.focusedProperty().addListener(showPopover);
                term2_TF.focusedProperty().addListener(showPopover);
                if (term2_TF.getText().isEmpty()){
                    term2_TF.textProperty().set("*");
                }
                toLabel.setVisible(true);
                term2_TF.setVisible(true);
                break;
            default:
                break;
        }
    }

    public SearchClause getClause() {
        return clause;
    }

    public void setClause(SearchClause clause) {
        this.clause = clause;
        occur_CB.getSelectionModel().select(clause.getOccur());
        fieldName.set(clause.getFieldName());
        term1_TF.textProperty().set(clause.getTerm1());
        wildcard_CHK.setSelected(!clause.getWildcardLiteral());

        final String term2Temp = clause.getTerm2();
        term2_TF.textProperty().set(term2Temp);

        if (term2Temp.isEmpty()) {
            matchType_CB.getSelectionModel().select(MatchEnum.TERM);
        }
        else {
            matchType_CB.getSelectionModel().select(MatchEnum.RANGE);;
        }
        setVisibility(matchType_CB.getSelectionModel().getSelectedItem());
    }

    public static enum MatchEnum {
        RANGE("in Range"),
        DATERANGE("in Time"),
        TERM("Term");

        public final String prettyString;

        private MatchEnum(String str) {
            prettyString = str;
        }

        @Override
        public String toString() {
            return prettyString;
        }
    }
}


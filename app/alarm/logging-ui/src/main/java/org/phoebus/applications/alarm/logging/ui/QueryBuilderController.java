package org.phoebus.applications.alarm.logging.ui;

import java.util.Collection;

import org.apache.lucene.queryparser.classic.ParseException;
import org.phoebus.applications.alarm.logging.ui.SearchClause.QueryNotSupported;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;

public class QueryBuilderController {

    @FXML private ListView<SearchClause> clausesView;
    @FXML private Button add;
    @FXML private Button remove;
    @FXML private Label infoLabel;
    @FXML private Label errorLabel;
    @FXML private AnchorPane queryBuilderPane;
    private String queryString;
    private Boolean queryEditable;


    public QueryBuilderController(){
        queryString = "";
        queryEditable = true;
    }

    @FXML
    public void initialize() {
        queryBuilderPane.minWidthProperty().set(0);
        queryBuilderPane.maxWidthProperty().set(0);

        clausesView.setCellFactory(param -> new SearchClauseCell());

        infoLabel.setVisible(false);
        errorLabel.setVisible(false);

        connectListeners();
    }

    private void connectListeners() {
        add.setOnAction(actionEvent ->  {
            clausesView.getItems().add(new SearchClause("pv"));
        });
        remove.setOnAction(actionEvent -> {
            final int idx = clausesView.getSelectionModel().getSelectedIndex();
            clausesView.getItems().remove(idx);
        });
    }

    public String getQueryString() {
        if (queryEditable) {
            queryString = SearchClause.listToQuery(clausesView.getItems());
        }
        return queryString;
    }

    public void setQueryString(String query) {
        queryEditable = false;
        queryString = query;
        try {
            var clauseList = SearchClause.parseQueryString(query, AlarmLogTableQueryUtil.defaultField);
            clausesView.getItems().clear();
            clausesView.getItems().addAll(clauseList);
            infoLabel.setVisible(false);
            errorLabel.setVisible(false);
            queryEditable = true;
        }
        catch (ParseException e) {
            errorLabel.setText("Invalid query string:\n" + e.getMessage());
            errorLabel.setVisible(true);
            infoLabel.setVisible(false);
        }
        catch (QueryNotSupported e) {
            infoLabel.setText("query is too comlex for GUI:\n" + e.getMessage());
            errorLabel.setVisible(false);
            infoLabel.setVisible(true);
        }

        setDisable(!queryEditable);
    }

    public void setSearchClauses(Collection<SearchClause> clauses) {
        clausesView.getItems().clear();
        clausesView.getItems().addAll(clauses);
    }

    public void setDisable(boolean value){
        boolean disableValue = value || !queryEditable;
        clausesView.setDisable(disableValue);
        add.setDisable(disableValue);
        remove.setDisable(disableValue);
    }

    public Pane getPane(){
        return queryBuilderPane;
    }



    static class SearchClauseCell extends ListCell<SearchClause> {

        // use as fieldname choices the fields from the AlarmLogTableType
        // that are not marked as JsonIgnro.
        // This should contain all the fields in the json returned by
        // elastic search.
        private SearchClauseController controller = new SearchClauseController(AlarmLogTableQueryUtil.esFields);
        @Override
        public void updateItem(SearchClause item, boolean empty) {
            super.updateItem(item, empty);
            if (empty){
                setText(null);
                setGraphic(null);
            }
            else {
                controller.setClause(item);
                setGraphic(controller);
            }
        }
    }
}

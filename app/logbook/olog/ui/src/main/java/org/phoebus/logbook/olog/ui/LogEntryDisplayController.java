package org.phoebus.logbook.olog.ui;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.phoebus.framework.jobs.JobManager;
import org.phoebus.logbook.Attachment;
import org.phoebus.logbook.LogClient;
import org.phoebus.logbook.LogEntry;
import org.phoebus.logbook.Logbook;
import org.phoebus.logbook.Tag;
import org.phoebus.ui.dialog.ExceptionDetailsErrorDialog;
import org.phoebus.ui.javafx.ImageCache;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.phoebus.util.time.TimestampFormats.MILLI_FORMAT;

public class LogEntryDisplayController {

    static final Image tag = ImageCache.getImage(LogEntryDisplayController.class, "/icons/add_tag.png");
    static final Image logbook = ImageCache.getImage(LogEntryDisplayController.class, "/icons/logbook-16.png");
    private final LogClient logClient;

    private HtmlRenderer htmlRenderer;
    private Parser parser;

    @FXML
    Label logTime;
    @FXML
    Label logOwner;
    @FXML
    Label logTitle;
    @FXML
    WebView logDescription;

    @FXML
    HBox metaDataBox;
    @FXML
    ListView<String> logTags;
    @FXML
    ListView<String> LogLogbooks;

    @FXML
    public TitledPane attachmentsPane;
    @FXML
    public AttachmentsPreviewController attachmentsPreviewController;

    @FXML
    public TitledPane propertiesPane;
    @FXML
    public VBox properties;
    @FXML
    public LogPropertiesController propertiesController;
    @FXML
    private Button downloadButton;

    private LogEntry logEntry;

    public LogEntryDisplayController() {
        this.logClient = null;
    }

    public LogEntryDisplayController(LogClient logClient) {
        this.logClient = logClient;
    }

    /**
     * List of attachments selected in the preview's {@link ListView}.
     */

    private ObservableList<Attachment> selectedAttachments = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        logTime.setStyle("-fx-font-weight: bold");
        logTitle.setStyle("-fx-font-weight: bold");

        logTags.setVisible(false);
        LogLogbooks.setVisible(false);

        logTags.setCellFactory(listView -> new ListCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setGraphic(new ImageView(tag));
                    setText(item);
                }
            }
        });

        LogLogbooks.setCellFactory(listView -> new ListCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setGraphic(new ImageView(logbook));
                    setText(item);
                }
            }
        });

        List<Extension> extensions = Arrays.asList(TablesExtension.create(), ImageAttributesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        htmlRenderer = HtmlRenderer.builder()
                .attributeProviderFactory(context -> new OlogAttributeProvider())
                .extensions(extensions).build();

        downloadButton.disableProperty().bind(Bindings.isEmpty(selectedAttachments));
        attachmentsPreviewController.addListSelectionChangeListener(change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    selectedAttachments.addAll(change.getAddedSubList());
                }
                if (change.wasRemoved()) {
                    selectedAttachments.removeAll(change.getRemoved());
                }
            }
        });
    }

    public void refresh() {
        if (logEntry != null) {

            attachmentsPane.setExpanded(logEntry.getAttachments() != null && !logEntry.getAttachments().isEmpty());
            attachmentsPane.setVisible(logEntry.getAttachments() != null && !logEntry.getAttachments().isEmpty());

            propertiesPane.setExpanded(logEntry.getProperties() != null && !logEntry.getProperties().isEmpty());
            propertiesPane.setVisible(logEntry.getProperties() != null && !logEntry.getProperties().isEmpty());

            int metaDataCount = logEntry.getLogbooks().size() >= logEntry.getTags().size() ? logEntry.getLogbooks().size() : logEntry.getTags().size();
            metaDataBox.setPrefHeight(metaDataCount * 60);

            logTags.setVisible(!logEntry.getTags().isEmpty());
            logTags.setPrefHeight(metaDataCount * 60);
            LogLogbooks.setVisible(!logEntry.getLogbooks().isEmpty());
            LogLogbooks.setPrefHeight(metaDataCount * 60);

            logTime.setText(MILLI_FORMAT.format(logEntry.getCreatedDate()));

            logOwner.setText(logEntry.getOwner());

            logTitle.setWrapText(true);
            logTitle.setText(logEntry.getTitle());

            logDescription.setDisable(true);
            // Content is defined by the source (default) or description field. If both are null
            // or empty, do no load any content to the WebView.
            WebEngine webEngine = logDescription.getEngine();
            webEngine.setUserStyleSheetLocation(getClass()
                    .getResource("/detail-log-webview.css").toExternalForm());


            if (logEntry.getSource() != null) {
                webEngine.loadContent(toHtml(logEntry.getSource()));
            } else if (logEntry.getDescription() != null) {
                webEngine.loadContent(toHtml(logEntry.getDescription()));
            }
            ObservableList<String> logbookList = FXCollections.observableArrayList();
            logbookList.addAll(logEntry.getLogbooks().stream().map(Logbook::getName).collect(Collectors.toList()));
            LogLogbooks.setItems(logbookList);

            ObservableList<String> tagList = FXCollections.observableArrayList();
            tagList.addAll(logEntry.getTags().stream().map(Tag::getName).collect(Collectors.toList()));
            logTags.setItems(tagList);

            attachmentsPreviewController
                    .setAttachments(FXCollections.observableArrayList(logEntry.getAttachments()));

            if (!logEntry.getProperties().isEmpty()) {
                propertiesController.setProperties(logEntry.getProperties());
            }
        }
    }

    public LogEntry getLogEntry() {
        return logEntry;
    }

    public void setLogEntry(LogEntry logEntry) {
        this.logEntry = logEntry;
        refresh();
    }

    /**
     * Converts Commonmark content to HTML.
     *
     * @param commonmarkString Raw Commonmark string
     * @return The HTML output of the Commonmark processor.
     */
    private String toHtml(String commonmarkString) {
        org.commonmark.node.Node document = parser.parse(commonmarkString);
        String html = htmlRenderer.render(document);
        // Wrap the content in a named div so that a suitable height may be determined.
        return "<div id='olog'>\n" + html + "</div>";
    }

    /**
     * An {@link AttributeProvider} used to style elements of a log entry. Other types of
     * attribute processing may be added.
     */
    class OlogAttributeProvider implements AttributeProvider {

        /**
         * Processes image nodes to prepend the service root URL, where needed. For table nodes the olog-table
         * class is added in order to give it some styling.
         *
         * @param node The {@link org.commonmark.node.Node} being processed.
         * @param s    The HTML tag, e.g. p, img, strong etc.
         * @param map  Map of attributes for the node.
         */
        @Override
        public void setAttributes(org.commonmark.node.Node node, String s, Map<String, String> map) {
            if (node instanceof TableBlock) {
                map.put("class", "olog-table");
            }
            // Image paths may be relative (when added through dialog), or absolute URLs (e.g. when added "manually" in editor).
            // Relative paths must be prepended with service root URL, while absolute URLs must not be changed.
            if (node instanceof org.commonmark.node.Image) {
                String src = map.get("src");
                if (!src.toLowerCase().startsWith("http")) {
                    String serviceUrl = logClient.getServiceUrl();
                    if (serviceUrl.endsWith("/")) {
                        serviceUrl = serviceUrl.substring(0, serviceUrl.length() - 1);
                    }
                    src = serviceUrl + "/" + src;
                }
                map.put("src", src);
            }
        }
    }

    /**
     * Downloads all selected attachments to folder selected by user.
     */
    @FXML
    public void downloadSelectedAttachments(){
        final DirectoryChooser dialog = new DirectoryChooser();
        dialog.setTitle(Messages.SelectFolder);
        dialog.setInitialDirectory(new File(System.getProperty("user.home")));
        File targetFolder = dialog.showDialog(attachmentsPane.getScene().getWindow());
        JobManager.schedule("Save attachments job", (monitor) ->
        {
            selectedAttachments.stream().forEach(a -> downloadAttachment(targetFolder, a));
        });
    }

    private void downloadAttachment(File targetFolder, Attachment attachment){
        try {
            File targetFile = new File(targetFolder, attachment.getName());
            if(targetFile.exists()){
                throw new Exception("Target file " + targetFile.getAbsolutePath() + " exists");
            }
            Files.copy(attachment.getFile().toPath(), targetFile.toPath());
        } catch (Exception e) {
            ExceptionDetailsErrorDialog.openError(attachmentsPane.getParent(), Messages.FileSave, Messages.FileSaveFailed, e);
        }
    }
}
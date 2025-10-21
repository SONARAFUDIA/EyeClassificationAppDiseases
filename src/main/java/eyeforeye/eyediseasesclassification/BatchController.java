package eyeforeye.eyediseasesclassification;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class BatchController {

    @FXML
    private Button uploadBatchButton, predictBatchButton, clearButton;

    @FXML
    private TableView<BatchResult> resultTable;

    @FXML
    private TableColumn<BatchResult, String> fileNameColumn;

    @FXML
    private TableColumn<BatchResult, String> predictedClassColumn;

    @FXML
    private TableColumn<BatchResult, String> confidenceColumn;

    @FXML
    private TableColumn<BatchResult, String> statusColumn;

    @FXML
    private ImageView selectedImageView;

    @FXML
    private Label detailLabel;

    @FXML
    private Label summaryLabel;

    @FXML
    private ProgressBar progressBar;

    private ModelPredictor modelPredictor;
    private ObservableList<BatchResult> results;
    private File lastDirectory;

    @FXML
    public void initialize() {
        // Setup table columns
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        predictedClassColumn.setCellValueFactory(new PropertyValueFactory<>("predictedClass"));
        confidenceColumn.setCellValueFactory(new PropertyValueFactory<>("confidencePercent"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Initialize observable list
        results = FXCollections.observableArrayList();
        resultTable.setItems(results);

        // Add selection listener
        resultTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    showImageDetail(newSelection);
                }
            }
        );

        // Disable predict button initially
        predictBatchButton.setDisable(true);
        progressBar.setVisible(false);

        summaryLabel.setText("Belum ada gambar yang dipilih");

        // Load model
        loadModel();
    }

    private void loadModel() {
        detailLabel.setText("⏳ Memuat model...");
        
        new Thread(() -> {
            try {
                modelPredictor = new ModelPredictor("saved_model");
                
                Platform.runLater(() -> {
                    detailLabel.setText("✅ Model siap! Silakan upload gambar.");
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    detailLabel.setText("❌ Gagal memuat model: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void onUploadBatchClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Pilih Gambar Mata (Multiple)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File Gambar", "*.png", "*.jpg", "*.jpeg")
        );

        if (lastDirectory != null && lastDirectory.exists()) {
            fileChooser.setInitialDirectory(lastDirectory);
        }

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(
                uploadBatchButton.getScene().getWindow()
        );

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            lastDirectory = selectedFiles.get(0).getParentFile();
            
            // Clear previous results
            results.clear();
            
            // Add placeholder results
            for (File file : selectedFiles) {
                results.add(new BatchResult(file, "Menunggu..."));
            }

            predictBatchButton.setDisable(false);
            summaryLabel.setText(String.format("📁 %d gambar dipilih", selectedFiles.size()));
            detailLabel.setText("Klik 'Prediksi Batch' untuk memulai");
        }
    }

    @FXML
    private void onPredictBatchClicked() {
        if (results.isEmpty() || modelPredictor == null) {
            return;
        }

        // Disable buttons during prediction
        uploadBatchButton.setDisable(true);
        predictBatchButton.setDisable(true);
        clearButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        detailLabel.setText("🔄 Memproses prediksi batch...");

        // Process in background thread
        new Thread(() -> {
            int total = results.size();
            int processed = 0;

            for (int i = 0; i < total; i++) {
                final int index = i;
                BatchResult placeholder = results.get(i);
                File imageFile = placeholder.getImageFile();

                // Predict
                BatchResult result = modelPredictor.predictDetailed(imageFile);

                // Update UI
                final int currentProcessed = ++processed;
                Platform.runLater(() -> {
                    results.set(index, result);
                    progressBar.setProgress((double) currentProcessed / total);
                    detailLabel.setText(String.format(
                        "🔄 Memproses... %d/%d", currentProcessed, total
                    ));
                });

                // Small delay to prevent UI freeze
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Show summary
            Platform.runLater(() -> {
                uploadBatchButton.setDisable(false);
                predictBatchButton.setDisable(false);
                clearButton.setDisable(false);
                progressBar.setVisible(false);

                long successCount = results.stream().filter(BatchResult::isSuccess).count();
                long errorCount = total - successCount;

                summaryLabel.setText(String.format(
                    "✅ Selesai! Sukses: %d, Error: %d", successCount, errorCount
                ));
                detailLabel.setText("Klik baris tabel untuk melihat detail gambar");
            });
        }).start();
    }

    @FXML
    private void onClearClicked() {
        results.clear();
        selectedImageView.setImage(null);
        detailLabel.setText("Tabel hasil dibersihkan");
        summaryLabel.setText("Belum ada gambar yang dipilih");
        predictBatchButton.setDisable(true);
    }

    private void showImageDetail(BatchResult result) {
        try {
            Image image = new Image(result.getImageFile().toURI().toString());
            selectedImageView.setImage(image);

            if (result.isSuccess()) {
                detailLabel.setText(String.format(
                    "📊 %s\n" +
                    "Prediksi: %s\n" +
                    "Confidence: %s\n" +
                    "File: %s",
                    result.getFileName(),
                    result.getPredictedClass(),
                    result.getConfidencePercent(),
                    result.getImageFile().getAbsolutePath()
                ));
            } else {
                detailLabel.setText(String.format(
                    "❌ %s\n" +
                    "Error: %s\n" +
                    "File: %s",
                    result.getFileName(),
                    result.getErrorMessage(),
                    result.getImageFile().getAbsolutePath()
                ));
            }
        } catch (Exception e) {
            detailLabel.setText("❌ Gagal memuat gambar: " + e.getMessage());
        }
    }

    public void cleanup() {
        if (modelPredictor != null) {
            modelPredictor.close();
        }
    }
}
package eyeforeye.eyediseasesclassification;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EvaluationController {

    @FXML
    private Button selectFolderButton, evaluateButton;
    @FXML
    private Label folderLabel, statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TextArea resultTextArea;

    private File testDatasetDirectory;
    private ModelPredictor modelPredictor;
    private final String[] classNames = ModelPredictor.getClasses(); // Mendapat nama kelas

    @FXML
    public void initialize() {
        statusLabel.setText("Status: Memuat model...");
        new Thread(() -> {
            try {
                modelPredictor = new ModelPredictor("saved_model");
                Platform.runLater(() -> statusLabel.setText("Status: Model siap! Pilih folder dataset uji."));
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Status: Gagal memuat model! " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void onSelectFolderClicked() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Pilih Folder Dataset Uji");
        File selectedDirectory = directoryChooser.showDialog(selectFolderButton.getScene().getWindow());

        if (selectedDirectory != null) {
            testDatasetDirectory = selectedDirectory;
            folderLabel.setText(selectedDirectory.getAbsolutePath());
            evaluateButton.setDisable(false);
            statusLabel.setText("Status: Folder dipilih. Klik 'Mulai Evaluasi'.");
        }
    }

    @FXML
    private void onEvaluateClicked() {
        if (testDatasetDirectory == null || modelPredictor == null) {
            statusLabel.setText("Status: Error! Model atau folder belum siap.");
            return;
        }

        // Nonaktifkan tombol
        evaluateButton.setDisable(true);
        selectFolderButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        resultTextArea.clear();

        // Jalankan di thread baru
        new Thread(this::runEvaluationTask).start();
    }

    private void runEvaluationTask() {
        try {
            List<MetricsCalculator.PredictionTuple> results = new ArrayList<>();
            List<File> allImageFiles = new ArrayList<>();
            
            // 1. Kumpulkan semua file gambar DAHULU
            for (String className : classNames) {
                File classDir = new File(testDatasetDirectory, className);
                if (classDir.exists() && classDir.isDirectory()) {
                    for (File file : Objects.requireNonNull(classDir.listFiles())) {
                        if (file.isFile() && (file.getName().endsWith(".png") || file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg"))) {
                            allImageFiles.add(file);
                        }
                    }
                }
            }

            int totalFiles = allImageFiles.size();
            if (totalFiles == 0) {
                Platform.runLater(() -> statusLabel.setText("Status: Error! Tidak ada file gambar yang ditemukan di sub-folder."));
                return;
            }

            // 2. Proses file satu per satu dan update UI
            for (int i = 0; i < totalFiles; i++) {
                File imageFile = allImageFiles.get(i);
                String trueLabel = imageFile.getParentFile().getName();
                
                // Update status di UI thread
                final int currentFileIndex = i + 1;
                Platform.runLater(() -> statusLabel.setText(String.format("Status: Memproses %d/%d... (%s)", currentFileIndex, totalFiles, imageFile.getName())));

                // Lakukan prediksi
                PredictionResult result = modelPredictor.predictFull(imageFile);
                
                if (result.isSuccess()) {
                    String predictedLabel = result.getPredictedClass();
                    results.add(new MetricsCalculator.PredictionTuple(trueLabel, predictedLabel));
                }
                
                // Update progress bar di UI thread
                final double progress = (double) currentFileIndex / totalFiles;
                Platform.runLater(() -> progressBar.setProgress(progress));
            }
            
            // 3. Hitung metrik
            Platform.runLater(() -> statusLabel.setText("Status: Menghitung metrik..."));
            MetricsCalculator calculator = new MetricsCalculator(results, classNames);
            String formattedResults = calculator.getFormattedResults();

            // 4. Tampilkan hasil di UI thread
            Platform.runLater(() -> {
                resultTextArea.setText(formattedResults);
                statusLabel.setText(String.format("Status: Selesai! %d gambar dievaluasi.", totalFiles));
                evaluateButton.setDisable(false);
                selectFolderButton.setDisable(false);
                progressBar.setVisible(false);
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> statusLabel.setText("Status: Terjadi Error! " + e.getMessage()));
        }
    }

    public void cleanup() {
        if (modelPredictor != null) {
            modelPredictor.close();
        }
    }
}
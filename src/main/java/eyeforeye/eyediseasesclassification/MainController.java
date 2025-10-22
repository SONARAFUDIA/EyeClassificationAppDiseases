package eyeforeye.eyediseasesclassification;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.awt.image.BufferedImage;

import java.io.File;

public class MainController {

    @FXML
    private ImageView imageView;

    @FXML
    private Label predictionLabel;

    @FXML
    private Button uploadButton, predictButton, showGradCAMButton;
    
    @FXML
    private Label confidenceLabel;  // Hanya confidence
    
    @FXML
    private ImageView gradcamImageView;
    
    @FXML
    private TableView<ClassProbabilityDisplay> probabilityTable;
    
    @FXML
    private TableColumn<ClassProbabilityDisplay, String> classNameColumn;
    
    @FXML
    private TableColumn<ClassProbabilityDisplay, String> probabilityColumn;

    private File selectedFile;
    private ModelPredictor modelPredictor;
    private File lastDirectory;
    private int lastPredictedClass = -1;
    private ObservableList<ClassProbabilityDisplay> probabilityData;

    @FXML
    public void initialize() {
        predictionLabel.setText("Belum ada gambar yang diunggah.");
        
        // Setup probability table
        if (probabilityTable != null) {
            classNameColumn.setCellValueFactory(new PropertyValueFactory<>("className"));
            probabilityColumn.setCellValueFactory(new PropertyValueFactory<>("probability"));
            
            probabilityData = FXCollections.observableArrayList();
            probabilityTable.setItems(probabilityData);
        }
        
        // Set initial confidence display
        if (confidenceLabel != null) {
            confidenceLabel.setText("0.00%");
        }
        
        // Disable Grad-CAM button initially
        if (showGradCAMButton != null) {
            showGradCAMButton.setDisable(true);
        }

        try {
            // Inisialisasi model TensorFlow SavedModel
            modelPredictor = new ModelPredictor("saved_model");
        } catch (Exception e) {
            predictionLabel.setText("❌ Gagal memuat model: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void onUploadClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Pilih Gambar Mata");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File Gambar", "*.png", "*.jpg", "*.jpeg")
        );

        if (lastDirectory != null && lastDirectory.exists()) {
            fileChooser.setInitialDirectory(lastDirectory);
        }

        selectedFile = fileChooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (selectedFile != null) {
            lastDirectory = selectedFile.getParentFile();
            
            Image image = new Image(selectedFile.toURI().toString());
            imageView.setImage(image);
            predictionLabel.setText("✅ Gambar berhasil diunggah!\nKlik 'Prediksi Hasil' untuk analisis.");
            
            // Clear previous data
            if (probabilityData != null) {
                probabilityData.clear();
            }
            
            // Reset confidence
            if (confidenceLabel != null) {
                confidenceLabel.setText("0.00%");
            }
        }
    }

    @FXML
    private void onPredictClicked() {
        if (selectedFile == null) {
            predictionLabel.setText("⚠️ Silakan unggah gambar terlebih dahulu.");
            return;
        }

        if (modelPredictor == null) {
            predictionLabel.setText("❌ Model belum siap.");
            return;
        }

        // Tampilkan loading indicator
        predictionLabel.setText("🔄 Memproses prediksi...");
        if (confidenceLabel != null) {
            confidenceLabel.setText("0.00%");
        }
        
        if (probabilityData != null) {
            probabilityData.clear();
        }
        
        // Clear Grad-CAM
        if (gradcamImageView != null) {
            gradcamImageView.setImage(null);
        }
        
        // Jalankan prediksi di thread terpisah
        new Thread(() -> {
            PredictionResult result = modelPredictor.predictFull(selectedFile);
            
            // Update UI di JavaFX thread
            javafx.application.Platform.runLater(() -> {
                if (result.isSuccess()) {
                    // Update main prediction label
                    predictionLabel.setText(String.format(
                        "✅ PREDIKSI VALID\n\n" +
                        "Prediksi Utama: %s\n\n" +
                        "📊 Lihat tabel untuk probabilitas semua kelas", 
                        result.getPredictedClass()
                    ));
                    
                    // Update confidence label
                    if (confidenceLabel != null) {
                        confidenceLabel.setText(String.format("%.2f%%", result.getConfidence() * 100));
                    }
                    
                    // Update probability table with ALL classes
                    updateProbabilityTable(result);
                    
                    // Store predicted class for Grad-CAM
                    lastPredictedClass = result.getPredictedClassIndex();
                    
                    // Enable Grad-CAM button
                    if (showGradCAMButton != null) {
                        showGradCAMButton.setDisable(false);
                    }
                } else {
                    predictionLabel.setText("❌ " + result.getErrorMessage());
                    if (showGradCAMButton != null) {
                        showGradCAMButton.setDisable(true);
                    }
                }
            });
        }).start();
    }
    
    /**
     * Update tabel probabilitas dengan semua kelas
     */
    private void updateProbabilityTable(PredictionResult result) {
        if (probabilityData == null) return;
        
        probabilityData.clear();
        
        // Get all predictions sorted by probability
        PredictionResult.ClassProbability[] allPredictions = result.getAllPredictions();
        
        for (PredictionResult.ClassProbability cp : allPredictions) {
            probabilityData.add(new ClassProbabilityDisplay(
                cp.getClassName(),
                cp.getProbabilityPercent(),
                cp.getProbability()
            ));
        }
    }
    
    @FXML
    private void onShowGradCAMClicked() {
        if (selectedFile == null || lastPredictedClass < 0) {
            return;
        }
        
        predictionLabel.setText(predictionLabel.getText() + "\n\n🔄 Generating Grad-CAM visualization...");
        
        new Thread(() -> {
            try {
                BufferedImage original = javax.imageio.ImageIO.read(selectedFile);
                
                // Generate Grad-CAM
                GradCAMGenerator gradcam = new GradCAMGenerator(modelPredictor.getModel());
                BufferedImage heatmap = gradcam.generateGradCAM(original, lastPredictedClass);
                
                // Update UI
                javafx.application.Platform.runLater(() -> {
                    if (gradcamImageView != null) {
                        javafx.scene.image.Image fxImage = convertToFxImage(heatmap);
                        gradcamImageView.setImage(fxImage);
                    }
                    
                    String currentText = predictionLabel.getText();
                    if (!currentText.contains("Grad-CAM visualization generated")) {
                        predictionLabel.setText(currentText.replace(
                            "🔄 Generating Grad-CAM visualization...", 
                            "🔥 Grad-CAM visualization generated!"));
                    }
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    predictionLabel.setText(predictionLabel.getText() + 
                        "\n\n❌ Grad-CAM generation failed: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Convert BufferedImage to JavaFX Image
     */
    private javafx.scene.image.Image convertToFxImage(java.awt.image.BufferedImage bufferedImage) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "PNG", out);
            java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(out.toByteArray());
            return new javafx.scene.image.Image(in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Method untuk cleanup resources saat aplikasi ditutup
     */
    public void cleanup() {
        if (modelPredictor != null) {
            modelPredictor.close();
        }
    }
    
    /**
     * Inner class untuk display di TableView
     */
    public static class ClassProbabilityDisplay {
        private final String className;
        private final String probability;
        private final float probabilityValue;
        
        public ClassProbabilityDisplay(String className, String probability, float probabilityValue) {
            this.className = className;
            this.probability = probability;
            this.probabilityValue = probabilityValue;
        }
        
        public String getClassName() {
            return className;
        }
        
        public String getProbability() {
            return probability;
        }
        
        public float getProbabilityValue() {
            return probabilityValue;
        }
    }
}
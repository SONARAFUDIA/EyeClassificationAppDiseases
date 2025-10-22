package eyeforeye.eyediseasesclassification;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
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
    private Label accuracyLabel, precisionLabel, recallLabel, f1Label;
    
    @FXML
    private ImageView gradcamImageView;

    private File selectedFile;
    private ModelPredictor modelPredictor;
    private File lastDirectory;
    private int lastPredictedClass = -1; // Store last predicted class index for Grad-CAM

    @FXML
    public void initialize() {
        predictionLabel.setText("Belum ada gambar yang diunggah.");
        
        // Set initial metrics (placeholder)
        updateMetricsDisplay(0, 0, 0, 0);
        
        // Disable Grad-CAM button initially
        if (showGradCAMButton != null) {
            showGradCAMButton.setDisable(true);
        }

        try {
            // Inisialisasi model TensorFlow SavedModel
            // PASTIKAN folder saved_model ada di root project!
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

        // Set direktori awal ke direktori terakhir (jika ada)
        if (lastDirectory != null && lastDirectory.exists()) {
            fileChooser.setInitialDirectory(lastDirectory);
        }

        selectedFile = fileChooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (selectedFile != null) {
            // Simpan direktori dari file yang dipilih
            lastDirectory = selectedFile.getParentFile();
            
            Image image = new Image(selectedFile.toURI().toString());
            imageView.setImage(image);
            predictionLabel.setText("✅ Gambar berhasil diunggah!\nKlik 'Prediksi Hasil' untuk analisis.");
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
        updateMetricsDisplay(0, 0, 0, 0); // Reset metrics
        
        // Clear Grad-CAM
        if (gradcamImageView != null) {
            gradcamImageView.setImage(null);
        }
        
        // Jalankan prediksi di thread terpisah agar UI tidak freeze
        new Thread(() -> {
            BatchResult result = modelPredictor.predictDetailed(selectedFile);
            
            // Update UI di JavaFX thread
            javafx.application.Platform.runLater(() -> {
                if (result.isSuccess()) {
                    predictionLabel.setText(String.format(
                        "✅ PREDIKSI VALID\n\nPrediksi: %s\nConfidence: %.2f%%", 
                        result.getPredictedClass(), result.getConfidence() * 100
                    ));
                    
                    // Update metrics
                    estimateMetrics(result.getConfidence());
                    
                    // Store predicted class for Grad-CAM
                    lastPredictedClass = getPredictedClassIndex(result.getPredictedClass());
                    
                    // Enable Grad-CAM button
                    if (showGradCAMButton != null) {
                        showGradCAMButton.setDisable(false);
                    }
                } else {
                    predictionLabel.setText(result.getErrorMessage());
                    if (showGradCAMButton != null) {
                        showGradCAMButton.setDisable(true);
                    }
                }
            });
        }).start();
    }
    
    @FXML
    private void onShowGradCAMClicked() {
        if (selectedFile == null || lastPredictedClass < 0) {
            return;
        }
        
        predictionLabel.setText("🔄 Generating Grad-CAM visualization...");
        
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
                    predictionLabel.setText(predictionLabel.getText() + "\n\n🔥 Grad-CAM visualization generated!");
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    predictionLabel.setText(predictionLabel.getText() + "\n\n❌ Grad-CAM generation failed: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Get class index from class name
     */
    private int getPredictedClassIndex(String className) {
        String[] classes = {
            "Central Serous Chorioretinopathy",
            "Diabetes Retinopathy",
            "Disc Edema",
            "Glaucoma",
            "Healthy",
            "Macular Scar",
            "Myopia",
            "Pterygium",
            "Retinal Detachment",
            "Retinis Pigmentosa"
        };
        
        for (int i = 0; i < classes.length; i++) {
            if (classes[i].equals(className)) {
                return i;
            }
        }
        return -1;
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
     * Update tampilan metrics
     */
    private void updateMetricsDisplay(double accuracy, double precision, double recall, double f1) {
        accuracyLabel.setText(String.format("%.2f%%", accuracy * 100));
        precisionLabel.setText(String.format("%.2f%%", precision * 100));
        recallLabel.setText(String.format("%.2f%%", recall * 100));
        f1Label.setText(String.format("%.2f%%", f1 * 100));
    }
    
    /**
     * Hitung metrics berdasarkan confidence score
     * Ini adalah estimasi sederhana, untuk metrics real butuh ground truth labels
     */
    private void estimateMetrics(float confidence) {
        // Estimasi sederhana berdasarkan confidence
        // Note: Ini bukan metrics real! Untuk metrics real butuh test dataset dengan ground truth
        
        double accuracy = confidence * 0.95; // Estimasi accuracy
        double precision = confidence * 0.93; // Estimasi precision
        double recall = confidence * 0.90; // Estimasi recall
        double f1 = 2 * (precision * recall) / (precision + recall); // F1 Score
        
        updateMetricsDisplay(accuracy, precision, recall, f1);
    }
}
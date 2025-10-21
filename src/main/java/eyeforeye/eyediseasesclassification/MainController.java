package eyeforeye.eyediseasesclassification;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;

public class MainController {

    @FXML
    private ImageView imageView;

    @FXML
    private Label predictionLabel;

    @FXML
    private Button uploadButton, predictButton;

    private File selectedFile;
    private ModelPredictor modelPredictor;
    private File lastDirectory;

    @FXML
    public void initialize() {
        predictionLabel.setText("Belum ada gambar yang diunggah.");

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
        
        // Jalankan prediksi di thread terpisah agar UI tidak freeze
        new Thread(() -> {
            String result = modelPredictor.predict(selectedFile);
            
            // Update UI di JavaFX thread
            javafx.application.Platform.runLater(() -> {
                predictionLabel.setText(result);
            });
        }).start();
    }

    /**
     * Method untuk cleanup resources saat aplikasi ditutup
     */
    public void cleanup() {
        if (modelPredictor != null) {
            modelPredictor.close();
        }
    }
}
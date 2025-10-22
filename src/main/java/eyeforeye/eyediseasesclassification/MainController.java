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
import java.io.InputStream; // BARU
import java.net.URI; // BARU
import java.net.http.HttpClient; // BARU
import java.net.http.HttpRequest; // BARU
import java.net.http.HttpResponse; // BARU
import java.net.http.HttpRequest.BodyPublishers; // BARU
import java.net.http.HttpResponse.BodyHandlers; // BARU
import java.nio.charset.StandardCharsets; // BARU
import java.nio.file.Files; // BARU
import java.time.Duration; // BARU
import java.util.ArrayList; // BARU
import java.util.List; // BARU

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

    // Klien HTTP untuk koneksi ke backend Python (BARU)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    // Alamat backend Python (BARU)
    private final String gradCamApiUrl = "http://127.0.0.1:5000/gradcam";

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
            // Inisialisasi model TensorFlow SavedModel (masih dipakai untuk prediksi)
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
            
            // Clear Grad-CAM
            if (gradcamImageView != null) {
                gradcamImageView.setImage(null);
            }
            
            // Disable Grad-CAM button
            if (showGradCAMButton != null) {
                showGradCAMButton.setDisable(true);
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
        // ====================================================================
        // METODE INI DIUBAH TOTAL UNTUK MEMANGGIL API PYTHON
        // ====================================================================
        
        if (selectedFile == null || lastPredictedClass < 0) {
            return;
        }
        
        predictionLabel.setText(predictionLabel.getText() + "\n\n🔄 Generating Grad-CAM (via Python)...");
        showGradCAMButton.setDisable(true); // Nonaktifkan tombol selama proses

        // Jalankan di thread terpisah agar UI tidak freeze
        new Thread(() -> {
            try {
                // 1. Siapkan boundary untuk multipart request
                String boundary = "---Boundary" + System.currentTimeMillis();
                
                // 2. Buat request body
                // Ini akan berisi 2 bagian: 'class_index' (teks) dan 'image' (file)
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gradCamApiUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(buildMultipartBody(selectedFile, lastPredictedClass, boundary))
                    .timeout(Duration.ofSeconds(30)) // Timeout 30 detik
                    .build();

                // 3. Kirim request secara asynchronous dan tunggu InputStream (gambar)
                httpClient.sendAsync(request, BodyHandlers.ofInputStream())
                    .thenApply(HttpResponse::body) // Ambil body (InputStream) dari response
                    .thenAccept(imageStream -> {
                        // 4. Update UI (Harus di dalam Platform.runLater)
                        javafx.application.Platform.runLater(() -> {
                            try (imageStream) {
                                // Buat JavaFX Image langsung dari InputStream
                                Image fxImage = new Image(imageStream);
                                gradcamImageView.setImage(fxImage);
                                
                                String currentText = predictionLabel.getText();
                                predictionLabel.setText(currentText.replace(
                                    "🔄 Generating Grad-CAM (via Python)...", 
                                    "🔥 Grad-CAM (Python) generated!"));
                            } catch (Exception e) {
                                e.printStackTrace();
                                predictionLabel.setText(predictionLabel.getText() + 
                                    "\n\n❌ Gagal memuat gambar Grad-CAM: " + e.getMessage());
                            } finally {
                                showGradCAMButton.setDisable(false); // Aktifkan kembali tombol
                            }
                        });
                    })
                    .exceptionally(e -> {
                        // 5. Tangani jika terjadi error koneksi
                        e.printStackTrace();
                        javafx.application.Platform.runLater(() -> {
                            predictionLabel.setText(predictionLabel.getText() + 
                                "\n\n❌ Grad-CAM Gagal: " + e.getMessage() + 
                                "\n(Pastikan backend Python berjalan!)");
                            showGradCAMButton.setDisable(false); // Aktifkan kembali tombol
                        });
                        return null;
                    });
                
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    predictionLabel.setText(predictionLabel.getText() + 
                        "\n\n❌ Grad-CAM error: " + e.getMessage());
                    showGradCAMButton.setDisable(false); // Aktifkan kembali tombol
                });
            }
        }).start();
    }
    
    /**
     * (BARU) Helper untuk membangun body multipart/form-data
     * @param file File gambar yang akan dikirim
     * @param classIndex Index kelas yang akan dikirim
     * @param boundary String boundary yang unik
     * @return HttpRequest.BodyPublisher
     * @throws Exception
     */
    private HttpRequest.BodyPublisher buildMultipartBody(File file, int classIndex, String boundary) throws Exception {
        List<byte[]> byteArrays = new ArrayList<>();
        String crlf = "\r\n";
        String twoHyphens = "--";

        // Bagian 1: class_index (form field)
        byteArrays.add((twoHyphens + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        byteArrays.add(("Content-Disposition: form-data; name=\"class_index\"" + crlf).getBytes(StandardCharsets.UTF_8));
        byteArrays.add(crlf.getBytes(StandardCharsets.UTF_8));
        byteArrays.add(Integer.toString(classIndex).getBytes(StandardCharsets.UTF_8));
        byteArrays.add(crlf.getBytes(StandardCharsets.UTF_8));

        // Bagian 2: image (file)
        byteArrays.add((twoHyphens + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        byteArrays.add(("Content-Disposition: form-data; name=\"image\"; filename=\"" + file.getName() + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        byteArrays.add(("Content-Type: " + Files.probeContentType(file.toPath()) + crlf).getBytes(StandardCharsets.UTF_8));
        byteArrays.add(crlf.getBytes(StandardCharsets.UTF_8));
        byteArrays.add(Files.readAllBytes(file.toPath()));
        byteArrays.add(crlf.getBytes(StandardCharsets.UTF_8));

        // Akhir dari multipart
        byteArrays.add((twoHyphens + boundary + twoHyphens + crlf).getBytes(StandardCharsets.UTF_8));

        return BodyPublishers.ofByteArrays(byteArrays);
    }
    
    /**
     * Method convertToFxImage TIDAK DIPERLUKAN LAGI
     * karena kita bisa membuat Image langsung dari InputStream
     */
    // private javafx.scene.image.Image convertToFxImage(java.awt.image.BufferedImage bufferedImage) { ... }


    /**
     * Method untuk cleanup resources saat aplikasi ditutup
     */
    public void cleanup() {
        if (modelPredictor != null) {
            modelPredictor.close();
        }
        // Tidak perlu mematikan HttpClient secara eksplisit
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
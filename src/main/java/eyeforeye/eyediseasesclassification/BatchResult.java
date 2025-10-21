package eyeforeye.eyediseasesclassification;

import java.io.File;

/**
 * Model untuk menyimpan hasil prediksi batch
 */
public class BatchResult {
    private final File imageFile;
    private final String predictedClass;
    private final float confidence;
    private final String status; // SUCCESS, ERROR
    private final String errorMessage;
    
    // Constructor untuk hasil sukses
    public BatchResult(File imageFile, String predictedClass, float confidence) {
        this.imageFile = imageFile;
        this.predictedClass = predictedClass;
        this.confidence = confidence;
        this.status = "SUCCESS";
        this.errorMessage = null;
    }
    
    // Constructor untuk hasil error
    public BatchResult(File imageFile, String errorMessage) {
        this.imageFile = imageFile;
        this.predictedClass = "ERROR";
        this.confidence = 0.0f;
        this.status = "ERROR";
        this.errorMessage = errorMessage;
    }
    
    // Getters
    public File getImageFile() {
        return imageFile;
    }
    
    public String getFileName() {
        return imageFile.getName();
    }
    
    public String getPredictedClass() {
        return predictedClass;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public String getConfidencePercent() {
        return String.format("%.2f%%", confidence * 100);
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "";
    }
    
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
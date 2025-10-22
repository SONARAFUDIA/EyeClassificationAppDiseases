package eyeforeye.eyediseasesclassification;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Model untuk menyimpan hasil prediksi lengkap dengan probabilitas semua kelas
 */
public class PredictionResult {
    private final File imageFile;
    private final String[] classNames;
    private final float[] probabilities;
    private final int predictedClassIndex;
    private final String status;
    private final String errorMessage;
    
    // Constructor untuk hasil sukses
    public PredictionResult(File imageFile, String[] classNames, float[] probabilities) {
        this.imageFile = imageFile;
        this.classNames = classNames;
        this.probabilities = probabilities;
        this.predictedClassIndex = argMax(probabilities);
        this.status = "SUCCESS";
        this.errorMessage = null;
    }
    
    // Constructor untuk hasil error
    public PredictionResult(File imageFile, String errorMessage) {
        this.imageFile = imageFile;
        this.classNames = null;
        this.probabilities = null;
        this.predictedClassIndex = -1;
        this.status = "ERROR";
        this.errorMessage = errorMessage;
    }
    
    private int argMax(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
    
    // Getters
    public File getImageFile() {
        return imageFile;
    }
    
    public String getFileName() {
        return imageFile != null ? imageFile.getName() : "Unknown";
    }
    
    public String[] getClassNames() {
        return classNames;
    }
    
    public float[] getProbabilities() {
        return probabilities;
    }
    
    public int getPredictedClassIndex() {
        return predictedClassIndex;
    }
    
    public String getPredictedClass() {
        if (classNames != null && predictedClassIndex >= 0) {
            return classNames[predictedClassIndex];
        }
        return "ERROR";
    }
    
    public float getConfidence() {
        if (probabilities != null && predictedClassIndex >= 0) {
            return probabilities[predictedClassIndex];
        }
        return 0.0f;
    }
    
    public String getConfidencePercent() {
        return String.format("%.2f%%", getConfidence() * 100);
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
    
    /**
     * Get top N predictions sorted by confidence
     */
    public ClassProbability[] getTopNPredictions(int n) {
        if (classNames == null || probabilities == null) {
            return new ClassProbability[0];
        }
        
        ClassProbability[] results = new ClassProbability[classNames.length];
        for (int i = 0; i < classNames.length; i++) {
            results[i] = new ClassProbability(classNames[i], probabilities[i], i);
        }
        
        // Sort by probability descending
        Arrays.sort(results, Comparator.comparing(ClassProbability::getProbability).reversed());
        
        // Return top N
        int resultSize = Math.min(n, results.length);
        return Arrays.copyOf(results, resultSize);
    }
    
    /**
     * Get all predictions sorted by confidence
     */
    public ClassProbability[] getAllPredictions() {
        return getTopNPredictions(classNames != null ? classNames.length : 0);
    }
    
    /**
     * Inner class untuk menyimpan class dan probabilitasnya
     */
    public static class ClassProbability {
        private final String className;
        private final float probability;
        private final int classIndex;
        
        public ClassProbability(String className, float probability, int classIndex) {
            this.className = className;
            this.probability = probability;
            this.classIndex = classIndex;
        }
        
        public String getClassName() {
            return className;
        }
        
        public float getProbability() {
            return probability;
        }
        
        public String getProbabilityPercent() {
            return String.format("%.2f%%", probability * 100);
        }
        
        public int getClassIndex() {
            return classIndex;
        }
    }
}
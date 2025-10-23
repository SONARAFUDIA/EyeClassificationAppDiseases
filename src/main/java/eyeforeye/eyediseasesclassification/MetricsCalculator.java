package eyeforeye.eyediseasesclassification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kelas ini menghitung metrik kuantitatif dari daftar hasil prediksi.
 * Ini membutuhkan daftar "PredictionTuple" yang berisi label aktual dan prediksi.
 */
public class MetricsCalculator {

    // Helper class kecil untuk menyimpan pasangan hasil
    public static class PredictionTuple {
        private final String actualLabel;
        private final String predictedLabel;

        public PredictionTuple(String actualLabel, String predictedLabel) {
            this.actualLabel = actualLabel;
            this.predictedLabel = predictedLabel;
        }
        public String getActualLabel() { return actualLabel; }
        public String getPredictedLabel() { return predictedLabel; }
    }

    private final List<PredictionTuple> results;
    private final String[] classNames;
    private final Map<String, Map<String, Integer>> confusionMatrix;
    private final int totalSamples;

    public MetricsCalculator(List<PredictionTuple> results, String[] classNames) {
        this.results = results;
        this.classNames = classNames;
        this.totalSamples = results.size();
        this.confusionMatrix = buildConfusionMatrix();
    }

    // 1. Membangun Confusion Matrix
    private Map<String, Map<String, Integer>> buildConfusionMatrix() {
        Map<String, Map<String, Integer>> matrix = new HashMap<>();

        // Inisialisasi matrix dengan 0
        for (String actualClass : classNames) {
            Map<String, Integer> row = new HashMap<>();
            for (String predictedClass : classNames) {
                row.put(predictedClass, 0);
            }
            matrix.put(actualClass, row);
        }

        // Isi matrix dengan hasil
        for (PredictionTuple result : results) {
            String actual = result.getActualLabel();
            String predicted = result.getPredictedLabel();
            
            // Tambahkan 1 ke sel yang sesuai
            int currentValue = matrix.get(actual).get(predicted);
            matrix.get(actual).put(predicted, currentValue + 1);
        }
        return matrix;
    }

    // 2. Menghitung Akurasi Keseluruhan
    public double getOverallAccuracy() {
        if (totalSamples == 0) return 0.0;
        
        int totalCorrect = 0;
        for (String className : classNames) {
            totalCorrect += confusionMatrix.get(className).get(className);
        }
        return (double) totalCorrect / totalSamples;
    }

    // 3. Menghitung True Positives, False Positives, False Negatives per kelas
    private Map<String, Integer> getMetrics(String className) {
        int tp = confusionMatrix.get(className).get(className);
        
        int fp = 0;
        for (String actualClass : classNames) {
            if (!actualClass.equals(className)) {
                fp += confusionMatrix.get(actualClass).get(className); // Menjumlahkan kolom
            }
        }
        
        int fn = 0;
        for (String predictedClass : classNames) {
            if (!predictedClass.equals(className)) {
                fn += confusionMatrix.get(className).get(predictedClass); // Menjumlahkan baris
            }
        }
        
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("TP", tp);
        metrics.put("FP", fp);
        metrics.put("FN", fn);
        return metrics;
    }
    
    // 4. Menghitung Presisi per kelas
    public double getPrecision(String className) {
        Map<String, Integer> m = getMetrics(className);
        int tp = m.get("TP");
        int fp = m.get("FP");
        if (tp + fp == 0) return 0.0;
        return (double) tp / (tp + fp);
    }
    
    // 5. Menghitung Recall per kelas
    public double getRecall(String className) {
        Map<String, Integer> m = getMetrics(className);
        int tp = m.get("TP");
        int fn = m.get("FN");
        if (tp + fn == 0) return 0.0;
        return (double) tp / (tp + fn);
    }
    
    // 6. Menghitung F1-Score per kelas
    public double getF1Score(String className) {
        double precision = getPrecision(className);
        double recall = getRecall(className);
        if (precision + recall == 0) return 0.0;
        return 2 * (precision * recall) / (precision + recall);
    }
    
    // 7. Format hasil sebagai String
    public String getFormattedResults() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("--- HASIL EVALUASI KESELURUHAN ---\n");
        sb.append(String.format("Total Gambar: %d\n", totalSamples));
        sb.append(String.format("Akurasi: %.2f%%\n\n", getOverallAccuracy() * 100));
        
        sb.append("--- METRIK PER KELAS ---\n");
        for (String className : classNames) {
            sb.append(String.format("[%s]\n", className));
            sb.append(String.format("  Presisi: %.2f%%\n", getPrecision(className) * 100));
            sb.append(String.format("  Recall   : %.2f%%\n", getRecall(className) * 100));
            sb.append(String.format("  F1-Score : %.2f%%\n\n", getF1Score(className) * 100));
        }
        
        sb.append("--- CONFUSION MATRIX ---\n");
        // Header
        sb.append("Aktual \\ Prediksi |");
        for (String c : classNames) sb.append(String.format(" %-10.10s |", c));
        sb.append("\n");
        
        // Rows
        for (String actualClass : classNames) {
            sb.append(String.format("%-17.17s |", actualClass));
            for (String predictedClass : classNames) {
                sb.append(String.format(" %-10d |", confusionMatrix.get(actualClass).get(predictedClass)));
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
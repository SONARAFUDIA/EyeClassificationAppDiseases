package eyeforeye.eyediseasesclassification;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Out-of-Distribution (OOD) Detector
 * Mendeteksi apakah gambar adalah gambar mata fundus yang valid atau bukan
 */
public class OODDetector {
    
    // Thresholds untuk berbagai checks
    private static final double ENTROPY_THRESHOLD = 2.0;  // Minimum entropy untuk gambar mata
    private static final double COLOR_VARIANCE_MIN = 0.02; // Minimum color variance
    private static final double CIRCULAR_SCORE_MIN = 0.3;  // Minimum circular pattern score
    private static final double RED_CHANNEL_MIN = 0.3;    // Minimum red channel dominance
    
    /**
     * Check apakah gambar adalah valid fundus image
     * @param imageFile File gambar yang akan dicek
     * @return OODResult dengan status dan score
     */
    public static OODResult detectOOD(File imageFile) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return new OODResult(false, 0.0, "Failed to read image");
            }
            
            // Resize untuk processing yang lebih cepat
            BufferedImage resized = resizeImage(img, 224, 224);
            
            // Multiple checks untuk OOD detection
            double entropyScore = calculateEntropy(resized);
            double colorVariance = calculateColorVariance(resized);
            double circularScore = detectCircularPattern(resized);
            double redDominance = calculateRedChannelDominance(resized);
            
            // Weighted scoring system
            double oodScore = 0.0;
            StringBuilder reason = new StringBuilder();
            
            // Check 1: Entropy (complexity of image)
            if (entropyScore < ENTROPY_THRESHOLD) {
                reason.append("- Gambar terlalu sederhana/uniform\n");
            } else {
                oodScore += 0.25;
            }
            
            // Check 2: Color variance
            if (colorVariance < COLOR_VARIANCE_MIN) {
                reason.append("- Variasi warna tidak sesuai dengan mata fundus\n");
            } else {
                oodScore += 0.25;
            }
            
            // Check 3: Circular pattern (mata fundus biasanya circular)
            if (circularScore < CIRCULAR_SCORE_MIN) {
                reason.append("- Tidak ditemukan pola circular (karakteristik mata)\n");
            } else {
                oodScore += 0.25;
            }
            
            // Check 4: Red channel dominance (fundus images have strong red)
            if (redDominance < RED_CHANNEL_MIN) {
                reason.append("- Dominasi channel merah tidak sesuai fundus image\n");
            } else {
                oodScore += 0.25;
            }
            
            // Decision: jika score >= 0.5 (2 dari 4 checks passed), dianggap valid
            boolean isValid = oodScore >= 0.5;
            
            if (!isValid) {
                return new OODResult(false, oodScore, 
                    "Gambar TIDAK VALID sebagai fundus image:\n" + reason.toString());
            }
            
            return new OODResult(true, oodScore, "Valid fundus image");
            
        } catch (Exception e) {
            e.printStackTrace();
            return new OODResult(false, 0.0, "Error during OOD detection: " + e.getMessage());
        }
    }
    
    /**
     * Calculate entropy (measure of complexity/randomness)
     */
    private static double calculateEntropy(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        // Calculate histogram
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;
                histogram[gray]++;
            }
        }
        
        // Calculate entropy
        int totalPixels = width * height;
        double entropy = 0.0;
        for (int count : histogram) {
            if (count > 0) {
                double probability = (double) count / totalPixels;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }
        
        return entropy;
    }
    
    /**
     * Calculate color variance across image
     */
    private static double calculateColorVariance(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        double sumR = 0, sumG = 0, sumB = 0;
        int totalPixels = width * height;
        
        // Calculate mean
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >> 8) & 0xFF;
                sumB += rgb & 0xFF;
            }
        }
        
        double meanR = sumR / totalPixels;
        double meanG = sumG / totalPixels;
        double meanB = sumB / totalPixels;
        
        // Calculate variance
        double varR = 0, varG = 0, varB = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                varR += Math.pow(r - meanR, 2);
                varG += Math.pow(g - meanG, 2);
                varB += Math.pow(b - meanB, 2);
            }
        }
        
        varR /= totalPixels;
        varG /= totalPixels;
        varB /= totalPixels;
        
        // Average variance across channels, normalized
        return (varR + varG + varB) / (3 * 255 * 255);
    }
    
    /**
     * Detect circular pattern (fundus images have circular retina)
     */
    private static double detectCircularPattern(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Check brightness distribution from center to edges
        int numRings = 5;
        double[] ringBrightness = new double[numRings];
        int[] ringCounts = new int[numRings];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
                double maxDistance = Math.sqrt(Math.pow(width/2, 2) + Math.pow(height/2, 2));
                int ringIndex = Math.min((int)(distance / maxDistance * numRings), numRings - 1);
                
                int rgb = img.getRGB(x, y);
                int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;
                
                ringBrightness[ringIndex] += gray;
                ringCounts[ringIndex]++;
            }
        }
        
        // Average brightness per ring
        for (int i = 0; i < numRings; i++) {
            if (ringCounts[i] > 0) {
                ringBrightness[i] /= ringCounts[i];
            }
        }
        
        // Check if brightness decreases towards edges (typical for fundus)
        double circularScore = 0.0;
        for (int i = 0; i < numRings - 1; i++) {
            if (ringBrightness[i] > ringBrightness[i + 1]) {
                circularScore += 1.0 / (numRings - 1);
            }
        }
        
        return circularScore;
    }
    
    /**
     * Calculate red channel dominance (fundus images are typically red-dominant)
     */
    private static double calculateRedChannelDominance(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        long sumR = 0, sumG = 0, sumB = 0;
        int totalPixels = width * height;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                sumR += (rgb >> 16) & 0xFF;
                sumG += (rgb >> 8) & 0xFF;
                sumB += rgb & 0xFF;
            }
        }
        
        double avgR = (double) sumR / totalPixels;
        double avgG = (double) sumG / totalPixels;
        double avgB = (double) sumB / totalPixels;
        
        double totalAvg = avgR + avgG + avgB;
        if (totalAvg == 0) return 0.0;
        
        // Red channel dominance ratio
        return avgR / totalAvg;
    }
    
    /**
     * Resize image helper
     */
    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(original, 0, 0, width, height, null);
        return resized;
    }
    
    /**
     * Inner class untuk hasil OOD detection
     */
    public static class OODResult {
        private final boolean isValid;
        private final double score;
        private final String message;
        
        public OODResult(boolean isValid, double score, String message) {
            this.isValid = isValid;
            this.score = score;
            this.message = message;
        }
        
        public boolean isValid() {
            return isValid;
        }
        
        public double getScore() {
            return score;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getScorePercent() {
            return String.format("%.1f%%", score * 100);
        }
    }
}
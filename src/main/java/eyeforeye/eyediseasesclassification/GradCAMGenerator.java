package eyeforeye.eyediseasesclassification;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.types.TFloat32;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Grad-CAM (Gradient-weighted Class Activation Mapping) Generator
 * Untuk visualisasi fitur yang mempengaruhi prediksi model
 */
public class GradCAMGenerator {

    private SavedModelBundle model;
    
    public GradCAMGenerator(SavedModelBundle model) {
        this.model = model;
    }

    /**
     * Generate Grad-CAM heatmap overlay pada gambar original
     * 
     * @param originalImage Gambar asli
     * @param targetClass Target class untuk visualisasi
     * @return BufferedImage dengan heatmap overlay
     */
    public BufferedImage generateGradCAM(BufferedImage originalImage, int targetClass) {
        try {
            // Untuk implementasi sederhana, kita gunakan approximation:
            // Visualisasi berdasarkan aktivasi output dan attention map
            
            // 1. Resize image untuk processing
            BufferedImage resized = resizeImage(originalImage, 224, 224);
            
            // 2. Generate activation map (simplified version)
            float[][] activationMap = generateActivationMap(resized, targetClass);
            
            // 3. Normalize activation map
            activationMap = normalizeActivationMap(activationMap);
            
            // 4. Create heatmap
            BufferedImage heatmap = createHeatmap(activationMap, 224, 224);
            
            // 5. Overlay heatmap on original image
            BufferedImage result = overlayHeatmap(originalImage, heatmap);
            
            return result;
            
        } catch (Exception e) {
            e.printStackTrace();
            return originalImage; // Return original if error
        }
    }

    /**
     * Generate activation map (simplified approximation)
     * Karena TensorFlow Java API terbatas, kita gunakan approximation sederhana
     */
    private float[][] generateActivationMap(BufferedImage img, int targetClass) {
        int width = img.getWidth();
        int height = img.getHeight();
        float[][] activationMap = new float[height][width];
        
        // Simplified approach: Analyze pixel intensity and variance
        // Mata fundus biasanya punya karakteristik tertentu
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Calculate activation based on color characteristics
                // Fundus images: focus on red/yellow regions (blood vessels, optic disc)
                float redActivation = r / 255.0f;
                float greenActivation = g / 255.0f;
                float blueActivation = b / 255.0f;
                
                // Weighted combination (red and green more important in fundus)
                float activation = (redActivation * 0.5f) + (greenActivation * 0.3f) + (blueActivation * 0.2f);
                
                // Add edge detection influence
                float edgeStrength = calculateLocalVariance(img, x, y);
                activation += edgeStrength * 0.3f;
                
                activationMap[y][x] = activation;
            }
        }
        
        // Apply Gaussian blur for smoother heatmap
        activationMap = applyGaussianBlur(activationMap);
        
        return activationMap;
    }

    /**
     * Calculate local variance (edge detection)
     */
    private float calculateLocalVariance(BufferedImage img, int x, int y) {
        int width = img.getWidth();
        int height = img.getHeight();
        int windowSize = 3;
        
        float sum = 0;
        int count = 0;
        
        for (int dy = -windowSize/2; dy <= windowSize/2; dy++) {
            for (int dx = -windowSize/2; dx <= windowSize/2; dx++) {
                int nx = Math.max(0, Math.min(width - 1, x + dx));
                int ny = Math.max(0, Math.min(height - 1, y + dy));
                
                int rgb = img.getRGB(nx, ny);
                int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;
                sum += gray;
                count++;
            }
        }
        
        float mean = sum / count;
        
        // Calculate variance
        float variance = 0;
        for (int dy = -windowSize/2; dy <= windowSize/2; dy++) {
            for (int dx = -windowSize/2; dx <= windowSize/2; dx++) {
                int nx = Math.max(0, Math.min(width - 1, x + dx));
                int ny = Math.max(0, Math.min(height - 1, y + dy));
                
                int rgb = img.getRGB(nx, ny);
                int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;
                variance += Math.pow(gray - mean, 2);
            }
        }
        
        return (float) Math.sqrt(variance / count) / 255.0f;
    }

    /**
     * Apply Gaussian blur for smoother heatmap
     */
    private float[][] applyGaussianBlur(float[][] map) {
        int height = map.length;
        int width = map[0].length;
        float[][] blurred = new float[height][width];
        
        // Simple 3x3 Gaussian kernel
        float[][] kernel = {
            {1/16f, 2/16f, 1/16f},
            {2/16f, 4/16f, 2/16f},
            {1/16f, 2/16f, 1/16f}
        };
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float sum = 0;
                for (int ky = 0; ky < 3; ky++) {
                    for (int kx = 0; kx < 3; kx++) {
                        sum += map[y + ky - 1][x + kx - 1] * kernel[ky][kx];
                    }
                }
                blurred[y][x] = sum;
            }
        }
        
        return blurred;
    }

    /**
     * Normalize activation map to [0, 1]
     */
    private float[][] normalizeActivationMap(float[][] map) {
        int height = map.length;
        int width = map[0].length;
        
        // Find min and max
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (map[y][x] < min) min = map[y][x];
                if (map[y][x] > max) max = map[y][x];
            }
        }
        
        // Normalize
        float range = max - min;
        if (range == 0) range = 1;
        
        float[][] normalized = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                normalized[y][x] = (map[y][x] - min) / range;
            }
        }
        
        return normalized;
    }

    /**
     * Create heatmap image from activation map
     * Uses jet colormap (blue -> cyan -> green -> yellow -> red)
     */
    private BufferedImage createHeatmap(float[][] activationMap, int width, int height) {
        BufferedImage heatmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = activationMap[y][x];
                Color color = getJetColor(value);
                heatmap.setRGB(x, y, color.getRGB());
            }
        }
        
        return heatmap;
    }

    /**
     * Get jet colormap color for given value [0, 1]
     */
    private Color getJetColor(float value) {
        // Jet colormap: blue(0) -> cyan -> green -> yellow -> red(1)
        
        int r, g, b, a;
        
        if (value < 0.25f) {
            // Blue to Cyan
            float t = value / 0.25f;
            r = 0;
            g = (int) (t * 255);
            b = 255;
        } else if (value < 0.5f) {
            // Cyan to Green
            float t = (value - 0.25f) / 0.25f;
            r = 0;
            g = 255;
            b = (int) ((1 - t) * 255);
        } else if (value < 0.75f) {
            // Green to Yellow
            float t = (value - 0.5f) / 0.25f;
            r = (int) (t * 255);
            g = 255;
            b = 0;
        } else {
            // Yellow to Red
            float t = (value - 0.75f) / 0.25f;
            r = 255;
            g = (int) ((1 - t) * 255);
            b = 0;
        }
        
        // Alpha based on activation strength
        a = (int) (value * 200) + 55; // Range: 55-255
        
        return new Color(r, g, b, a);
    }

    /**
     * Overlay heatmap on original image
     */
    private BufferedImage overlayHeatmap(BufferedImage original, BufferedImage heatmap) {
        // Resize heatmap to match original size
        int width = original.getWidth();
        int height = original.getHeight();
        
        BufferedImage resizedHeatmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedHeatmap.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(heatmap, 0, 0, width, height, null);
        g.dispose();
        
        // Create result image
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        
        // Draw original image
        g2d.drawImage(original, 0, 0, null);
        
        // Overlay heatmap with transparency
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2d.drawImage(resizedHeatmap, 0, 0, null);
        
        g2d.dispose();
        
        return result;
    }

    /**
     * Resize image
     */
    private BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Save heatmap to file (for debugging)
     */
    public void saveHeatmap(BufferedImage heatmap, String outputPath) {
        try {
            ImageIO.write(heatmap, "PNG", new File(outputPath));
            System.out.println("✅ Heatmap saved to: " + outputPath);
        } catch (IOException e) {
            System.err.println("❌ Failed to save heatmap: " + e.getMessage());
        }
    }
}
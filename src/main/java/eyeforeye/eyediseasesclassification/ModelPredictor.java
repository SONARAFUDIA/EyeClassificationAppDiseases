package eyeforeye.eyediseasesclassification;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.ndarray.buffer.FloatDataBuffer;
import org.tensorflow.types.TFloat32;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class ModelPredictor {

    private SavedModelBundle model;
    private static final String DEFAULT_SERVING_SIGNATURE_DEF_KEY = "serving_default";

    private static final String[] CLASSES = {
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

    // Threshold untuk validasi
    private static final float CONFIDENCE_THRESHOLD = 0.50f; // 50%
    private static final float LOW_CONFIDENCE_THRESHOLD = 0.30f; // 30%
    
    

    public ModelPredictor(String modelPath) {
        try {
            System.out.println("🔹 Memuat model TensorFlow dari: " + modelPath);
            model = SavedModelBundle.load(modelPath, "serve");
            System.out.println("✅ Model berhasil dimuat!");
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Gagal memuat model: " + e.getMessage());
        }
    }

    public String predict(File imageFile) {
        if (model == null) {
            return "❌ Model belum dimuat!";
        }

        TFloat32 inputTensor = null;
        
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return "❌ Gagal membaca gambar!";
            }
            
            // Validasi gambar
            String validationError = validateImage(img);
            if (validationError != null) {
                return validationError;
            }
            
            BufferedImage resized = resizeImage(img, 224, 224);
            inputTensor = imageToTensorRGB(resized);

            Tensor result = model.session()
                    .runner()
                    .feed("serve_keras_tensor", inputTensor)
                    .fetch("StatefulPartitionedCall")
                    .run()
                    .get(0);

            float[][] output = new float[1][CLASSES.length];
            ((TFloat32) result).read(DataBuffers.of(output[0]));

            int predictedIndex = argMax(output[0]);
            float confidence = output[0][predictedIndex];
            String predictedClass = CLASSES[predictedIndex];
            
            result.close();

            // Validasi confidence
            if (confidence < LOW_CONFIDENCE_THRESHOLD) {
                return "❌ GAMBAR TIDAK VALID\n\n" +
                       "Gambar ini kemungkinan besar BUKAN gambar mata fundus.\n" +
                       "Confidence sangat rendah: " + String.format("%.2f%%", confidence * 100) + "\n\n" +
                       "Silakan upload gambar mata fundus yang benar.";
            } else if (confidence < CONFIDENCE_THRESHOLD) {
                return "⚠️ PREDIKSI TIDAK DAPAT DIANDALKAN\n\n" +
                       "Prediksi: " + predictedClass + "\n" +
                       "Confidence: " + String.format("%.2f%%", confidence * 100) + "\n\n" +
                       "⚠️ Confidence terlalu rendah!\n" +
                       "Kemungkinan:\n" +
                       "- Gambar bukan mata fundus yang valid\n" +
                       "- Kualitas gambar buruk\n" +
                       "- Pencahayaan tidak sesuai\n\n" +
                       "Silakan gunakan gambar mata fundus yang lebih jelas.";
            }

            return String.format("✅ PREDIKSI VALID\n\nPrediksi: %s\nConfidence: %.2f%%", 
                               predictedClass, confidence * 100);

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error saat prediksi: " + e.getMessage();
        } finally {
            if (inputTensor != null) {
                inputTensor.close();
            }
        }
    }

    public BatchResult predictDetailed(File imageFile) {
        if (model == null) {
            return new BatchResult(imageFile, "Model belum dimuat");
        }

        TFloat32 inputTensor = null;
        
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return new BatchResult(imageFile, "Gagal membaca gambar");
            }
            
            // Validasi gambar
            String validationError = validateImage(img);
            if (validationError != null) {
                return new BatchResult(imageFile, validationError);
            }
            
            BufferedImage resized = resizeImage(img, 224, 224);
            inputTensor = imageToTensorRGB(resized);

            Tensor result = model.session()
                    .runner()
                    .feed("serve_keras_tensor", inputTensor)
                    .fetch("StatefulPartitionedCall")
                    .run()
                    .get(0);

            float[][] output = new float[1][CLASSES.length];
            ((TFloat32) result).read(DataBuffers.of(output[0]));

            int predictedIndex = argMax(output[0]);
            float confidence = output[0][predictedIndex];
            String predictedClass = CLASSES[predictedIndex];
            
            result.close();

            // Validasi confidence
            if (confidence < LOW_CONFIDENCE_THRESHOLD) {
                return new BatchResult(imageFile, "TIDAK VALID (Confidence: " + 
                                     String.format("%.2f%%", confidence * 100) + " - Bukan gambar mata)");
            } else if (confidence < CONFIDENCE_THRESHOLD) {
                return new BatchResult(imageFile, predictedClass + " (⚠️ Low Confidence: " + 
                                     String.format("%.2f%%)", confidence * 100));
            }

            return new BatchResult(imageFile, predictedClass, confidence);

        } catch (Exception e) {
            e.printStackTrace();
            return new BatchResult(imageFile, "Error: " + e.getMessage());
        } finally {
            if (inputTensor != null) {
                inputTensor.close();
            }
        }
    }
    
    /**
     * Validasi awal gambar (misal: dimensi,
     * apakah gambar terlalu gelap/terang, dll.)
     *
     * @param img Gambar yang akan divalidasi
     * @return String error jika tidak valid, atau null jika valid
     */
    private String validateImage(BufferedImage img) {
        // Implementasi validasi gambar dasar
        // Model ini mengharapkan gambar mata, yang biasanya tidak super kecil.
        if (img.getWidth() < 50 || img.getHeight() < 50) {
            return "❌ GAMBAR TIDAK VALID\n\nResolusi gambar terlalu kecil (< 50x50 pixels).";
        }
        
        // TODO: Validasi yang lebih canggih bisa ditambahkan di sini
        // (misal: cek histogram untuk gambar yang terlalu gelap/terang)
        
        // Lolos validasi dasar
        return null;
    }

    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(original, 0, 0, width, height, null);
        return resized;
    }

    private static TFloat32 imageToTensorRGB(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        float[] data = new float[height * width * 3];
        
        int index = 0;
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                int pixel = img.getRGB(w, h);
                
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                
                data[index++] = r / 255.0f;
                data[index++] = g / 255.0f;
                data[index++] = b / 255.0f;
            }
        }

        FloatDataBuffer buffer = DataBuffers.of(data);
        return TFloat32.tensorOf(Shape.of(1, height, width, 3), buffer);
    }

    private static int argMax(float[] array) {
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
    
    public SavedModelBundle getModel() {
        return model;
    }

    public void close() {
        try {
            if (model != null) {
                model.close();
            }
            System.out.println("🔹 Model resources cleaned up");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
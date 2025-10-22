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

    /**
     * Prediksi dengan hasil lengkap (semua probabilitas kelas)
     */
    public PredictionResult predictFull(File imageFile) {
        if (model == null) {
            return new PredictionResult(imageFile, "Model belum dimuat");
        }

        TFloat32 inputTensor = null;
        
        try {
            // ==========================================
            // STEP 1: OOD Detection (Out-of-Distribution)
            // BLOK INI TELAH DIHAPUS
            // ==========================================
            
            // ==========================================
            // STEP 2: Model Prediction
            // ==========================================
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return new PredictionResult(imageFile, "Gagal membaca gambar");
            }
            
            // Basic validation
            if (img.getWidth() < 50 || img.getHeight() < 50) {
                return new PredictionResult(imageFile, 
                    "Resolusi gambar terlalu kecil (< 50x50 pixels)");
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

            // Copy probabilities
            float[] probabilities = new float[CLASSES.length];
            System.arraycopy(output[0], 0, probabilities, 0, CLASSES.length);
            
            result.close();

            // ==========================================
            // STEP 3: Return result
            // ==========================================
            return new PredictionResult(imageFile, CLASSES, probabilities);

        } catch (Exception e) {
            e.printStackTrace();
            return new PredictionResult(imageFile, "Error: " + e.getMessage());
        } finally {
            if (inputTensor != null) {
                inputTensor.close();
            }
        }
    }

    public String predict(File imageFile) {
        PredictionResult result = predictFull(imageFile);
        
        if (!result.isSuccess()) {
            return "❌ " + result.getErrorMessage();
        }
        
        float confidence = result.getConfidence();
        String predictedClass = result.getPredictedClass();
        
        // Build detailed prediction string with top 3 predictions
        StringBuilder sb = new StringBuilder();
        sb.append("✅ PREDIKSI VALID\n\n");
        sb.append(String.format("Prediksi Utama: %s\nConfidence: %.2f%%\n\n", 
                              predictedClass, confidence * 100));
        
        sb.append("Top 3 Prediksi:\n");
        PredictionResult.ClassProbability[] topPredictions = result.getTopNPredictions(3);
        for (int i = 0; i < topPredictions.length; i++) {
            sb.append(String.format("%d. %s: %.2f%%\n", 
                i + 1, 
                topPredictions[i].getClassName(), 
                topPredictions[i].getProbability() * 100));
        }

        return sb.toString();
    }

    public BatchResult predictDetailed(File imageFile) {
        PredictionResult result = predictFull(imageFile);
        
        if (!result.isSuccess()) {
            return new BatchResult(imageFile, result.getErrorMessage());
        }
        
        float confidence = result.getConfidence();
        String predictedClass = result.getPredictedClass();

        return new BatchResult(imageFile, predictedClass, confidence);
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
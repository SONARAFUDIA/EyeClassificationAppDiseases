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

    
    //SINGLE
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
            
            BufferedImage resized = resizeImage(img, 224, 224);
            inputTensor = imageToTensorRGB(resized);
            //SINGLE
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

            return String.format("Prediksi: %s\nConfidence: %.2f%%", 
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
    
    //BATCH
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
            
            BufferedImage resized = resizeImage(img, 224, 224);
            inputTensor = imageToTensorRGB(resized);
            //BATCH
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

    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(original, 0, 0, width, height, null);
        return resized;
    }

    private static TFloat32 imageToTensorRGB(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
//        AADDING COMMAND TEST FOR GIT BRACHNDAHUWDH UPFDATE FORF USCK SAKE
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
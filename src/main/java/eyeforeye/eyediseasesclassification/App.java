package eyeforeye.eyediseasesclassification;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

/**
 * JavaFX App - Aplikasi Klasifikasi Penyakit Mata
 * Non-modular version (tanpa module-info.java)
 */
public class App extends Application {

    private MainController singleController;
    private BatchController batchController;

    @Override
    public void start(Stage stage) throws Exception {
        // Create TabPane
        TabPane tabPane = new TabPane();
        
        // Tab 1: Single Prediction
        FXMLLoader singleLoader = new FXMLLoader(getClass().getResource("main-view.fxml"));
        Tab singleTab = new Tab("Single Image", singleLoader.load());
        singleTab.setClosable(false);
        singleController = singleLoader.getController();
        
        // Tab 2: Batch Prediction
        FXMLLoader batchLoader = new FXMLLoader(getClass().getResource("batch-view.fxml"));
        Tab batchTab = new Tab("Batch Prediction", batchLoader.load());
        batchTab.setClosable(false);
        batchController = batchLoader.getController();
        
        tabPane.getTabs().addAll(singleTab, batchTab);
        
        Scene scene = new Scene(tabPane, 900, 600);
        
        stage.setTitle("Aplikasi Klasifikasi Penyakit Mata");
        stage.setScene(scene);
        
        // Event handler untuk cleanup saat window ditutup
        stage.setOnCloseRequest(event -> {
            cleanup();
        });
        
        stage.show();
    }

    @Override
    public void stop() {
        cleanup();
    }
    
    private void cleanup() {
        if (singleController != null) {
            singleController.cleanup();
        }
        if (batchController != null) {
            batchController.cleanup();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
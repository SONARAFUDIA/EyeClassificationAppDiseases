package eyeforeye.eyediseasesclassification;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class App extends Application {

    private MainController singleController;
    private BatchController batchController;
    private EvaluationController evalController; // <-- TAMBAHKAN INI

    @Override
    public void start(Stage stage) throws Exception {
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
        
        // Tab 3: Model Evaluation (BARU)
        FXMLLoader evalLoader = new FXMLLoader(getClass().getResource("evaluation-view.fxml"));
        Tab evalTab = new Tab("Model Evaluation", evalLoader.load());
        evalTab.setClosable(false);
        evalController = evalLoader.getController();
        
        // Tambahkan semua tab
        tabPane.getTabs().addAll(singleTab, batchTab, evalTab); // <-- TAMBAHKAN evalTab
        
        Scene scene = new Scene(tabPane, 900, 750);
        
        stage.setTitle("Aplikasi Klasifikasi Penyakit Mata");
        stage.setScene(scene);
        
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
        if (evalController != null) { // <-- TAMBAHKAN INI
            evalController.cleanup();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
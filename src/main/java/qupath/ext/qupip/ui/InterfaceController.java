package qupath.ext.qupip.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Spinner;
import javafx.scene.layout.VBox;
import qupath.ext.qupip.qupipExtension;
import qupath.fx.dialogs.Dialogs;

import java.io.IOException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Controller for UI pane contained in fxml files
 */

public class InterfaceController extends VBox {
    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.qupip.ui.strings");


    public static InterfaceController createInstance(String fxmlFile) throws IOException {
        return new InterfaceController(fxmlFile);
    }

    private InterfaceController(String fxmlFile) throws IOException {
        var url = InterfaceController.class.getResource(fxmlFile);
        FXMLLoader loader = new FXMLLoader(url);
        loader.setRoot(this);
        loader.setController(this);
        loader.load();

        action();
    }

    private void action() {

    }

    @FXML
    private void runFXML() {
    }

    @FXML
    private Spinner<Integer> threadSpinner;
}
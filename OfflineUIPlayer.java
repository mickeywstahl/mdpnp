package org.mdpnp.apps.testapp.pleth.validity;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * An offline playback tool for visualizing data from a text file on the Pleth Validity UI.
 * This class is self-contained and can be run independently.
 *
 * How to use:
 * 1. Place this file in the same package as PlethValidityApp.
 * 2. Create a data file named "plethapp.txt" in the root directory of the project.
 *    The file should be a 4-column, comma-separated text file with the format:
 *    time_in_seconds,pleth_value,spo2_value,validation_score
 * 3. Run this class as a Java application.
 */
public class OfflineUIPlayer extends Application {

    private static final String DATA_FILE = "plethapp.txt";
    private static final double PLAYBACK_SPEED = 10.0; // 1.0 = real-time, 10.0 = 10x speed

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Load the FXML file to get the UI layout
        // We find the FXML file relative to this class's location.
        URL fxmlUrl = getClass().getResource("/org/mdpnp/apps/testapp/pleth/validity/PlethValidity.fxml");
        if (fxmlUrl == null) {
            System.err.println("Cannot find FXML file. Make sure it's in the correct resources path.");
            return;
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        // 2. Find the charts in the UI using their fx:id
        LineChart<Number, Number> plethChart = (LineChart<Number, Number>) root.lookup("#plethChart");
        LineChart<Number, Number> spo2Chart = (LineChart<Number, Number>) root.lookup("#spo2Chart");
        LineChart<Number, Number> validationChart = (LineChart<Number, Number>) root.lookup("#validationChart");

        if (plethChart == null || spo2Chart == null || validationChart == null) {
            System.err.println("Could not find one or more charts in the FXML file. Check the fx:id attributes.");
            return;
        }

        // 3. Prepare the data series for each chart
        XYChart.Series<Number, Number> plethSeries = new XYChart.Series<>();
        plethChart.getData().add(plethSeries);
        plethChart.setTitle("Plethysmograph (Playback)");

        XYChart.Series<Number, Number> spo2Series = new XYChart.Series<>();
        spo2Chart.getData().add(spo2Series);
        spo2Chart.setTitle("SpO2 (Playback)");

        XYChart.Series<Number, Number> validationSeries = new XYChart.Series<>();
        validationChart.getData().add(validationSeries);
        validationChart.setTitle("Validation Status (Playback)");

        // 4. Read the data from the file
        List<DataRecord> records = readDataFile(DATA_FILE);
        if (records.isEmpty()) {
            System.err.println("No data found in " + DATA_FILE);
            return;
        }

        // 5. Set up a timeline to play back the data
        Timeline timeline = new Timeline();
        timeline.setCycleCount(records.size());

        // Determine the time interval between data points from the file
        double timeInterval = records.get(1).time - records.get(0).time;
        Duration frameDuration = Duration.seconds(timeInterval / PLAYBACK_SPEED);

        KeyFrame keyFrame = new KeyFrame(frameDuration, event -> {
            int frame = timeline.getCurrentRate() > 0 ? (int) timeline.getCurrentTime().toSeconds() / (int)timeInterval : records.size() - 1 - (int) (timeline.getCurrentTime().toSeconds() / timeInterval);
            if (frame >= records.size()) {
                timeline.stop();
                return;
            }
            DataRecord record = records.get(frame);

            // Add data to the series
            plethSeries.getData().add(new XYChart.Data<>(record.time, record.plethValue));
            spo2Series.getData().add(new XYChart.Data<>(record.time, record.spo2Value));
            validationSeries.getData().add(new XYChart.Data<>(record.time, record.validationScore));

            // Optional: Keep the charts to a scrolling window
            if (plethSeries.getData().size() > 200) plethSeries.getData().remove(0);
            if (spo2Series.getData().size() > 200) spo2Series.getData().remove(0);
            if (validationSeries.getData().size() > 200) validationSeries.getData().remove(0);
        });

        timeline.getKeyFrames().add(keyFrame);
        timeline.play();

        // 6. Show the UI
        primaryStage.setTitle("Offline Pleth/SpO2 Player");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    /**
     * Reads the 4-column data file into a list of DataRecord objects.
     */
    private List<DataRecord> readDataFile(String filename) {
        List<DataRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("[,\\s]+"); // Split by comma or whitespace
                if (parts.length == 4) {
                    records.add(new DataRecord(
                            Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Integer.parseInt(parts[3])
                    ));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error reading or parsing data file: " + e.getMessage());
        }
        return records;
    }

    /**
     * A simple class to hold one row of data from the file.
     */
    private static class DataRecord {
        final double time;
        final double plethValue;
        final double spo2Value;
        final int validationScore;

        DataRecord(double time, double plethValue, double spo2Value, int validationScore) {
            this.time = time;
            this.plethValue = plethValue;
            this.spo2Value = spo2Value;
            this.validationScore = validationScore;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

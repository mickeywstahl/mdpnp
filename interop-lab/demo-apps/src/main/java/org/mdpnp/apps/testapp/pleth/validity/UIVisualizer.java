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

public class UIVisualizer extends Application {

    // Read the data file path from a system property set by Gradle
    private static final String DATA_FILE_PATH = System.getProperty("pleth.data.file", "plethapp.txt");
    private static final double PLAYBACK_SPEED = 1.0; // 1.0 = real-time

    @Override
    public void start(Stage primaryStage) throws Exception {
        URL fxmlUrl = getClass().getResource("/org/mdpnp/apps/testapp/pleth/validity/PlethValidity.fxml");
        if (fxmlUrl == null) {
            System.err.println("Cannot find FXML file.");
            return;
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        LineChart<Number, Number> plethChart = (LineChart<Number, Number>) root.lookup("#plethChart");
        LineChart<Number, Number> spo2Chart = (LineChart<Number, Number>) root.lookup("#spo2Chart");
        LineChart<Number, Number> validationChart = (LineChart<Number, Number>) root.lookup("#validationChart");

        XYChart.Series<Number, Number> plethSeries = new XYChart.Series<>();
        plethChart.getData().add(plethSeries);
        plethChart.setTitle("Plethysmograph (Playback)");

        XYChart.Series<Number, Number> spo2Series = new XYChart.Series<>();
        spo2Chart.getData().add(spo2Series);
        spo2Chart.setTitle("SpO2 (Playback)");

        XYChart.Series<Number, Number> validationSeries = new XYChart.Series<>();
        validationChart.getData().add(validationSeries);
        validationChart.setTitle("Validation Status (Playback)");

        List<DataRecord> records = readDataFile(DATA_FILE_PATH);
        if (records.isEmpty()) {
            System.err.println("No data found in " + DATA_FILE_PATH);
            return;
        }

        Timeline timeline = new Timeline();
        final int[] frameIndex = {0};

        double timeInterval = records.get(1).time - records.get(0).time;
        Duration frameDuration = Duration.seconds(timeInterval / PLAYBACK_SPEED);

        KeyFrame keyFrame = new KeyFrame(frameDuration, event -> {
            if (frameIndex[0] >= records.size()) {
                timeline.stop();
                return;
            }
            DataRecord record = records.get(frameIndex[0]);

            plethSeries.getData().add(new XYChart.Data<>(record.time, record.plethValue));
            spo2Series.getData().add(new XYChart.Data<>(record.time, record.spo2Value));
            validationSeries.getData().add(new XYChart.Data<>(record.time, record.validationScore));

            if (plethSeries.getData().size() > 300) plethSeries.getData().remove(0);
            if (spo2Series.getData().size() > 300) spo2Series.getData().remove(0);
            if (validationSeries.getData().size() > 300) validationSeries.getData().remove(0);
            
            frameIndex[0]++;
        });

        timeline.getKeyFrames().add(keyFrame);
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        primaryStage.setTitle("Pleth Data Visualizer");
        primaryStage.setScene(new Scene(root, 800, 700));
        primaryStage.show();
    }

    private List<DataRecord> readDataFile(String filename) {
        List<DataRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("[,\\s]+");
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
            e.printStackTrace();
        }
        return records;
    }

    private static class DataRecord {
        final double time, plethValue, spo2Value;
        final int validationScore;
        DataRecord(double t, double p, double s, int v) {
            time = t; plethValue = p; spo2Value = s; validationScore = v;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

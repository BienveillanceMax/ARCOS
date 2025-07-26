package com.arcos.service.EventLoop.OutputHandling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TextToSpeech {

    private static final String PIPER_PATH = "/path/to/piper";
    private static final String MODEL_PATH = "/path/to/model.onnx";

    public void speak(String text) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(PIPER_PATH, "--model", MODEL_PATH, "--output_raw");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Write text to piper's stdin
            process.getOutputStream().write(text.getBytes());
            process.getOutputStream().close();

            // Play audio output using aplay
            Process aplayProcess = new ProcessBuilder("aplay", "-r", "22050", "-f", "S16_LE", "-t", "raw").start();
            new Thread(() -> {
                try {
                    process.getInputStream().transferTo(aplayProcess.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Read piper's output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            process.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

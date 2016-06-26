package ml_pedometer.uwaterloo.ca.ml_pedometer;


import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.learning.SupervisedTrainingElement;
import org.neuroph.core.learning.TrainingSet;
import org.neuroph.nnet.MultiLayerPerceptron;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity
{
    TextView stepTextView;
    Button reset;
    Button learn;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);

        LinearLayout lin = (LinearLayout) findViewById(R.id.linear);
        lin.setOrientation(LinearLayout.VERTICAL);

        stepTextView = new TextView(getApplicationContext());
        stepTextView.setTextColor(Color.parseColor("#000000"));
        reset = (Button) findViewById(R.id.button);
        learn = (Button) findViewById(R.id.button2);

        learn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                trainNeuralNetwork();
            }
        });

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        AccelerometerSensorEventListener a = new AccelerometerSensorEventListener(stepTextView, reset);

        sensorManager.registerListener(a, accelSensor, SensorManager.SENSOR_DELAY_FASTEST);

        lin.addView(stepTextView);
    }

    public void trainNeuralNetwork ()
    {
        BufferedReader reader;
        MultiLayerPerceptron mlPerceptron = new MultiLayerPerceptron(100, 8, 4, 1);
        TrainingSet<SupervisedTrainingElement> trainingSet = new TrainingSet<SupervisedTrainingElement>(100, 1);

        String[][] accelVals = new String[200][100];
        double[][] step = new double[200][1];

        //Add training set data from "trainingData.txt"
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open("trainingData.txt")));
            for (int x = 0; x < 200; x++)
            {
                double[] traingVals = new double[100];
                step[x][0] = Double.parseDouble(reader.readLine());
                accelVals[x] = reader.readLine().split("\\t");
                for (int y = 0; y < 100; y++)
                {
                    traingVals[y] = Double.parseDouble(accelVals[x][y]);
                }
                trainingSet.addElement(new SupervisedTrainingElement(traingVals, step[x]));
            }
        } catch (IOException e) {
        }
        mlPerceptron.learn(trainingSet);
        Log.i("Done", "Training Done");
        //Test the trained neural network
        testNeuralNetwork(mlPerceptron, accelVals, step);

        mlPerceptron.save("data/data/ml_pedometer.uwaterloo.ca.ml_pedometer/pedometer_perceptron.nnet");
    }

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // For app to write a text file to external storage, it must ask user for permission
    public static void verifyStoragePermissions(Activity activity)
    {
        // See if app has write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED)
        {
            // If app does not have write permission ask for it
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public void testNeuralNetwork(NeuralNetwork nnet, String[][] testSet, double[][] expectedOutput)
    {
        double[] testVals = new double[100];
        for (int x = 0; x < 200; x++)
        {
            for (int y = 0; y < 100; y++)
            {
                testVals[y] = Double.parseDouble(testSet[x][y]);
            }
            nnet.setInput(testVals);
            nnet.calculate();
            double[] networkOutput = nnet.getOutput();
            //Log.i("Test input", "Input: " + testSet[x]);
            //Log.i("Test output", "Output: " + Arrays.toString(networkOutput));
            //Log.i("Expected output", "Output: "+ Double.toString(expectedOutput[x][0]));
        }
    }

    class AccelerometerSensorEventListener implements SensorEventListener
    {
        NeuralNetwork loadedNeuralNetwork;
        TextView outputStep;
        Button reset;
        Queue<Double> accelReadings = new LinkedList();
        int stepCount = 0;
        int stepTimer = 5;
        float[] smoothedAccel = new float[3];
        public final Handler mHandler = new Handler() {
            public void handleMessage(Message msg) {
                outputStep.setText("Steps: "+ Integer.toString(stepCount)); //this is the textview
            }
        };;

        public AccelerometerSensorEventListener(TextView outputStep, Button resetButton)
        {
            this.reset = resetButton;
            this.outputStep = outputStep;
            loadedNeuralNetwork = NeuralNetwork.load("data/data/ml_pedometer.uwaterloo.ca.ml_pedometer/pedometer_perceptron.nnet");
            mHandler.obtainMessage(1).sendToTarget();
            for (int x =0; x< 100 ; x++)
            {
                accelReadings.add(0.0);
            }
            reset.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    stepCount = 0;
                    mHandler.obtainMessage(1).sendToTarget();
                }
            });
            TimerTask timerTask = new TimerTask()
            {
                @Override
                public void run() {
                    countSteps();
                }
            };
            Timer timer = new Timer();
            timer.schedule(timerTask, stepTimer, stepTimer);
        }

        public void onAccuracyChanged(Sensor s, int i) {
        }

        public void onSensorChanged(SensorEvent se)
        {
            if (se.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)
            {

                smoothedAccel[0] += (se.values[0] - smoothedAccel[0]) / 10;
                smoothedAccel[1] += (se.values[1] - smoothedAccel[1]) / 10;
                smoothedAccel[2] += (se.values[2] - smoothedAccel[2]) / 10;
            }
        }

        public void countSteps ()
        {
            double accelDoubleVal = smoothedAccel[2];

            accelReadings.remove();
            accelReadings.add(accelDoubleVal);
            double[] accelArray = new double[100];
            for (int z =0 ; z< 100 ; z++)
            {
                double queueVal=accelReadings.remove();
                accelArray[z]=queueVal;
            }
            for (int k =0 ; k< 100 ; k++)
            {
                accelReadings.add(accelArray[k]);
            }
            loadedNeuralNetwork.setInput(accelArray);
            loadedNeuralNetwork.calculate();
            double[] networkOutput = loadedNeuralNetwork.getOutput();
            if (networkOutput[0] - 0.91 > 0)
            {
                stepCount++;
                mHandler.obtainMessage(1).sendToTarget();
                accelReadings.clear();
                for (int y =0; y< 100 ; y++)
                {
                    accelReadings.add(0.0);
                }
            }

        }

    }
}
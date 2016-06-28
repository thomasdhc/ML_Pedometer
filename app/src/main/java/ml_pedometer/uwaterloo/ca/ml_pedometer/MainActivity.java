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
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity
{
    TextView stepTextView;
    TextView trained;
    Button reset;
    Button learn;
    public final Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            if (msg.what == 1)
                trained.setText("Neural Network Status: Training");
            else
                trained.setText("Neural Network Status: Trained");//this is the textview
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);

        LinearLayout lin = (LinearLayout) findViewById(R.id.linear);
        lin.setOrientation(LinearLayout.VERTICAL);

        stepTextView = new TextView(getApplicationContext());
        trained = (TextView) findViewById(R.id.textview1);
        stepTextView.setTextColor(Color.parseColor("#000000"));
        trained.setTextColor(Color.parseColor("#000000"));

        reset = (Button) findViewById(R.id.button);
        learn = (Button) findViewById(R.id.button2);

        learn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (trained.getText().equals("Neural Network Status: Not Trained"))
                {
                    mHandler.sendEmptyMessage(1);
                    trainNeuralNetwork();
                    setUpSensor();
                }
            }
        });

        lin.addView(stepTextView);
    }

    public void setUpSensor ()
    {
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        AccelerometerSensorEventListener a = new AccelerometerSensorEventListener(stepTextView, reset);

        sensorManager.registerListener(a, accelSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void trainNeuralNetwork ()
    {
        BufferedReader reader;
        MultiLayerPerceptron mlPerceptron = new MultiLayerPerceptron(210, 8, 4, 1);
        TrainingSet<SupervisedTrainingElement> trainingSet = new TrainingSet<SupervisedTrainingElement>(210, 1);

        String[][] accelVals = new String[1000][210];
        double[][] step = new double[1000][1];

        //Add training set data from "trainingData.txt"
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open("finalStepTrainingData.txt")));
            for (int x = 0; x < 1000; x++)
            {
                double[] traingVals = new double[210];
                step[x][0] = Double.parseDouble(reader.readLine());
                accelVals[x] = reader.readLine().split(" ");
                for (int y = 0; y < 210; y++)
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
        mHandler.sendEmptyMessage(2);
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
        double[] testVals = new double[210];
        for (int x = 0; x < 1000; x++)
        {
            for (int y = 0; y < 210; y++)
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
        List<Double>[] accelReadingsFast =  (ArrayList<Double>[])new ArrayList[3];
        List<Double>[] accelReadingsNormal =  (ArrayList<Double>[])new ArrayList[3];
        List<Double>[] accelReadingsSlow =  (ArrayList<Double>[])new ArrayList[3];
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
            for (int x = 0; x < 3 ; x++)
            {
                accelReadingsFast[x] = new ArrayList<Double>();
                accelReadingsNormal[x] = new ArrayList<Double>();
                accelReadingsSlow[x] = new ArrayList<Double>();
            }
            loadedNeuralNetwork = NeuralNetwork.load("data/data/ml_pedometer.uwaterloo.ca.ml_pedometer/pedometer_perceptron.nnet");
            mHandler.obtainMessage(1).sendToTarget();
            padListZero();
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
            double[] accelDoubleVal = {smoothedAccel[0],smoothedAccel[1],smoothedAccel[2]};
            for (int x =0 ; x<3 ; x++)
            {
                accelReadingsFast[x].remove(39);
                accelReadingsNormal[x].remove(54);
                accelReadingsSlow[x].remove(69);
                accelReadingsFast[x].add(0,accelDoubleVal[x]);
                accelReadingsNormal[x].add(0,accelDoubleVal[x]);
                accelReadingsSlow[x].add(0,accelDoubleVal[x]);
            }
            scaleList("Fast Step");
            scaleList("Normal Step");
            double[][] accelArray = new double[3][210];
            for (int y = 0; y< 3; y++)
            {
                for (int z = 0; z < 70; z++)
                {
                    double queueVal1 = accelReadingsFast[y].get(z);
                    double queueVal2 = accelReadingsNormal[y].get(z);
                    double queueVal3 = accelReadingsSlow[y].get(z);
                    accelArray[0][z] = queueVal1;
                    accelArray[1][z] = queueVal2;
                    accelArray[2][z] = queueVal3;
                }
            }
            loadedNeuralNetwork.setInput(accelArray[0]);
            loadedNeuralNetwork.calculate();
            double[] networkOutput = loadedNeuralNetwork.getOutput();
            if (!checkStep(networkOutput[0]))
            {
                loadedNeuralNetwork.setInput(accelArray[1]);
                loadedNeuralNetwork.calculate();
                double[] networkOutput1 = loadedNeuralNetwork.getOutput();
                if (!checkStep(networkOutput1[0]))
                {
                    loadedNeuralNetwork.setInput(accelArray[2]);
                    loadedNeuralNetwork.calculate();
                    double[] networkOutput2 = loadedNeuralNetwork.getOutput();
                    checkStep(networkOutput2[0]);
                }
            }

        }
        public void scaleList (String stepType)
        {
            for (int y= 0; y<3; y++)
            {
                if (stepType.equals("Normal Step"))
                {
                    for (int x = 1; x < 16; x++)
                    {
                        accelReadingsNormal[y].add(55 - x * 3, accelReadingsNormal[y].get(55 - x * 3 - 1) + (accelReadingsNormal[y].get(55 - x * 3) - accelReadingsNormal[y].get(55 - x * 3 - 1)) / 2);
                    }

                }
                else
                {
                    for (int x = 1; x < 31; x++)
                    {
                        accelReadingsFast[y].add(40 - x, accelReadingsFast[y].get(40 - x - 1) + (accelReadingsFast[y].get(40 - x) - accelReadingsFast[y].get(40 - x - 1)) / 2);
                    }
                }
            }
        }
        public void padListZero ()
        {
            for (int y = 0; y< 3; y++)
            {
                for (int x = 0; x < 70; x++)
                {
                    if (x < 40)
                        accelReadingsFast[y].add(0.0);
                    if (x < 55)
                        accelReadingsNormal[y].add(0.0);
                    accelReadingsSlow[y].add(0.0);
                }
            }
        }
        public boolean checkStep (double result)
        {
            if (result - 0.91 > 0)
            {
                stepCount++;
                mHandler.obtainMessage(1).sendToTarget();
                for (int q =0; q< 3 ; q++)
                {
                    accelReadingsFast[q].clear();
                    accelReadingsNormal[q].clear();
                    accelReadingsSlow[q].clear();
                }
                padListZero();
                return true;
            }
            return false;
        }
    }
}
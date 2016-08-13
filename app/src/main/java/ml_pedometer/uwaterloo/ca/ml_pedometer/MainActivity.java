package ml_pedometer.uwaterloo.ca.ml_pedometer;


import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity
{
    TextView stepTextView;
    TextView trained;
    TextView numOfInput;
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
        numOfInput = new TextView(getApplicationContext());

        trained = (TextView) findViewById(R.id.textview1);
        stepTextView.setTextColor(Color.parseColor("#000000"));
        numOfInput.setTextColor(Color.parseColor("#000000"));
        trained.setTextColor(Color.parseColor("#000000"));

        reset = (Button) findViewById(R.id.button);
        learn = (Button) findViewById(R.id.button2);
        /*learn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (trained.getText().equals("Neural Network Status: Not Trained"))
                {
                    trainNeuralNetwork();
                    setUpSensor();
                }
            }
        });*/
        setUpSensor();
        lin.addView(stepTextView);
        lin.addView(numOfInput);
    }

    public void setUpSensor ()
    {
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        AccelerometerSensorEventListener a = new AccelerometerSensorEventListener(stepTextView,numOfInput, reset);

        sensorManager.registerListener(a, accelSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void trainNeuralNetwork ()
    {
        BufferedReader reader;
        MultiLayerPerceptron mlPerceptron = new MultiLayerPerceptron(59, 8, 4, 1);
        TrainingSet<SupervisedTrainingElement> trainingSet = new TrainingSet<SupervisedTrainingElement>(59, 1);

        String[][] accelVals = new String[1500][59];
        double[][] step = new double[1500][1];

        //Add training set data from "trainingData.txt"
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open("ShuffleAug13.txt")));
            for (int x = 0; x < 1500; x++)
            {
                double[] traingVals = new double[59];
                step[x][0] = Double.parseDouble(reader.readLine());
                accelVals[x] = reader.readLine().split("\\t");
                for (int y = 0; y < 59; y++)
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
        testNeuralNetwork(mlPerceptron);

        String fileName="pedometer_perceptron.nnet";
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath()+"/Download");
        dir.mkdirs();
        File file = new File(dir, fileName);

        mlPerceptron.save(file.toString());
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

    public void testNeuralNetwork(NeuralNetwork nnet)
    {
        String fileName = "Test.txt";
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath()+"/Download");
        dir.mkdirs();
        File file = new File(dir, fileName);

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("trainingFeed.txt")));
            FileOutputStream os = new FileOutputStream(file, true);
            PrintWriter out = new PrintWriter(os);
            double[] testVals = new double[59];
            double[] step = new double[1];
            String[] accelVals = new String[59];
            for (int x = 0; x < 1500; x++)
            {

                step[0] = Double.parseDouble(reader.readLine());
                accelVals = reader.readLine().split("\\t");

                for (int y = 0; y < 59; y++)
                {
                    testVals[y] = Double.parseDouble(accelVals[y]);
                }
                nnet.setInput(testVals);
                nnet.calculate();
                double[] networkOutput = nnet.getOutput();

                out.println("Input: " + testVals);
                out.println("Output: " + Arrays.toString(networkOutput));
                out.println("Expected output"+ Double.toString(step[0]));
            }
            out.close();
            os.close();
        }
        catch (FileNotFoundException f)
        {
            Log.e("FileNotFoundException", "File was not found");
        }
        catch (IOException e)
        {
            Log.e("IOException", "IOException occured");
        }
    }

    class AccelerometerSensorEventListener implements SensorEventListener
    {
        NeuralNetwork loadedNeuralNetwork;
        TextView outputStep;
        TextView numOfInput;

        Button reset;
        Queue<Double> accelQueue = new LinkedList<Double>();
        int stepCount = 0;
        int stepTimer = 10;
        float[] smoothedAccel = new float[3];

        int totalInputs =0;
        FileOutputStream os;
        PrintWriter out;

        public final Handler mHandler = new Handler() {
            public void handleMessage(Message msg) {
                outputStep.setText("Steps: "+ Integer.toString(stepCount)); //this is the textview
                numOfInput.setText("Total Input: " + totalInputs);
            }
        };;

        public AccelerometerSensorEventListener(TextView outputStep, TextView numOfInput, Button resetButton)
        {

            this.numOfInput = numOfInput;
            this.reset = resetButton;
            this.outputStep = outputStep;

            String fileName="pedometer_perceptron.nnet";
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File (sdCard.getAbsolutePath()+"/Download");
            dir.mkdirs();
            File file = new File(dir, fileName);

            loadedNeuralNetwork = NeuralNetwork.load(file.toString());
            //testNN();
            mHandler.obtainMessage(1).sendToTarget();
            for (int x = 0; x < 59 ; x++)
            {
                accelQueue.add(0.0);
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
            double accelDoubleVal =  smoothedAccel[2];
            accelQueue.add(accelDoubleVal);
            accelQueue.remove();
            String inputVals = "";
            double[] accelArray = new double[59];

            for (int z = 0; z < 59; z++)
            {
                accelArray[z] = accelQueue.remove();
            }
            for (int x =0; x< 59; x++)
            {
                inputVals = inputVals + accelArray[x] + " ";
                accelQueue.add(accelArray[x]);
            }

            if (accelArray[0] != 0.0)
            {
                totalInputs ++;
                loadedNeuralNetwork.setInput(accelArray);
                loadedNeuralNetwork.calculate();


                double[] networkOutput1 = loadedNeuralNetwork.getOutput();
                if (networkOutput1[0] > 0.9)
                {
                    recordStepVals (accelArray);
                    stepCount++;

                    accelQueue.clear();
                    for (int y = 0; y < 59; y++)
                    {
                        accelQueue.add(0.0);
                    }
                }

                mHandler.obtainMessage(1).sendToTarget();
            }
        }

        public void recordStepVals (double[] readingsArray)
        {
            String fileName = "NNDectedSteps.txt";
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File (sdCard.getAbsolutePath()+"/Download");
            dir.mkdirs();
            File file = new File(dir, fileName);

            try
            {
                os = new FileOutputStream(file, true);
                out = new PrintWriter(os);
                for (int x = 0 ; x < 59 ; x++)
                {
                    if (x == 58)
                    {
                        out.println(readingsArray[x]);
                    }
                    else
                    {
                        out.print(readingsArray[x] + " ");
                    }
                }
                out.close();
                os.close();
            }
            catch (FileNotFoundException f)
            {
                Log.e("FileNotFoundException", "File was not found");
            }
            catch (IOException e)
            {
                Log.e("IOException", "IOException occured");
            }
        }

        public void testNN ()
        {
            BufferedReader reader;
            MultiLayerPerceptron mlPerceptron = new MultiLayerPerceptron(59, 8, 4, 1);
            TrainingSet<SupervisedTrainingElement> trainingSet = new TrainingSet<SupervisedTrainingElement>(59, 1);

            String[][] accelVals = new String[1500][59];
            double[][] step = new double[1500][1];

            //Add training set data from "trainingData.txt"
            try {
                reader = new BufferedReader(new InputStreamReader(getAssets().open("trainingFeed.txt")));
                for (int x = 0; x < 1500; x++)
                {
                    step[x][0] = Double.parseDouble(reader.readLine());
                    accelVals[x] = reader.readLine().split("\\t");
                }
            } catch (IOException e) {
            }

            double[] testVals = new double[59];
            for (int x = 0; x < 1500; x++)
            {
                for (int y = 0; y < 59; y++)
                {
                    testVals[y] = Double.parseDouble(accelVals[x][y]);
                }
                loadedNeuralNetwork.setInput(testVals);
                loadedNeuralNetwork.calculate();
                double[] networkOutput = loadedNeuralNetwork.getOutput();
                Log.i("Test input", "Input: " +x + " " + accelVals[x]);
                Log.i("Test output", "Output: " + x+" "  + Arrays.toString(networkOutput));
                Log.i("Expected output", "Output: "+  Double.toString(step[x][0]));
            }
        }
    }
}
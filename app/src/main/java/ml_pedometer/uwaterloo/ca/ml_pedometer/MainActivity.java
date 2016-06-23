package ml_pedometer.uwaterloo.ca.ml_pedometer;


import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.learning.SupervisedTrainingElement;
import org.neuroph.core.learning.TrainingSet;
import org.neuroph.nnet.MultiLayerPerceptron;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        BufferedReader reader;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MultiLayerPerceptron mlPerceptron = new MultiLayerPerceptron(100,8,4,1);
        //NeuralNetwork mlPerceptron = new Perceptron(100,1);
        TrainingSet<SupervisedTrainingElement> trainingSet = new TrainingSet<SupervisedTrainingElement>(100,1);
        String[][] accelVals = new String[200][100];
        double[][] step = new double[200][1];

        //Add training set data from "trainingData.txt"
        try
        {
            reader = new BufferedReader(new InputStreamReader(getAssets().open("trainingData.txt")));
            for (int x =0 ; x< 200 ; x++)
            {
                double [] traingVals= new double[100];
                step[x][0] = Double.parseDouble(reader.readLine());
                accelVals[x] = reader.readLine().split("\\t");
                for (int y =0 ; y< 100; y++ )
                {
                    traingVals[y]= Double.parseDouble(accelVals[x][y]);
                }
                trainingSet.addElement(new SupervisedTrainingElement(traingVals,step[x]));
            }
        }
        catch (IOException e)
        {
        }
        mlPerceptron.learn(trainingSet);
        Log.i("Done", "Training Done");
        //Test the trained neural network
        testNeuralNetwork(mlPerceptron, accelVals, step);

        //mlPerceptron.save("pedometer_perceptron.nnet");

        //NeuralNetwork loadedNeuralNetwork = NeuralNetwork.load("pedometer_perceptron.nnet");
        //Test the loaded neural network
       // testNeuralNetwork(loadedNeuralNetwork, accelVals, step);
    }

    public void testNeuralNetwork (NeuralNetwork nnet, String[][] testSet, double[][] expectedOutput)
    {
        double[] testVals = new double[100];
        for (int x=0; x<200; x++)
        {
            for (int y =0 ; y< 100; y++ )
            {
                testVals[y]= Double.parseDouble(testSet[x][y]);
            }
            nnet.setInput(testVals);
            nnet.calculate();
            double[] networkOutput = nnet.getOutput();
            Log.i("Test input", "Input: " + testSet[x]);
            Log.i("Test output", "Output: " + Arrays.toString(networkOutput));
            Log.i("Expected output", "Output: "+ Double.toString(expectedOutput[x][0]));
        }
    }
}
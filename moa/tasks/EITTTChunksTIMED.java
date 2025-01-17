/*
 *    EvaluateInterleavedTestThenTrain2.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package moa.tasks;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.IntOption;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import moa.capabilities.Capability;
import moa.capabilities.ImmutableCapabilities;
import moa.classifiers.MultiClassClassifier;
import moa.classifiers.Multithreading;
import moa.classifiers.meta.AdaptiveRandomForest;
import moa.core.*;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.preview.LearningCurve;
import moa.learners.Learner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;

/**
 * Task for evaluating a classifier on a stream by testing then training with each example in sequence.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @version $Revision: 7 $
 */
public class EITTTChunksTIMED extends ClassificationMainTask {

    @Override
    public String getPurposeString() {
        return "Evaluates a classifier on a stream by testing then training with each example in sequence.";
    }

    private static final long serialVersionUID = 1L;

    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Learner to train.", MultiClassClassifier.class, "moa.classifiers.bayes.NaiveBayes");

    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    public IntOption chunkSizeOption = new IntOption("chunkSize", 'c',
            "Size of the chunks to be trained.", 1);

    public IntOption randomSeedOption = new IntOption(
            "instanceRandomSeed", 'r',
            "Seed for random generation of instances.", 1);

    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "BasicClassificationPerformanceEvaluator");

    public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
            "Maximum number of instances to test/train on  (-1 = no limit).",
            100000000, -1, Integer.MAX_VALUE);

    public IntOption timeLimitOption = new IntOption("timeLimit", 't',
            "Maximum number of seconds to test/train for (-1 = no limit).", 120,
            -1, Integer.MAX_VALUE);

    public IntOption sampleFrequencyOption = new IntOption("sampleFrequency",
            'f',
            "How many instances between samples of the learning performance.",
            1000000000, 0, Integer.MAX_VALUE);

    public IntOption memCheckFrequencyOption = new IntOption(
            "memCheckFrequency", 'q',
            "How many instances between memory bound checks.", 1000000000, 0,
            Integer.MAX_VALUE);

    public FileOption dumpFileOption = new FileOption("dumpFile", 'd',
            "File to append intermediate csv results to.", null, "csv", true);

    @Override
    public Class<?> getTaskResultType() {
        return LearningCurve.class;
    }

    @Override
    protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {
        String learnerString = this.learnerOption.getValueAsCLIString();
        String streamString = this.streamOption.getValueAsCLIString();

        int chunkSize = this.chunkSizeOption.getValue();

        Learner learner = (Learner) getPreparedClassOption(this.learnerOption);
        if (learner.isRandomizable()) {
            learner.setRandomSeed(this.randomSeedOption.getValue());
            learner.resetLearning();
        }

        boolean isInitialised = false;
        if (learner instanceof Multithreading) {
            try {
                ((Multithreading) learner).init();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            isInitialised = true;
        }

        ExampleStream stream = (InstanceStream) getPreparedClassOption(this.streamOption);

        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);
        learner.setModelContext(stream.getHeader());
        int maxInstances = this.instanceLimitOption.getValue();
        long instancesProcessed = 0;
        int maxSeconds = this.timeLimitOption.getValue();
        int secondsElapsed = 0;
        monitor.setCurrentActivity("Evaluating learner...", -1.0);
        LearningCurve learningCurve = new LearningCurve(
                "learning evaluation instances");
        File dumpFile = this.dumpFileOption.getFile();
        PrintStream immediateResultStream = null;
        if (dumpFile != null) {
            try {
                if (dumpFile.exists()) {
                    immediateResultStream = new PrintStream(
                            new FileOutputStream(dumpFile, true), true);
                } else {
                    immediateResultStream = new PrintStream(
                            new FileOutputStream(dumpFile), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open immediate result file: " + dumpFile, ex);
            }
        }
        boolean firstDump = true;
        float timeTaken = 0;
        // Clock Time Start
        long t1 = System.currentTimeMillis();
        boolean firstChunk = true;

        while (stream.hasMoreInstances()
                && ((maxInstances < 0) || (instancesProcessed < maxInstances))
                && ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {

            Instances chunkInstances = new Instances(stream.getHeader(), chunkSize);
//            Attribute classatt = chunkInstances.attribute(chunkInstances.numAttributes());
////            System.out.println(classatt.indexOfValue("2134576i") + "  -  " + classatt.numValues() +
////                    "\nvalues " + classatt.getAttributeValues());
//            System.out.println("\nnum att " + chunkInstances.numAttributes() +
//                    "\nlast att " + classatt +
//                    "\nlast att valuess " + classatt.getAttributeValues() +
//                    "\nlast att fetch inexistent " + classatt.indexOfValue("2345") +
//                    "\nexists in array " +  classatt.getAttributeValues().contains("2") +
//                    "\nindex on att str" + classatt.toString().indexOf("8"));


            while (stream.hasMoreInstances() && chunkInstances.numInstances() < chunkSize) {
                chunkInstances.add((Instance) stream.nextInstance().getData());
                if (chunkInstances.numInstances()
                        % INSTANCES_BETWEEN_MONITOR_UPDATES == 0) {
                    if (monitor.taskShouldAbort()) {
                        return null;
                    }

                    long estimatedRemainingInstances = stream.estimatedRemainingInstances();

                    if (maxInstances > 0) {
                        long maxRemaining = maxInstances - instancesProcessed;
                        if ((estimatedRemainingInstances < 0) || (maxRemaining < estimatedRemainingInstances)) {
                            estimatedRemainingInstances = maxRemaining;
                        }
                    }
                    monitor.setCurrentActivityFractionComplete((double) instancesProcessed/ (double) (instancesProcessed + estimatedRemainingInstances));
                }
            }
            ////Testing
            if(!firstChunk) {
//                System.out.println("testing");
                for (int i=0; i< chunkInstances.numInstances(); i++) {
                    Example testInst = new InstanceExample((Instance) chunkInstances.instance(i));
                    double[] prediction = learner.getVotesForInstance(testInst);
                    evaluator.addResult(testInst, prediction);
                }
            } else {
                firstChunk = false;
            }

            ////Training
            learner.trainOnInstances(chunkInstances);
            instancesProcessed += chunkInstances.numInstances();
            if (instancesProcessed % INSTANCES_BETWEEN_MONITOR_UPDATES == 0) {
                if (monitor.taskShouldAbort()) {
                    return null;
                }
                long estimatedRemainingInstances = stream.estimatedRemainingInstances();
                if (maxInstances > 0) {
                    long maxRemaining = maxInstances - instancesProcessed;
                    if ((estimatedRemainingInstances < 0)
                            || (maxRemaining < estimatedRemainingInstances)) {
                        estimatedRemainingInstances = maxRemaining;
                    }
                }
                monitor.setCurrentActivityFractionComplete(estimatedRemainingInstances < 0 ? -1.0
                        : (double) instancesProcessed
                        / (double) (instancesProcessed + estimatedRemainingInstances));
                if (monitor.resultPreviewRequested()) {
                    monitor.setLatestResultPreview(learningCurve.copy());
                }
            }
            secondsElapsed = (int) (System.currentTimeMillis() - t1)/1000;
        }

        long t2 = System.currentTimeMillis();
        //Clock Time End
        timeTaken = (t2 - t1) / 1000F;
        learningCurve.insertEntry(new LearningEvaluation(
                new Measurement[]{
                        new Measurement(
                                "learning evaluation instances",
                                instancesProcessed),
                        new Measurement(
                                "Wall Time (Actual Time)"
                                , timeTaken)
                },
                evaluator, learner));
        if (immediateResultStream != null) {
            if (firstDump) {
                immediateResultStream.print("Learner,stream,randomSeed,");
                immediateResultStream.println(learningCurve.headerToString());
            }
            immediateResultStream.print(learnerString + "," + streamString + "," + this.randomSeedOption.getValueAsCLIString() + ",");
            immediateResultStream.println(learningCurve.entryToString(learningCurve.numEntries() - 1));
            immediateResultStream.flush();
            immediateResultStream.close();
        }

        if (isInitialised) {
            ((Multithreading) learner).trainingHasEnded();
        }
        if (learner instanceof Multithreading) {
            ((Multithreading) learner).trainingHasEnded();
        }
        //Temporary (easy and simple) solution to shutdown the executor
        if (learner instanceof AdaptiveRandomForest)
            ((AdaptiveRandomForest)learner).trainingHasEnded();

        return learningCurve;
    }

    @Override
    public ImmutableCapabilities defineImmutableCapabilities() {
        if (this.getClass() == EITTTChunksTIMED.class)
            return new ImmutableCapabilities(Capability.VIEW_STANDARD, Capability.VIEW_LITE);
        else
            return new ImmutableCapabilities(Capability.VIEW_STANDARD);
    }
}

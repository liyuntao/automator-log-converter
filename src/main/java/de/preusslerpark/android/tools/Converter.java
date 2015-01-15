/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package de.preusslerpark.android.tools;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.InstrumentationResultParser;
import com.android.ddmlib.testrunner.TestIdentifier;

import java.io.*;
import java.util.Map;

public class Converter {

    private final String mTestSuiteName;
    private final String mOutPath;

    private Converter(String testSuiteName, String outPath) {
        this.mTestSuiteName = testSuiteName;
        this.mOutPath = new File(outPath, testSuiteName + ".xml").toString();
    }

    public static Converter createConverterForPath(String testSuiteName, String outPath) {
        return new Converter(testSuiteName, outPath);
    }

    public void convert(String streamToRead) throws IOException {
        FileOutputStream currentFile = new FileOutputStream(mOutPath);
        final XMLResultFormatter outputter = new XMLResultFormatter();
        InstrumentationResultParser parser = createParser(mTestSuiteName, outputter);
        outputter.setOutput(currentFile);
        outputter.startTestSuite();

        String[] lines = streamToRead.split("\n");
        parser.processNewLines(lines);
        parser.done();
        currentFile.close();
    }

    private InstrumentationResultParser createParser(final String testSuite, final XMLResultFormatter outputter) {
        /**
         * Receives event notifications during instrumentation test runs.
         * Patterned after junit.runner.TestRunListener.
         *
         * The sequence of calls will be:
         * <ul>
         *     <li> testRunStarted </li>
         *     <li> testStarted </li>
         *     <li> [testFailed] </li>
         *     <li> testEnded </li>
         *     <li> ... </li>
         *     <li> [testRunFailed] </li>
         *     <li> testRunEnded </li>
         * </ul>
         */

        ITestRunListener listener = new ITestRunListener() {
            /**
             * Reports the start of a test run.
             *
             * @param runName the test run name
             * @param testCount total number of tests in test run
             */
            @Override
            public void testRunStarted(String runName, int testCount) {
                System.out.println("test suite started " + runName + ", testCount: " + testCount);
            }

            /**
             * Reports the start of an individual test case.
             *
             * @param testIdentifier identifies the test
             */
            @Override
            public void testStarted(TestIdentifier testIdentifier) {
                System.out.println("testStarted " + testIdentifier.toString());
            }

            /**
             * Reports the failure of an individual test case.
             *
             * @param testIdentifier identifies the test
             * @param s stack trace of failure
             */
            @Override
            public void testFailed(TestIdentifier testIdentifier, String s) {
                System.out.println("testFailed " + s);
                BufferedReader reader = new BufferedReader(new StringReader(s));
                try {
                    String error = reader.readLine();
                    String[] errorSeperated = error.split(":", 2);
                    outputter.addFailure(testIdentifier, errorSeperated.length > 1 ?
                            errorSeperated[1].trim() : "Failed", errorSeperated[0].trim(), s.substring(error.length()));
                } catch (IOException e) {
                    e.printStackTrace();
                    outputter.addFailure(testIdentifier, e);
                }
            }

            @Override
            public void testAssumptionFailure(TestIdentifier testIdentifier, String s) {
                System.out.println("assumption Failed " + s);
            }

            @Override
            public void testIgnored(TestIdentifier testIdentifier) {}

            /**
             * Reports the execution end of an individual test case.
             * @param testIdentifier identifies the test
             * @param map a {@link Map} of the metrics emitted
             */
            @Override
            public void testEnded(TestIdentifier testIdentifier, Map<String, String> map) {
                System.out.println("testEnded " + testIdentifier);
                outputter.endTest(testIdentifier);
            }

            /**
             * Reports test run failed to complete due to a fatal error.
             *
             * @param s describing reason for run failure.
             */
            @Override
            public void testRunFailed(String s) {
                System.out.println("testRunFailed " + s);
            }

            /**
             * Reports test run stopped before completion due to a user request.
             *
             * TODO: currently unused, consider removing
             * @param l device reported elapsed time, in milliseconds
             */
            @Override
            public void testRunStopped(long l) {}

            /**
             * Reports end of test run.
             *
             * @param l device reported elapsed time, in milliseconds
             * @param map key-value pairs reported at the end of a test run
             */
            @Override
            public void testRunEnded(long l, Map<String, String> map) {
                System.out.println("testRunEnded " + l);
                outputter.endTestSuite(l);
            }
        };
        return new InstrumentationResultParser(testSuite, listener);
    }
}

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

import com.android.ddmlib.testrunner.TestIdentifier;
import junit.framework.AssertionFailedError;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTestRunner;
import org.apache.tools.ant.taskdefs.optional.junit.XMLConstants;
import org.apache.tools.ant.util.DOMElementWriter;
import org.apache.tools.ant.util.DateUtils;
import org.apache.tools.ant.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.Date;
import java.util.Hashtable;


/**
 * Prints XML output of the test to a specified Writer.
 *
 * @author dpreussler, based on JUnitResultFormatter
 * @see FormatterElement
 */

public class XMLResultFormatter implements XMLConstants {

    private static final double ONE_SECOND = 1000.0;
    private static final String XSL_NAME = "report.xsl";

    /** constant for unnnamed testsuites/cases */
    private static final String UNKNOWN = "unknown";

    /** The XML document. */
    private Document doc;

    /** The wrapper for the whole testsuites. */
    private Element rootElement;

    /** The wrapper for the whole testsuite. */
    private Element testSuiteElement;

    /** The ClassName of testcases. */
    private String testSuiteName;

    /** Element for the current test. */
    private Hashtable<TestIdentifier, Element> testElements = new Hashtable<TestIdentifier, Element>();

    /** tests that failed. */
    private Hashtable<TestIdentifier, TestIdentifier> failedTests = new Hashtable<TestIdentifier, TestIdentifier>();

    /** Where to write the log to. */
    private OutputStream out;

    /** No arg constructor. */
    public XMLResultFormatter() {
    }

    private static DocumentBuilder getDocumentBuilder() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (Exception exc) {
            throw new ExceptionInInitializerError(exc);
        }
    }

    /**
     * {@inheritDoc}.
     */
    public void setOutput(OutputStream out) {
        this.out = out;
    }

    /**
     * {@inheritDoc}.
     */
    public void setSystemOutput(String out) {
        formatOutput(SYSTEM_OUT, out);
    }

    /**
     * {@inheritDoc}.
     */
    public void setSystemError(String out) {
        formatOutput(SYSTEM_ERR, out);
    }

    /**
     * The whole testsuite started.
     */
    public void startTestSuite() {
        doc = getDocumentBuilder().newDocument();
        rootElement = doc.createElement(TESTSUITES);
        testSuiteElement = doc.createElement(TESTSUITE);

        // add the timestamp
        final String timestamp = DateUtils.format(new Date(), DateUtils.ISO8601_DATETIME_PATTERN);
        testSuiteElement.setAttribute(TIMESTAMP, timestamp);

        // Output properties
        Element propsElement = doc.createElement(PROPERTIES);
        testSuiteElement.appendChild(propsElement);
        // Properties props = suite.getProperties();
        // if (props != null) {
        // Enumeration e = props.propertyNames();
        // while (e.hasMoreElements()) {
        // String name = (String) e.nextElement();
        // Element propElement = doc.createElement(PROPERTY);
        // propElement.setAttribute(ATTR_NAME, name);
        // propElement.setAttribute(ATTR_VALUE, props.getProperty(name));
        // propsElement.appendChild(propElement);
        // }
        // }
    }

    /**
     * The whole testsuite ended.
     *
     * @param time the test duration
     * @throws BuildException on error.
     */
    public void endTestSuite(long time) throws BuildException {
        // testSuiteElement.setAttribute(ATTR_TESTS, "" + suite.runCount());
        // testSuiteElement.setAttribute(ATTR_FAILURES, "" + suite.failureCount());
        // testSuiteElement.setAttribute(ATTR_ERRORS, "" + suite.errorCount());
        testSuiteElement.setAttribute(ATTR_TIME, "" + (time / ONE_SECOND));
        testSuiteElement.setAttribute(ATTR_CLASSNAME, testSuiteName == null ? UNKNOWN : testSuiteName);
        if (out != null) {
            Writer wri = null;
            try {
                wri = new BufferedWriter(new OutputStreamWriter(out, "UTF8"));
                wri.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
                wri.write("<?xml-stylesheet type=\"text/xsl\" href=\"" + XSL_NAME + "\" ?>\n");
                rootElement.appendChild(testSuiteElement);
                new DOMElementWriter().write(rootElement, wri, 0, "  ");
            } catch (IOException exc) {
                throw new BuildException("Unable to write log file " + exc.toString(), exc);
            } finally {
                if (wri != null) {
                    try {
                        wri.flush();
                    } catch (IOException ex) {
                        // ignore
                    }
                }
                if (out != System.out && out != System.err) {
                    FileUtils.close(wri);
                }
            }
        }
    }

    /**
     * Interface TestListener.
     * <p/>
     * A Test is finished.
     *
     * @param test the test.
     */
    public void endTest(TestIdentifier test) {
        Element currentTest;
        if (!failedTests.containsKey(test)) {
            currentTest = doc.createElement(TESTCASE);
            String n = test.getTestName();
            currentTest.setAttribute(ATTR_NAME, n == null ? UNKNOWN : n);
            // a TestSuite can contain Tests from multiple classes,
            // even tests with the same name - disambiguate them.
            currentTest.setAttribute(ATTR_CLASSNAME, test.getClassName());
            testSuiteElement.appendChild(currentTest);
            testElements.put(test, currentTest);

            // FIXME: Value will be overwritten. We assumes that testcases under same testsuite have the same className
            testSuiteName = test.getClassName();
        }
    }

    /**
     * Interface TestListener for JUnit &lt;= 3.4.
     * <p/>
     * A Test failed.
     *
     * @param test the test.
     * @param t    the exception.
     */
    public void addFailure(TestIdentifier test, Throwable t) {
        formatError(FAILURE, test, t);
    }

    public void addFailure(TestIdentifier test, String message, String className, String strace) {
        formatError(FAILURE, test, message, className, strace);
    }


    /**
     * Interface TestListener for JUnit &gt; 3.4.
     * <p/>
     * A Test failed.
     *
     * @param test the test.
     * @param t    the assertion.
     */
    public void addFailure(TestIdentifier test, AssertionFailedError t) {
        addFailure(test, (Throwable) t);
    }

    /**
     * Interface TestListener.
     * <p/>
     * An error occurred while running the test.
     *
     * @param test the test.
     * @param t    the error.
     */
    public void addError(TestIdentifier test, Throwable t) {
        formatError(ERROR, test, t);
    }

    private void formatError(String type, TestIdentifier test, Throwable t) {
        formatError(type, test, t.getMessage(), t.getClass().getName(), JUnitTestRunner.getFilteredTrace(t));
    }

    private void formatError(String type, TestIdentifier test, String message, String className, String strace) {
        strace = strace.replace("\r", "");
        if (test != null) {
            endTest(test);
            failedTests.put(test, test);
        }

        Element nested = doc.createElement(type);
        Element currentTest;
        if (test != null) {
            currentTest = testElements.get(test);
        } else {
            currentTest = testSuiteElement;
        }

        currentTest.appendChild(nested);
        if (message != null && message.length() > 0) {
            nested.setAttribute(ATTR_MESSAGE, message);
        }
        nested.setAttribute(ATTR_TYPE, className);

        Text trace = doc.createTextNode(strace);
        nested.appendChild(trace);
    }

    private void formatOutput(String type, String output) {
        Element nested = doc.createElement(type);
        testSuiteElement.appendChild(nested);
        nested.appendChild(doc.createCDATASection(output));
    }

}

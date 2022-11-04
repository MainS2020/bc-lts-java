package org.bouncycastle.crypto.test;

import java.util.Enumeration;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.bouncycastle.crypto.NativeEntropyTests;
import org.bouncycastle.crypto.NativeFailsafeTest;

public class AllTests
    extends TestCase
{
    public static void main(String[] args)
    {

        TestResult res = junit.textui.TestRunner.run(suite());
        if (res.errorCount() > 0)
        {
            Enumeration<TestFailure> e = res.errors();
            while (e.hasMoreElements())
            {
                System.out.println(e.nextElement().toString());
            }
        }
        if (res.failureCount() > 0)
        {
            Enumeration<TestFailure> e = res.failures();
            while (e.hasMoreElements())
            {
                System.out.println(e.nextElement().toString());
            }
        }

        if (!res.wasSuccessful())
        {
            System.exit(1);
        }

    }

    public static Test suite()
    {
        TestSuite suite = new TestSuite("Lightweight Crypto Tests");

        suite.addTestSuite(SimpleTestTest.class);
        suite.addTestSuite(GCMReorderTest.class);
        suite.addTestSuite(NativeFailsafeTest.class);
        suite.addTestSuite(NativeEntropyTests.class);


        return new BCTestSetup(suite);
    }

    static class BCTestSetup
        extends TestSetup
    {
        public BCTestSetup(Test test)
        {
            super(test);
        }

        protected void setUp()
        {

        }

        protected void tearDown()
        {

        }
    }
}

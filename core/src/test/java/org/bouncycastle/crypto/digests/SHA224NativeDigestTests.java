package org.bouncycastle.crypto.digests;

import junit.framework.TestCase;
import org.bouncycastle.crypto.CryptoServicesRegistrar;
import org.bouncycastle.crypto.NativeServices;
import org.bouncycastle.crypto.SavableDigest;
import org.bouncycastle.crypto.engines.TestUtil;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;

public class SHA224NativeDigestTests
        extends TestCase
{
    @Before
    public void setUp()
    {
//        FipsStatus.isReady();
        CryptoServicesRegistrar.setNativeEnabled(true);
    }


    @After
    public void tearDown()
    {
        CryptoServicesRegistrar.setNativeEnabled(true);
    }


    static boolean skip() {
        if (!TestUtil.hasNativeService(NativeServices.SHA224))
        {
            if (!(System.getProperty("test.bclts.ignore.native", "").contains("sha") || System.getProperty("test.bclts.ignore.native", "").contains("sha224")))
            {
                fail("Skipping SHA224 Limit Test: " + TestUtil.errorMsg());
            }
            return true;
        }

        return false;
    }


    @Test
    public void testReturnLen() throws Exception
    {
        if (skip())
        {
            return;
        }

        SHA224Digest jdig = new SHA224Digest();

        SHA224NativeDigest ndig = new SHA224NativeDigest();
        TestCase.assertEquals(jdig.getByteLength(), ndig.getByteLength());
        TestCase.assertEquals(jdig.getDigestSize(), ndig.getDigestSize());

        // digest result tested elsewhere
        byte[] z = new byte[jdig.getDigestSize() * 2];
        TestCase.assertEquals(jdig.doFinal(z, 0), ndig.doFinal(z, 0));

    }


    @Test
    public void testSHA224Empty()
            throws Exception
    {

        if (skip())
        {
            return;
        }

        byte[] empty = new byte[28];
        {
            SHA224Digest dig = new SHA224Digest(); // java one
            dig.doFinal(empty, 0);
        }

        SavableDigest dig = SHA224Digest.newInstance();
        byte[] res = new byte[dig.getDigestSize()];
        TestCase.assertEquals(28,dig.doFinal(res, 0));

        TestCase.assertTrue("Empty Digest result", Arrays.areEqual(res, empty));

    }

    @Test
    public void testSHA224FullStateEncoding()
            throws Exception
    {
        if (skip())
        {
            return;
        }



        byte[] msg = new byte[256];
        SecureRandom rand = new SecureRandom();
        rand.nextBytes(msg);


        for (int t = 0; t < 256; t++)
        {

            SavableDigest dig = SHA224Digest.newInstance();
            dig.update(msg, 0, t);
            byte[] state = dig.getEncodedState();

            byte[] resAfterStateExtraction = new byte[dig.getDigestSize()];
            TestCase.assertEquals(28,dig.doFinal(resAfterStateExtraction, 0));

            SavableDigest dig2 =  SHA224Digest.newInstance(state,0);
            byte[] resStateRecreated = new byte[dig2.getDigestSize()];
            TestCase.assertEquals(28, dig2.doFinal(resStateRecreated, 0));


            SHA224Digest javaDigest = new SHA224Digest();
            javaDigest.update(msg, 0, t);

            byte[] resJava = new byte[javaDigest.getDigestSize()];
            TestCase.assertEquals(28, javaDigest.doFinal(resJava, 0));


            TestCase.assertTrue("Java = native post state extraction", Arrays.areEqual(resJava, resAfterStateExtraction));
            TestCase.assertTrue("Java = native recreated from extracted state", Arrays.areEqual(resJava, resStateRecreated));
        }
    }


    public void testSHA224ByteByByte()
            throws Exception
    {

        if (skip())
        {
            return;
        }


        byte[] msg = new byte[256];
        SecureRandom rand = new SecureRandom();
        rand.nextBytes(msg);

        SavableDigest dig = SHA224Digest.newInstance();
        SHA224Digest javaDigest = new SHA224Digest();

        for (int t = 0; t < 256; t++)
        {
            dig.update(msg[t]);
            javaDigest.update(msg[t]);
        }

        byte[] resJava = new byte[javaDigest.getDigestSize()];
        TestCase.assertEquals(28, javaDigest.doFinal(resJava, 0));

        byte[] nativeDigest = new byte[dig.getDigestSize()];
        TestCase.assertEquals(28, dig.doFinal(nativeDigest, 0));

        TestCase.assertTrue("Java = native byte by byte", Arrays.areEqual(resJava, nativeDigest));

    }


    /**
     * Prove that a digest created from the state of another one will calculate the same result with the same bytes as
     * input. Final value compared to java version.
     *
     * @throws Exception
     */
    @Test
    public void testSHA224FullStateEncodingExtraData()
            throws Exception
    {

        if (skip())
        {
            return;
        }


        byte[] msg = new byte[256];
        SecureRandom rand = new SecureRandom();
        rand.nextBytes(msg);


        SavableDigest dig =  SHA224Digest.newInstance();
        dig.update(msg, 0, 12);
        byte[] state = dig.getEncodedState();


        SavableDigest dig2 = SHA224Digest.newInstance(state, 0);

        dig.update(msg, 12, msg.length - 12);
        dig2.update(msg, 12, msg.length - 12);

        SHA224Digest javaDigest = new SHA224Digest();
        javaDigest.update(msg, 0, msg.length);

        byte[] d1Result = new byte[dig.getDigestSize()];
        byte[] d2Result = new byte[dig2.getDigestSize()];
        byte[] javaResult = new byte[javaDigest.getDigestSize()];

        TestCase.assertEquals(28,dig.doFinal(d1Result, 0));
        TestCase.assertEquals(28,dig2.doFinal(d2Result, 0));
        TestCase.assertEquals(28,javaDigest.doFinal(javaResult, 0));


        TestCase.assertTrue(Arrays.areEqual(javaResult, d1Result) && Arrays.areEqual(javaResult, d2Result));

    }

    public void testUpdateLimitEnforcement()
            throws Exception
    {


        if (skip())
        {
            return;
        }



        new SHA224NativeDigest()
        {
            {
                try
                {
                    update(null, 0, 0);
                    fail("accepted null byte array");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("input was null"));
                }
            }
        };


        new SHA224NativeDigest()
        {
            {
                try
                {
                    update(new byte[0], -1, 0);
                    fail("accepted negative input offset");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("offset is negative"));
                }
            }
        };


        new SHA224NativeDigest()
        {
            {
                try
                {
                    update(new byte[0], 0, -1);
                    fail("accepted negative input len");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("len is negative"));
                }
            }
        };

        new SHA224NativeDigest()
        {
            {
                try
                {
                    update(new byte[1], 1, 1);
                    fail("accepted input past end of buffer");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("array too short for offset + len"));
                }
            }
        };


        new SHA224NativeDigest()
        {
            {

                //
                // Pass in an array but with offset at the limit and zero length
                // Assert this works
                //

                byte[] res = new byte[getDigestSize()];
                update(new byte[20], 19, 0);
                TestCase.assertEquals(28,doFinal(res, 0));

                TestCase.assertTrue("Empty Digest result",
                        Arrays.areEqual(
                                res,
                                Hex.decode("d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f")
                        ));
            }
        };


        new SHA224NativeDigest()
        {
            {

                //
                // Pass in an array but with offset at zero with zero length
                // Assert this doesn't process anything.
                //

                byte[] res = new byte[getDigestSize()];
                update(new byte[20], 0, 0);
                TestCase.assertEquals(28, doFinal(res, 0));

                TestCase.assertTrue("Empty Digest result",
                        Arrays.areEqual(
                                res,
                                Hex.decode("d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f")
                        ));
            }
        };


    }

    public void testDoFinalLimitEnforcement()
            throws Exception
    {

        if (skip())
        {
            return;
        }



        new SHA224NativeDigest()
        {
            {
                try
                {
                    doFinal(null, 0);
                    fail("accepted null byte array");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("output was null"));
                }
            }
        };


        new SHA224NativeDigest()
        {
            {
                try
                {
                    doFinal(new byte[0], -1);
                    fail("accepted negative output offset");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("offset is negative"));
                }
            }
        };


        new SHA224NativeDigest()
        {
            {
                try
                {
                    doFinal(new byte[0], 1);
                    fail("accept offset pas end of buffer");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("offset past end of array"));
                }
            }
        };

        new SHA224NativeDigest()
        {
            {
                try
                {
                    doFinal(new byte[20], 0);
                    fail("accepted output array too small");
                }
                catch (Exception ex)
                {
                    TestCase.assertTrue(ex.getMessage().contains("array + offset too short for digest output"));
                }
            }
        };





        new SHA224NativeDigest()
        {
            {
                //
                // Should result in result array with leading zero byte
                // followed by no-input digest value.
                //

                byte[] res = new byte[getDigestSize() + 1];
                TestCase.assertEquals(28, doFinal(res, 1));
                TestCase.assertTrue(
                        Arrays.areEqual(
                                Hex.decode("00d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f"),
                                res)
                );

            }
        };

    }

    public void testRecreatingFromEncodedState()
            throws Exception
    {

        if (skip())
        {
            return;
        }



        //
        // Generate the sane state, we need to do this as runtime as it may very because of alignment.
        //
        final byte[] saneState;

        SHA224NativeDigest dig = new SHA224NativeDigest()
        {
            {
                update((byte) 1);
                update((byte) 1);
                update((byte) 1);
                update((byte) 1);
            }
        };
        saneState = dig.getEncodedState();


        try
        {
            new SHA224NativeDigest().restoreState(null, 0);
            fail("too short");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("input was null"));
        }



        try
        {
            new SHA224NativeDigest().restoreState(new byte[saneState.length - 2], 0);
            fail("too short");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("array at offset too short for encoded input"));
        }


        try
        {
            new SHA224NativeDigest().restoreState(new byte[saneState.length], 0);
            fail("bad id");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("invalid SHA224 encoded state"));
        }


        // At length should fail.
        try
        {
            // Check bufPtr limit test

            byte[] state = Arrays.clone(saneState);


            //
            // Our sane state has four bytes written to it, so both bufPtr and byteCount should be four.
            // Here we find every four in the state and set it to 64, because we cannot guarantee the position
            // within in the struct that the LSB of bufPtr will be.
            // This will enable us to assert that length checking of encoded bufPtr is correct.

            for (int t = 0; t < state.length; t++)
            {
                if (state[t] == 4)
                {
                    state[t] = 64;
                    break;
                }
            }

            new SHA224NativeDigest().restoreState(state, 0);
            fail("should fail on bufPtr value exceeding 64");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("invalid SHA224 encoded state"));
        }


        // Over length should fail
        try
        {
            // Check bufPtr limit test

            byte[] state = Arrays.clone(saneState);


            for (int t = 0; t < state.length; t++)
            {
                if (state[t] == 4)
                {
                    state[t] = 65;
                }
            }

            new SHA224NativeDigest().restoreFullState(state, 0);
            fail("should fail on bufPtr value exceeding 64");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("invalid SHA224 encoded state"));
        }

    }

    public void testRecreatingFromMemoable()
            throws Exception
    {

        if (skip())
        {
            return;
        }


        //
        // Generate the sane state, we need to do this as runtime as it may very because of alignment.
        //
        final byte[] saneState;

        SHA224NativeDigest dig = new SHA224NativeDigest()
        {
            {
                update((byte) 1);
                update((byte) 1);
                update((byte) 1);
                update((byte) 1);
            }
        };
        saneState = dig.getEncodedState();

        try
        {
            new SHA224NativeDigest().restoreState(null, 0);
            fail("accepted null");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("input was null"));
        }


        try
        {
            new SHA224NativeDigest().restoreState(new byte[saneState.length - 2], 0);
            fail("too short");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("array at offset too short for encoded input"));
        }


        try
        {
            // All zeroes.
            new SHA224NativeDigest().restoreFullState(new byte[saneState.length], 0);
            fail("bad id");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("invalid SHA224 encoded state"));
        }


        // At length should fail.
        try
        {
            // Check bufPtr limit test

            byte[] state = Arrays.clone(saneState);


            //
            // Our sane state has four bytes written to it, so both bufPtr and byteCount should be four.
            // Here we find every four in the state and set it to 64, because we cannot guarantee the position
            // within in the struct that the LSB of bufPtr will be.
            // This will enable us to assert that length checking of encoded bufPtr is correct.

            for (int t = 0; t < state.length; t++)
            {
                if (state[t] == 4)
                {
                    state[t] = 64;
                }
            }

            new SHA224NativeDigest().restoreState(state, 0);
            fail("should fail on bufPtr value exceeding 64");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("invalid SHA224 encoded state"));
        }


        // Over length should fail
        try
        {
            // Check bufPtr limit test

            byte[] state = Arrays.clone(saneState);


            for (int t = 0; t < state.length; t++)
            {
                if (state[t] == 4)
                {
                    state[t] = 65;
                }
            }

            new SHA224NativeDigest().restoreState(state, 0);
            fail("should fail on bufPtr value exceeding 64");
        }
        catch (Exception ex)
        {
            TestCase.assertTrue(ex.getMessage().contains("invalid SHA224 encoded state"));
        }




    }

    @Test
    public void testMemoable()
            throws Exception
    {
        if (skip())
        {
            return;
        }


        // There are other tests for memoable, this is more of a sanity test

        SHA224NativeDigest dig1 = new SHA224NativeDigest();
        dig1.update((byte) 1);

        SHA224NativeDigest dig2 = new SHA224NativeDigest(dig1);

        SHA224Digest jig1 = new SHA224Digest();
        jig1.update((byte) 1);

        byte[] r1 = new byte[dig1.getDigestSize()];
        byte[] r2 = new byte[dig2.getDigestSize()];
        byte[] j1 = new byte[jig1.getDigestSize()];

        TestCase.assertEquals(28, dig1.doFinal(r1, 0));
        TestCase.assertEquals(28,dig2.doFinal(r2, 0));
        TestCase.assertEquals(28, jig1.doFinal(j1, 0));

        TestCase.assertTrue(Arrays.areEqual(j1, r1));
        TestCase.assertTrue(Arrays.areEqual(j1, r2));

    }


}
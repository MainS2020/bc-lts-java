package org.bouncycastle.crypto.modes;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.ExceptionMessage;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.PacketCipherEngine;
import org.bouncycastle.crypto.PacketCipherException;
import org.bouncycastle.crypto.modes.gcm.GCMUtil;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Bytes;
import org.bouncycastle.util.Integers;
import org.bouncycastle.util.Longs;
import org.bouncycastle.util.Pack;

public class AESGCMSIVPacketCipher
    extends PacketCipherEngine
{
    public static AESGCMSIVPacketCipher newInstance()
    {
        return new AESGCMSIVPacketCipher();
    }

    private AESGCMSIVPacketCipher()
    {

    }

    /**
     * The nonce length.
     */
    private static final int NONCELEN = 12;
    /**
     * The halfBuffer length.
     */
    private static final int HALFBUFLEN = BLOCK_SIZE >> 1;
    /**
     * The maximum data length (AEAD/PlainText). Due to implementation constraints this is restricted to the maximum
     * array length (https://programming.guide/java/array-maximum-length.html) minus the BUFLEN to allow for the MAC
     */
    private static final int MAX_DATALEN = Integer.MAX_VALUE - 8 - BLOCK_SIZE;
    /**
     * The top bit mask.
     */
    private static final byte MASK = (byte)0x80;
    /**
     * The addition constant.
     */
    private static final byte ADD = (byte)0xE1;

    @Override
    public int getOutputSize(boolean encryption, CipherParameters parameters, int len)
    {
        if (len < 0)
        {
            throw new IllegalArgumentException(ExceptionMessage.LEN_NEGATIVE);
        }
        if (encryption)
        {
            return len + BLOCK_SIZE;
        }
        else if (len < BLOCK_SIZE)
        {
            throw new IllegalArgumentException(ExceptionMessage.OUTPUT_LENGTH);
        }
        return len - BLOCK_SIZE;
    }

    @Override
    public int processPacket(boolean encryption, CipherParameters parameters, byte[] input, int inOff, int len, byte[] output, int outOff)
        throws PacketCipherException
    {
        processPacketExceptionCheck(input, inOff, len, output, outOff);
        if (encryption)
        {
            if (len + outOff + BLOCK_SIZE > output.length)
            {
                throw PacketCipherException.from(new DataLengthException(ExceptionMessage.OUTPUT_LENGTH));
            }
        }
        else
        {
            if (len < BLOCK_SIZE)
            {
                throw PacketCipherException.from(new IllegalArgumentException(ExceptionMessage.INPUT_SHORT));
            }
            if (len + outOff - BLOCK_SIZE > output.length)
            {
                throw PacketCipherException.from(new DataLengthException(ExceptionMessage.OUTPUT_LENGTH));
            }
        }
        final byte[] theGHash = new byte[BLOCK_SIZE];
        final byte[] theReverse = new byte[BLOCK_SIZE];
        final GCMSIVHasher theAEADHasher;
        final GCMSIVHasher theDataHasher;
        boolean forEncryption;
        byte[] theInitialAEAD;
        byte[] theNonce;
        int KC, ROUNDS;
        int[][] workingKey;
        byte[] s;

        // defined fixed
        byte[] macBlock = new byte[BLOCK_SIZE];
        long[][] T = new long[256][2];
        byte[] H = new byte[BLOCK_SIZE];

        /* Create the hashers */
        theAEADHasher = new GCMSIVHasher();
        theDataHasher = new GCMSIVHasher();
        byte[] myInitialAEAD = null;
        byte[] myNonce;
        KeyParameter myKey;

        /* Access parameters */
        if (parameters instanceof AEADParameters)
        {
            final AEADParameters myAEAD = (AEADParameters)parameters;
            myInitialAEAD = myAEAD.getAssociatedText();
            myNonce = myAEAD.getNonce();
            myKey = myAEAD.getKey();
        }
        else if (parameters instanceof ParametersWithIV)
        {
            final ParametersWithIV myParms = (ParametersWithIV)parameters;
            myNonce = myParms.getIV();
            myKey = (KeyParameter)myParms.getParameters();
        }
        else
        {
            throw PacketCipherException.from(new IllegalArgumentException("invalid parameters passed to GCM-SIV"));
        }

        /* Check nonceSize */
        if (myNonce == null || myNonce.length != NONCELEN)
        {
            throw PacketCipherException.from(new IllegalArgumentException("Invalid nonce"));
        }

        /* Check keysize */
        if (myKey == null || (myKey.getKeyLength() != BLOCK_SIZE && myKey.getKeyLength() != (BLOCK_SIZE << 1)))
        {
            throw PacketCipherException.from(new IllegalArgumentException(ExceptionMessage.AES_KEY_LENGTH));
        }
        else
        {
            int keyLen = myKey.getKey().length;
            checkKeyLength(keyLen);
            KC = keyLen >>> 2;
            ROUNDS = KC + 6;  // This is not always true for the generalized Rijndael that allows larger block sizes
            workingKey = generateWorkingKey(myKey.getKey(), KC, ROUNDS);
            s = Arrays.clone(S);
        }

        /* Reset details */
        forEncryption = encryption;
        theInitialAEAD = myInitialAEAD;
        theNonce = myNonce;

        /* Initialise the keys */
        //deriveKeys(myKey);
        final byte[] myIn = new byte[BLOCK_SIZE];
        final byte[] myOut = new byte[BLOCK_SIZE];
        final byte[] myResult = new byte[BLOCK_SIZE];
        final byte[] myEncKey = new byte[myKey.getKeyLength()];

        /* Prepare for encryption */
        System.arraycopy(theNonce, 0, myIn, BLOCK_SIZE - NONCELEN, NONCELEN);

        /* Derive authentication key */
        int myOff = 0;
        encryptBlock(myIn, 0, myOut, 0, workingKey, s, ROUNDS);
        System.arraycopy(myOut, 0, myResult, myOff, HALFBUFLEN);
        myIn[0]++;
        myOff += HALFBUFLEN;
        encryptBlock(myIn, 0, myOut, 0, workingKey, s, ROUNDS);
        System.arraycopy(myOut, 0, myResult, myOff, HALFBUFLEN);

        /* Derive encryption key */
        myIn[0]++;
        myOff = 0;
        encryptBlock(myIn, 0, myOut, 0, workingKey, s, ROUNDS);

        System.arraycopy(myOut, 0, myEncKey, myOff, HALFBUFLEN);
        myIn[0]++;
        myOff += HALFBUFLEN;
        encryptBlock(myIn, 0, myOut, 0, workingKey, s, ROUNDS);

        System.arraycopy(myOut, 0, myEncKey, myOff, HALFBUFLEN);

        /* If we have a 32byte key */
        if (myEncKey.length == BLOCK_SIZE << 1)
        {
            /* Derive remainder of encryption key */
            myIn[0]++;
            myOff += HALFBUFLEN;
            encryptBlock(myIn, 0, myOut, 0, workingKey, s, ROUNDS);

            System.arraycopy(myOut, 0, myEncKey, myOff, HALFBUFLEN);
            myIn[0]++;
            myOff += HALFBUFLEN;
            encryptBlock(myIn, 0, myOut, 0, workingKey, s, ROUNDS);

            System.arraycopy(myOut, 0, myEncKey, myOff, HALFBUFLEN);
        }

        /* Initialise the Cipher */
        int keyLen = myEncKey.length;
        checkKeyLength(keyLen);
        KC = keyLen >>> 2;
        ROUNDS = KC + 6;  // This is not always true for the generalized Rijndael that allows larger block sizes
        workingKey = generateWorkingKey(myEncKey, KC, ROUNDS);

        /* Initialise the multiplier */
        fillReverse(myResult, 0, BLOCK_SIZE, myOut);
        mulX(myOut);
        GCMUtil.copy(myOut, H);

        // T[0] = 0
        // T[1] = H.p^7
        GCMUtil.asLongs(H, T[1]);
        GCMUtil.multiplyP7(T[1], T[1]);

        for (int n = 2; n < 256; n += 2)
        {
            // T[2.n] = T[n].p^-1
            GCMUtil.divideP(T[n >> 1], T[n]);
            // T[2.n + 1] = T[2.n] + T[1]
            GCMUtil.xor(T[n], T[1], T[n + 1]);
        }


        /* Reset hashers */
        theAEADHasher.reset();
        theDataHasher.reset();


        /* Initialise AEAD if required */
        Arrays.fill(theGHash, (byte)0);
        if (theInitialAEAD != null)
        {
            theAEADHasher.updateHash(theInitialAEAD, 0, theInitialAEAD.length, theReverse, theGHash, T);
        }

        /* Complete the AEAD section if this is the first data */
        theAEADHasher.completeHash(theReverse, theGHash, T);
        /* Make sure that we haven't breached data limit */
        long dataLimit = MAX_DATALEN;
        if (!forEncryption)
        {
            dataLimit += BLOCK_SIZE;
        }
        if ((long)len + Long.MIN_VALUE > (dataLimit - len) + Long.MIN_VALUE)
        {
            throw new IllegalStateException("byte count exceeded");
        }
        /* Check input buffer */
        /* Store the data */
        if (forEncryption)
        {
            theDataHasher.updateHash(input, inOff, len, theReverse, theGHash, T);
        }
        //doFinal

        /* If we are encrypting */
        if (forEncryption)
        {
            /* Derive the tag */
            final byte[] myTag = calculateTag(theDataHasher, theAEADHasher, theReverse, theGHash, T, theNonce, workingKey, s, ROUNDS);
            /* encrypt the plain text */
            final int myDataLen = BLOCK_SIZE + encryptPlain(input, inOff, len, myTag, output, outOff, workingKey, s, ROUNDS);
            /* Add the tag to the output */
            System.arraycopy(myTag, 0, output, outOff + len, BLOCK_SIZE);
            return myDataLen;
        }
        else
        {
            /* decrypt to plain text */
            decryptPlain(theDataHasher, theAEADHasher, input, inOff, len,
                output, outOff, theNonce, macBlock, theReverse, theGHash, T, workingKey, s, ROUNDS);
            return len - BLOCK_SIZE;
        }
    }

    /**
     * calculate tag.
     *
     * @return the calculated tag
     */
    private byte[] calculateTag(GCMSIVHasher theDataHasher, GCMSIVHasher theAEADHasher, byte[] theReverse,
                                byte[] theGHash, long[][] T, byte[] theNonce, int[][] workingKey, byte[] s, int ROUNDS)
    {
        /* Complete the hash */
        theDataHasher.completeHash(theReverse, theGHash, T);
        final byte[] myPolyVal = completePolyVal(theDataHasher, theAEADHasher, theGHash, T);

        /* calculate polyVal */
        final byte[] myResult = new byte[BLOCK_SIZE];

        /* Fold in the nonce */
        for (int i = 0; i < NONCELEN; i++)
        {
            myPolyVal[i] ^= theNonce[i];
        }

        /* Clear top bit */
        myPolyVal[BLOCK_SIZE - 1] &= (MASK - 1);

        /* Calculate tag and return it */
        encryptBlock(myPolyVal, 0, myResult, 0, workingKey, s, ROUNDS);
        //theCipher.processBlock(myPolyVal, 0, myResult, 0);
        return myResult;
    }

    /**
     * complete polyVAL.
     *
     * @return the calculated value
     */
    private byte[] completePolyVal(GCMSIVHasher theDataHasher, GCMSIVHasher theAEADHasher, byte[] theGHash, long[][] T)
    {
        /* Build the polyVal result */
        final byte[] myResult = new byte[BLOCK_SIZE];
        /* Create reversed bigEndian buffer to keep it simple */
        final byte[] myIn = new byte[BLOCK_SIZE];
        Pack.longToBigEndian(Bytes.SIZE * theDataHasher.getBytesProcessed(), myIn, 0);
        Pack.longToBigEndian(Bytes.SIZE * theAEADHasher.getBytesProcessed(), myIn, Longs.BYTES);

        /* hash value */
        gHASH(myIn, theGHash, T);
        fillReverse(theGHash, 0, BLOCK_SIZE, myResult);
        return myResult;
    }


    /**
     * encrypt data stream.
     *
     * @param pCounter the counter
     * @param pTarget  the target buffer
     * @param pOffset  the target offset
     * @return the length of data encrypted
     */
    private int encryptPlain(byte[] input, int inOff, int len, final byte[] pCounter, final byte[] pTarget, final int pOffset,
                             int[][] workingKey, byte[] s, int ROUNDS)
    {
        /* Access buffer and length */
        //final byte[] mySrc = thePlain.getBuffer();
        final byte[] myCounter = Arrays.clone(pCounter);
        myCounter[BLOCK_SIZE - 1] |= MASK;
        final byte[] myMask = new byte[BLOCK_SIZE];
        int myRemaining = len;//thePlain.size();
        int myOff = inOff;

        /* While we have data to process */
        while (myRemaining > 0)
        {
            /* Generate the next mask */
            encryptBlock(myCounter, 0, myMask, 0, workingKey, s, ROUNDS);
            /* Xor data into mask */
            final int myLen = Math.min(BLOCK_SIZE, myRemaining);
            xorBlock(myMask, input, myOff, myLen);
            /* Copy encrypted data to output */
            System.arraycopy(myMask, 0, pTarget, pOffset + myOff, myLen);
            /* Adjust counters */
            myRemaining -= myLen;
            myOff += myLen;
            incrementCounter(myCounter);
        }

        /* Return the amount of data processed */
        return len;
    }

    private class GCMSIVHasher
    {
        /**
         * Cache.
         */
        private final byte[] theBuffer = new byte[BLOCK_SIZE];

        /**
         * Count of active bytes in cache.
         */
        private int numActive;

        /**
         * Count of hashed bytes.
         */
        private long numHashed;

        /**
         * Obtain the count of bytes hashed.
         *
         * @return the count
         */
        long getBytesProcessed()
        {
            return numHashed;
        }

        /**
         * Reset the hasher.
         */
        void reset()
        {
            numActive = 0;
            numHashed = 0;
        }

        /**
         * update hash.
         *
         * @param pBuffer the buffer
         * @param pOffset the offset within the buffer
         * @param pLen    the length of data
         */
        void updateHash(final byte[] pBuffer,
                        final int pOffset,
                        final int pLen, byte[] theReverse, byte[] theGHash, long[][] T)
        {
            /* If we should process the cache */
            final int mySpace = BLOCK_SIZE - numActive;
            int numProcessed = 0;
            int myRemaining = pLen;
            if (numActive > 0
                && pLen >= mySpace)
            {
                /* Copy data into the cache and hash it */
                System.arraycopy(pBuffer, pOffset, theBuffer, numActive, mySpace);
                fillReverse(theBuffer, 0, BLOCK_SIZE, theReverse);
                gHASH(theReverse, theGHash, T);

                /* Adjust counters */
                numProcessed += mySpace;
                myRemaining -= mySpace;
                numActive = 0;
            }

            /* While we have full blocks */
            while (myRemaining >= BLOCK_SIZE)
            {
                /* Access the next data */
                fillReverse(pBuffer, pOffset + numProcessed, BLOCK_SIZE, theReverse);
                gHASH(theReverse, theGHash, T);

                /* Adjust counters */
                numProcessed += BLOCK_SIZE;
                myRemaining -= BLOCK_SIZE;
            }

            /* If we have remaining data */
            if (myRemaining > 0)
            {
                /* Copy data into the cache */
                System.arraycopy(pBuffer, pOffset + numProcessed, theBuffer, numActive, myRemaining);
                numActive += myRemaining;
            }

            /* Adjust the number of bytes processed */
            numHashed += pLen;
        }

        /**
         * complete hash.
         */
        void completeHash(byte[] theReverse, byte[] theGHash, long[][] T)
        {
            /* If we have remaining data */
            if (numActive > 0)
            {
                /* Access the next data */
                Arrays.fill(theReverse, (byte)0);
                fillReverse(theBuffer, 0, numActive, theReverse);

                /* hash value */
                gHASH(theReverse, theGHash, T);
            }
        }
    }

    private static void fillReverse(final byte[] pInput,
                                    final int pOffset,
                                    final int pLength,
                                    final byte[] pOutput)
    {
        /* Loop through the buffer */
        for (int i = 0, j = BLOCK_SIZE - 1; i < pLength; i++, j--)
        {
            /* Copy byte */
            pOutput[j] = pInput[pOffset + i];
        }
    }

    private void gHASH(final byte[] pNext, byte[] theGHash, long[][] T)
    {
        xorBlock(theGHash, pNext);
        multiplyH(theGHash, T);
    }

    /**
     * xor a full block buffer.
     *
     * @param pLeft  the left operand and result
     * @param pRight the right operand
     */
    private static void xorBlock(final byte[] pLeft,
                                 final byte[] pRight)
    {
        /* Loop through the bytes */
        for (int i = 0; i < BLOCK_SIZE; i++)
        {
            pLeft[i] ^= pRight[i];
        }
    }

    /**
     * xor a partial block buffer.
     *
     * @param pLeft   the left operand and result
     * @param pRight  the right operand
     * @param pOffset the offset in the right operand
     * @param pLength the length of data in the right operand
     */
    private static void xorBlock(final byte[] pLeft,
                                 final byte[] pRight,
                                 final int pOffset,
                                 final int pLength)
    {
        /* Loop through the bytes */
        for (int i = 0; i < pLength; i++)
        {
            pLeft[i] ^= pRight[i + pOffset];
        }
    }

    private void multiplyH(byte[] x, long[][] T)
    {
        long[] t = T[x[15] & 0xFF];
        long z0 = t[0], z1 = t[1];

        for (int i = 14; i >= 0; --i)
        {
            t = T[x[i] & 0xFF];

            long c = z1 << 56;
            z1 = t[1] ^ ((z1 >>> 8) | (z0 << 56));
            z0 = t[0] ^ (z0 >>> 8) ^ c ^ (c >>> 1) ^ (c >>> 2) ^ (c >>> 7);
        }

        Pack.longToBigEndian(z0, x, 0);
        Pack.longToBigEndian(z1, x, 8);
    }

    /**
     * multiply by X.
     *
     * @param pValue the value to adjust
     */
    private static void mulX(final byte[] pValue)
    {
        /* Loop through the bytes */
        byte myMask = (byte)0;
        for (int i = 0; i < BLOCK_SIZE; i++)
        {
            final byte myValue = pValue[i];
            pValue[i] = (byte)(((myValue >> 1) & ~MASK) | myMask);
            myMask = (myValue & 1) == 0 ? 0 : MASK;
        }

        /* Xor in addition if last bit was set */
        if (myMask != 0)
        {
            pValue[0] ^= ADD;
        }
    }

    /**
     * decrypt data stream.
     *
     */
    private void decryptPlain(GCMSIVHasher theDataHasher, GCMSIVHasher theAEADHasher, byte[] input, int inOff, int len,
                              byte[] output, int outOff, byte[] theNonce, byte[] macBlock, byte[] theReverse, byte[] theGHash,
                              long[][] T, int[][] workingKey, byte[] s, int ROUNDS)
        throws PacketCipherException
    {
        int myRemaining = len - BLOCK_SIZE;
        /* Access counter */
        final byte[] myExpected = Arrays.copyOfRange(input, myRemaining, myRemaining + BLOCK_SIZE);
        final byte[] myCounter = Arrays.clone(myExpected);
        myCounter[BLOCK_SIZE - 1] |= MASK;
        final byte[] myMask = new byte[BLOCK_SIZE];
        int myOff = inOff;

        /* While we have data to process */
        while (myRemaining > 0)
        {
            /* Generate the next mask */
            //theCipher.processBlock(myCounter, 0, myMask, 0);
            encryptBlock(myCounter, 0, myMask, 0, workingKey, s, ROUNDS);

            /* Xor data into mask */
            final int myLen = Math.min(BLOCK_SIZE, myRemaining);
            xorBlock(myMask, input, myOff, myLen);

            /* Write data to plain dataStream */
            //thePlain.write(myMask, 0, myLen);
            System.arraycopy(myMask, 0, output, outOff, myLen);
            theDataHasher.updateHash(myMask, 0, myLen, theReverse, theGHash, T);

            /* Adjust counters */
            myRemaining -= myLen;
            myOff += myLen;
            outOff += myLen;
            incrementCounter(myCounter);
        }

        /* Derive and check the tag */
        final byte[] myTag = calculateTag(theDataHasher, theAEADHasher, theReverse, theGHash, T, theNonce, workingKey, s, ROUNDS);
        if (!Arrays.constantTimeAreEqual(myTag, myExpected))
        {
            //reset();
            throw PacketCipherException.from(new InvalidCipherTextException("mac check failed"));
        }

        System.arraycopy(myTag, 0, macBlock, 0, macBlock.length);
    }

    /**
     * increment the counter.
     *
     * @param pCounter the counter to increment
     */
    private static void incrementCounter(final byte[] pCounter)
    {
        /* Loop through the bytes incrementing counter */
        for (int i = 0; i < Integers.BYTES; i++)
        {
            if (++pCounter[i] != 0)
            {
                break;
            }
        }
    }
}

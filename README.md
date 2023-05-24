# The Bouncy Castle Crypto Package For Java LTS


# Native support

## Create jar with native libraries

Before creating the jar the native libraries will need to be built. This will need to be done on each target platform
specifically.

### Building on linux

Before building the native code components, you will need to have the following installed:

Linux:

- build essentials
- cmake
- nasm
- gcc

Windows:

- TBD

#### Process:

##### Step 1 Create native headers

The native headers need to be created first before the native library can be built,
these are generated by the JVM so that JNI calls can be implemented with the correct
signatures on the native side.

Change into the ```<base_dir>/``` directory and run:

```
./gradlew clean cleanNative prov:build util:build pg:build pkix:build tls:build mail:build -x test

# Run the tests later
```

This will compile the java code and build the headers, the tests will be run during Step 3.

##### Step 2 Build native lib

Change into the ```<base_dir>/native``` directory.

```
# For linux
./build_linux.sh

```

This will create the native libraries and install it into the correct location for them to be
bundled into the LTS jar.

##### Step 3 Build LTS jar

###### Java only version

Change into the ```<base_dir>/``` directory.

```
./gradlew clean cleanNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars
```

This will build a java only variation and install it in the ```jars``` directory. The name of the jar is defined with
the ```version=2.73.0-SNAPSHOT``` version property in ```gradle.properties```.

Running the DumpInfo class from the jar will report something similar to:

```
java -cp jars/bcprov-lts8on-2.73.0-SNAPSHOT.jar org.bouncycastle.util.DumpInfo
        
BouncyCastle APIs (LTS edition) v1.0.0b
Native Status: probe lib failed to load /native/linux/x86_64/probe/libbc-probe.so lib not found in jar
Native Features: [NONE]


# or the vebose version

```

NB: ```probe lib failed to load /native/linux/x86_64/probe/libbc-probe.so lib not found in jar``` means there
were no native libraries in the jar file.

###### Java with native capabilities build

Before doing this, please make sure you have built the native capabilities, see Step 2, above.

Building a jar with native capabilities exercising only the java core tests, running the core tests against native
capabilities is covered next.

```
  ./gradlew clean cleanNative withNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars

# If you need to skip the tests

./gradlew clean cleanNative withNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars -x test
```

After building a jar with the native libraries bundled in you can veryify they are getting loaded by
doing the following on a modern Intel CPU.

```
#Running:
java -cp jars/bcprov-lts8on-2.73.0-SNAPSHOT.jar org.bouncycastle.util.DumpInfo

BouncyCastle APIs (LTS edition) v1.0.0b
Native Status: successfully loaded
Native Variant: vaesf
Native Build Date: 2022-12-23T14:24:25Z
Native Features: [SHA2, DRBG, AES/CFB, AES/GCM, NRBG, AES/ECB, AES/CBC]


```

###### Building and or testing native capabilities

Use the following test targets to exercise the tests while using specific sets of native components
compiled for certain CPU features.

| Variant Name | Family | Requirements                                             |
|--------------|--------|----------------------------------------------------------|
| sse          | x86_64 | sse4.1 sha aes pclmul rdrnd rdseed                       |
| avx          | x86_64 | avx sha aes pclmul rdrnd -rdseed lzcnt                   |
| vaes         | x86_64 | avx sha aes pclmul rdrnd rdseed lzcnt vaes avx2          |
| vaesf        | x86_64 | avx sha aes pclmul rdrnd rdseed lzcnt vaes avx2 avx512f  |

Attempting to test a variant on a CPU without matching hardware features will cause a fault.
```

# Using testXXX will run the java tests but with the native componnents swapped in
# where appropriate. We don't need to rerun the java only tests again hence the '-x test'

./gradlew clean cleanNative withNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars testSSE -x test
./gradlew clean cleanNative withNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars testAVX -x test
./gradlew clean cleanNative withNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars testVAES -x test
./gradlew clean cleanNative withNative prov:build util:build pg:build pkix:build tls:build mail:build copyJars testVAESF -x test

```

## Using the module

The module can be used like any other jar file, it will manage the installation of native libraries into
a temporary directory created in the same path as ```File.createTempFile()```.

```
java -cp jars/bcprov-lts8on-2.73.0-SNAPSHOT.jar org.bouncycastle.util.DumpInfo

BouncyCastle APIs (LTS edition) v1.0.0b
Native Status: successfully loaded
Native Variant: vaesf
Native Build Date: 2022-12-23T14:24:25Z
Native Features: [SHA2, DRBG, AES/CFB, AES/GCM, NRBG, AES/ECB, AES/CBC]

```

## Selecting a specific variant

The library includes a probe library that examines CPU features and advises the module which
native variation is the appropriate version to load.

To override this use the ```-Dorg.bouncycastle.native.cpu_variant``` at the time of invocation or
this value can be set within the security policy as well.

For example, using -Dorg.bouncycastle.native.cpu_variant=sse:

```
java -cp jars/bcprov-lts8on-2.73.0-SNAPSHOT.jar -Dorg.bouncycastle.native.cpu_variant=sse  org.bouncycastle.util.DumpInfo

BouncyCastle APIs (LTS edition) v1.0.0b
Native Status: successfully loaded
Native Variant: sse
Native Build Date: 2022-12-23T14:24:25Z
Native Features: [SHA2, DRBG, AES/CFB, AES/GCM, NRBG, AES/ECB, AES/CBC]

```

NB: Forcing a variant that is compiled to use instructions not supported by the host CPU will
case a fault and terminate the program. And you won't know until the JVM either errors or causes
a segfault.

## Optimisation status

Some feature set optimisations, VAES and VAES with AFX512F


[![Build Status](https://travis-ci.org/bcgit/bc-java.svg?branch=master)](https://travis-ci.org/bcgit/bc-java)

The Bouncy Castle Crypto package is a Java implementation of cryptographic algorithms, it was developed by the Legion of the Bouncy Castle, a registered Australian Charity, with a little help! The Legion, and the latest goings on with this package, can be found at [https://www.bouncycastle.org](https://www.bouncycastle.org).

The Legion also gratefully acknowledges the contributions made to this package by others (see [here](https://www.bouncycastle.org/contributors.html) for the current list). If you would like to contribute to our efforts please feel free to get in touch with us or visit our [donations page](https://www.bouncycastle.org/donate), sponsor some specific work, or purchase a support contract through [Crypto Workshop](https://www.cryptoworkshop.com).

The package is organised so that it contains a light-weight API suitable for use in any environment (including the newly released J2ME) with the additional infrastructure to conform the algorithms to the JCE framework.

Except where otherwise stated, this software is distributed under a license based on the MIT X Consortium license. To view the license, [see here](https://www.bouncycastle.org/licence.html). The OpenPGP library also includes a modified BZIP2 library which is licensed under the [Apache Software License, Version 2.0](https://www.apache.org/licenses/).

**Note**: this source tree is not the FIPS version of the APIs - if you are interested in our FIPS version please contact us directly at  [office@bouncycastle.org](mailto:office@bouncycastle.org).

## Code Organisation

The clean room JCE, for use with JDK 1.1 to JDK 1.3 is in the jce/src/main/java directory. From JDK 1.4 and later the JCE ships with the JVM, the source for later JDKs follows the progress that was made in the later versions of the JCE. If you are using a later version of the JDK which comes with a JCE install please **do not** include the jce directory as a source file as it will clash with the JCE API installed with your JDK.

The **core** module provides all the functionality in the ligthweight APIs.

The **prov** module provides all the JCA/JCE provider functionality.

The **util** module is the home for code which is used by other modules that does not need to be in prov. At the moment this is largely ASN.1 classes for the PKIX module.

The **pkix** module is the home for code for X.509 certificate generation and the APIs for standards that rely on ASN.1 such
as CMS, TSP, PKCS#12, OCSP, CRMF, and CMP.

The **mail** module provides an S/MIME API built on top of CMS.

The **pg** module is the home for code used to support OpenPGP.

The **tls** module is the home for code used to a general TLS API and JSSE Provider.

The build scripts that come with the full distribution allow creation of the different releases by using the different source trees while excluding classes that are not appropriate and copying in the required compatibility classes from the directories containing compatibility classes appropriate for the distribution.

If you want to try create a build for yourself, using your own environment, the best way to do it is to start with the build for the distribution you are interested in, make sure that builds, and then modify your build scripts to do the required exclusions and file copies for your setup, otherwise you are likely to get class not found exceptions. The final caveat to this is that as the j2me distribution includes some compatibility classes starting in the java package, you need to use an obfuscator to change the package names before attempting to import a midlet using the BC API.


## Examples and Tests

To view some examples, look at the test programs in the packages:

*   **org.bouncycastle.crypto.test**

*   **org.bouncycastle.jce.provider.test**

*   **org.bouncycastle.cms.test**

*   **org.bouncycastle.mail.smime.test**

*   **org.bouncycastle.openpgp.test**

*   **org.bouncycastle.tsp.test**

There are also some specific example programs for dealing with SMIME and OpenPGP. They can be found in:

*   **org.bouncycastle.mail.smime.examples**

*   **org.bouncycastle.openpgp.examples**

## Mailing Lists

For those who are interested, there are 2 mailing lists for participation in this project. To subscribe use the links below and include the word subscribe in the message body. (To unsubscribe, replace **subscribe** with **unsubscribe** in the message body)

*   [announce-crypto-request@bouncycastle.org](mailto:announce-crypto-request@bouncycastle.org)  
    This mailing list is for new release announcements only, general subscribers cannot post to it.
*   [dev-crypto-request@bouncycastle.org](mailto:dev-crypto-request@bouncycastle.org)  
    This mailing list is for discussion of development of the package. This includes bugs, comments, requests for enhancements, questions about use or operation.

**NOTE:** You need to be subscribed to send mail to the above mailing list.

## Feedback and Contributions

If you want to provide feedback directly to the members of **The Legion** then please use [feedback-crypto@bouncycastle.org](mailto:feedback-crypto@bouncycastle.org), if you want to help this project survive please consider [donating](https://www.bouncycastle.org/donate).

For bug reporting/requests you can report issues here on github, or via feedback-crypto if required. We will accept pull requests based on this repository as well, but only on the basis that any code included may be distributed under the [Bouncy Castle License](https://www.bouncycastle.org/licence.html).

## Finally

Enjoy!

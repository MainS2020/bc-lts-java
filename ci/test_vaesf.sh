#!/bin/bash

set -e

#
# This script is for running inside the docker container
#

cd /workspace/bc-lts-java
source ci/common.sh


export JAVA_HOME=`openjdk_11`
export PATH=$JAVA_HOME/bin:$PATH


./gradlew clean compileJava
(cd native_c; ./build_linux.sh;)
./gradlew clean cleanNative withNative build -x test

java -Dorg.bouncycastle.native.cpu_variant=vaesf -cp prov/build/libs/bcprov-lts8on-`./version.sh`.jar org.bouncycastle.util.DumpInfo

./gradlew -Pskip.pqc.tests testVAESF -x test

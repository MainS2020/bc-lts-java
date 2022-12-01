#!/usr/bin/env bash

version="1.0.0-SNAPSHOT"

rm -rf mtest
mkdir mtest

artifactsHome=build/libs/

tj=( $artifactsHome/*tests.jar )

testJar="${tj[0]}";

prefixes=("bcprov-lts8on" "bcpkix-lts8on" "bctls-lts8on" "bcutil-lts8on" "bcpg-lts8on" "bcmail-lts8on")

for str in "${prefixes[@]}"; do
  src="jars/${str}-${version}.jar"
  echo $src
  cp "${src}" mtest/
done

a=(`$JAVA_HOME/bin/jar -tf "$testJar" | grep -E "AllTests\.class" | sed -e 's!.class!!' | sed -e 's|/|.|g'`);

rm -rf /tmp/bc-libs
mkdir /tmp/bc-libs

export DYLIB_LIBRARY_PATH=/tmp/bc-libs
export LD_LIBRARY_PATH=/tmp/bc-libs

java  \
  -cp "$testJar:mtest/*:libs/junit.jar:libs/activation.jar:libs/mail.jar" \
  org.bouncycastle.util.DumpInfo



for i in "${a[@]}"
do
#  case $i in org\.bouncycaslte\.crypto\.engines\.*)
#    echo "skipping $i"
#    continue
#    esac

  echo $i

  java  \
  -cp "$testJar:mtest/*:libs/junit.jar:libs/activation.jar:libs/mail.jar" \
  -Dbc.test.data.home=core/src/test/data \
  "$i"

done

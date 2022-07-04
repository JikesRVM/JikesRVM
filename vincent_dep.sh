#!/bin/bash

#download dacapo-9.12-bach.jar
wget https://sourceforge.net/projects/dacapobench/files/archive/9.12-bach/dacapo-9.12-bach.jar/download
mv download dacapo-9.12-bach.jar


#TODO install java 6 and store in /home see kenan/compile.sh
#get java 6 for kenan dir the flags are required due to oracles OTN liscense terms
# wget --no-check-certificate -c --header "Cookie: oraclelicense=accept-securebackup-cookie" https://download.oracle.com/otn/java/jdk/6u45-b06/jdk-6u45-linux-i586.bin
# mv jdk-6u45-linux-i586.bin /home/spoonjoon/
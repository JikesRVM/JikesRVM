#! /bin/bash
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Eclipse Public License (EPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/eclipse-1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

# A script illustrating how the benchmarks can be run from the command
# line without the GUI. This is not the way they can be run for reportable
# results when the suite is released. And this script is quite contrary
# to the spirit of Java, UNIX shell scripts being non-portable
# to Windows 95 and MacOS for example. However, people at several
# companies seem to be running the benchmarks this way because it's
# easier to integrate with existing performance characterization tools.
# So at least it should be documented by example. Feel free to modify
# for your own needs.

#######################################
# Define benchmark groups

all='
	_200_check
	_201_compress
	_202_jess
	_209_db
	_213_javac
	_222_mpegaudio
	_227_mtrt
	_228_jack
	'
all='_200_check _202_jess'

#######################################
# Select options for java and for the benchmarks
# Usage: java [JVMoptions] SpecApplication [options] [_NNN_benchmark]

jvmoptions="	-ms16m				$jvmoptions"
jvmoptions="	-mx32m				$jvmoptions"
#jvmoptions="	-verbosegc			$jvmoptions"
#jvmoptions="	-Djava.compiler=		$jvmoptions"
#jvmoptions="	-noasyncgc			$jvmoptions"

#Perform autorun sequence on a single selected benchmark
options="	-a		$options"
#Delay <number> milliseconds between autorun executions
options="	-d3000		$options"
#Garbage collect in between autorun executions
options="	-g		$options"
#Set minimum number of executions in autorun sequence
options="	-m2		$options"
#Set maximum number of executions in autorun sequence
options="	-M4		$options"
#Turn on file input caching
#options="	-n		$options"
#Set thread priority to <number>
#options="	-p<number>	$options"
#Set problem size to <number>, 1, 10, or 100
options="	-s1		$options"
#Run using threads
#options="	-t		$options"
#Specify <filename> property file instead of props/user
#options="	-uprops/userme	$options"

# If you want verbose output of command executions...
set -x

#######################################
# Finally, run the benchmarks. Three methods are shown below

# This example runs all in the same JVM under the SPEC tool harness.
# This is least convenient for data collection tools but complies with
# the SPEC run rules. The benchmark set and SPEC options are controlled
# by the property files, not from the command line
#export DISPLAY=wherever_you_can_display_windows
#java $jvmoptions SpecApplication -b
#exit

# This example runs all in the same JVM without the GUI tool harness.
# This is somewhat more convenient than the above for running data
# collection tools but does not follow the SPEC compliant methodology
#java $jvmoptions SpecApplication $options $all
#exit

# This example runs each in a separate JVM without the GUI tool
# harness.  This is usually most convenient for running data collection
# tools, but farthest from the SPEC run rules.
for benchmark in $all
do
	java $jvmoptions SpecApplication $options $benchmark
done

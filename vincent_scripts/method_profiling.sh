#!/bin/bash

DEBUG=false
freqOpt=8
pbench=$1
ptype=$2
iters="$3"
samples="$4"
frequency="$5"
expected=$iters

if [ "$ptype" == "old" ];
then
	dacapoJar="dacapo-2006-10-MR2.jar"
	callbackClass="kenan.OIterationCallBack"
	expected=$((iters))
else
	callbackClass="kenan.IterationCallBack"
	dacapoJar="dacapo-9.12-bach.jar"
fi	

#callbackClass="kenan.IterationCallBack"
echo "Number of expected lines in iteration_times is $expected"
bench="$pbench"
size="default"

#TODO: get available freqs from cpufreq
freq=('0' '2201000' '2200000' '2100000' '2000000' '1900000' '1800000' '1700000' '1600000' '1500000' '1400000' '1300000' '1200000')
events=('cache-misses' 'cache-references' 'cpu-cycles' 'branches' 'branch-misses' 'cpu-clock' 'page-faults' 'context-switches' 'cpu-migrations');
timeSlice=('0' '8' '4' '2' '1') 

runJikesProfile() {
		echo $1 $2 $3 $4 $5
		sudo dist/FullAdaptiveMarkSweep_x86_64-linux/rvm  "-Xmx4000M" "-X:kenan:frequency=$frequency" "-X:kenan:samples=$samples"  "-X:vm:errorsFatal=true" "-X:gc:printPhaseStats=true" "-X:vm:interruptQuantum=${4}" "-X:aos:enable_recompilation=true" "-X:aos:hot_method_time_min=0.1" "-X:aos:hot_method_time_max=1"  "-X:aos:frequency_to_be_printed=${2}" "-X:aos:eventcounter=${3}" "-X:aos:enable_counter_profiling=false" "-X:aos:enable_energy_profiling=true" "-X:aos:profiler_file=doubleSampleWindow_1ms.csv" "-X:aos:enable_scaling_by_counters=false" "-X:aos:enable_counter_printer=true" "-cp" "$dacapoJar:." "Harness" "-s" "$size" "-n" "${iters}" "-c" "$callbackClass"  "$bench" &> freq_${kkfreq}
}

kkfreq=0
i=0
timeSlice=$((${timeSlice}))		
if [ -f kenan.csv ];
then
	rm kenan.csv
fi

#set governor to ondemand
sudo java energy.Scaler 1 ondemand

itercount="0"
while [ "$itercount"!="$iters" ]
do
	runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} 4 Energy -t 8 
	itercount=$(wc -l iteration_times)
	itercount=$(echo $itercount | cut -d' ' -f 1)
	echo "iterations: $itercount, Expected: $iters" 
done


# TODO rename file to be less confusing
mv iteration_times counter_based_sampling_iteration_times_$i
mv kenan.csv counter_based_sampling_kenan.${i}.csv
mv kenan_energy kenan_energy_$i 
mv execution_time execution_time_${i}.csv

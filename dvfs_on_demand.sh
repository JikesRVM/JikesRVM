#!/bin/bash
DEBUG=false
freqOpt=8
pbench=$1
ptype=$2
iters="$3"
ifreq="$4"
expected=$iters
mname=$5
ssn=$6
samplefreq=$7
if [ "$ptype" == "old" ];
then
	dacapoJar="dacapo-2006-10-MR2.jar"
	callbackClass="kenan.OIterationCallBack"
else

	callbackClass="kenan.IterationCallBack"
	dacapoJar="dacapo-9.12-bach.jar"
fi	

#callbackClass="kenan.IterationCallBack"
bench="$pbench"
size="default"

freq=('0' '2201000' '2200000' '2100000' '2000000' '1900000' '1800000' '1700000' '1600000' '1500000' '1400000' '1300000' '1200000')
#freq=('0' '2601000' '2400000' '2200000' '2000000' '1800000' '1600000' '1400000' '1200000' '2600000');
events=('cache-misses' 'cache-references' 'cpu-cycles' 'branches' 'branch-misses' 'cpu-clock' 'page-faults' 'context-switches' 'cpu-migrations');
timeSlice=('0' '8.0' '4.0' '2.0' '1.0' '0.5' '0.25' '0.125')
hotMin=('0' '50' '100' '150' '200' '250' '300' '350' '400')
hotMax=('0' '100' '150' '200' '250' '300' '350' '400' '1000000')
threads=('2' '4' '8')
eventNum=8
freqScaling=1

#cache-misses,cache-references
#It was 2/32
runJikesProfile() {
		sudo dist/FullAdaptiveMarkSweep_x86_64-linux/rvm  "-Xmx4000M" "-X:kenan:frequency=$samplefreq" "-X:kenan:samples=$ssn" "-X:gc:eagerMmapSpaces=true"  "-X:vm:errorsFatal=true" "-X:gc:printPhaseStats=true" "-X:vm:interruptQuantum=${4}" "-X:aos:enable_recompilation=true" "-X:aos:hot_method_time_min=0.1" "-X:aos:hot_method_time_max=1" "-X:aos:dvfs_class_method_name=$mname" "-X:aos:frequency_to_be_printed=${2}" "-X:aos:event_counter=${3}" "-X:aos:enable_counter_profiling=false" "-X:aos:enable_energy_profiling=false" "-X:aos:profiler_file=doubleSampleWindow_1ms.csv" "-X:aos:enable_scaling_by_counters=false" "-X:aos:enable_counter_printer=false" "-cp" "$dacapoJar:." "Harness" "-s" "$size" "-n" "${iters}" "-c" "$callbackClass"  "$bench" &> freq_${kkfreq}
	}

kkfreq="$ifreq"
timeSlice=$((${timeSlice}))		
sudo java energy.Scaler 1 ondemand
runJikesProfile 4 ${freq[$kkfreq]} ${events[0]},${events[1]} ${timeSlice[2]} Energy -t 8 

cp kenan_energy "kenan_energy_${bench}_${ifreq}_${mname}"
cp execution_time "execution_time_${bench}_${ifreq}_${mname}"
cp "freq_$kkfreq" "freq_${bench}_${ifreq}_${mname}"
cp "iteration_times" "iteration_times_${bench}_${ifreq}_${mname}"
cp "iteration_energy" "iteration_energy_${bench}_${ifreq}_${mname}"
killall JikesRVM

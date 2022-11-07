#!/bin/bash
pbench=$1
ptype=$2
iters="$3"
expected=$iters

echo "$1-$2-$3-$4-$5"

if [ "$ptype" == "old" ];
then
	dacapoJar="dacapo-2006-10-MR2.jar"
	callbackClass="kenan.OIterationCallBack"
	expected=$((iters))
else

	callbackClass="kenan.IterationCallBack"
	dacapoJar="dacapo-9.12-bach.jar"
fi	

bench="$pbench"
size="default"

freq=('0' '2201000' '2200000' '2100000' '2000000' '1900000' '1800000' '1700000' '1600000' '1500000' '1400000' '1300000' '1200000')
events=('cache-misses' 'cache-references' 'cpu-cycles' 'branches' 'branch-misses' 'cpu-clock' 'page-faults' 'context-switches' 'cpu-migrations');
timeSlice=('0' '8.0' '4.0' '2.0' '1.0' '0.5' '0.25' '0.125')
hotMin=('0' '50' '100' '150' '200' '250' '300' '350' '400')
hotMax=('0' '100' '150' '200' '250' '300' '350' '400' '1000000')
threads=('2' '4' '8')

kkfreq="0"
#cache-misses,cache-references

runJikesProfile() {
		sudo dist/FullAdaptiveMarkSweep_x86_64-linux/rvm  "-Xmx4000M" "-X:vm:errorsFatal=true" "-X:gc:printPhaseStats=true" "-X:vm:interruptQuantum=${4}"  "-X:aos:enable_counter_profiling=false" "-X:aos:enable_energy_profiling=false" "-X:aos:enable_scaling_by_counters=false" "-X:aos:enable_counter_printer=true" "-cp" "$dacapoJar:." "Harness" "-s" "$size" "-n" "${iters}" "-c" "$callbackClass"  "$bench"
}

for((i=1;i<=12;i++))
do
	# edit to according governor
	sudo java energy.Scaler $i ondemand
	runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} ${timeSlice[2]} Energy -t 4 
	
	mv execution_time "${pbench}_${i}_execution_time"
	mv kenan_energy "${pbench}_${i}_kenan_energy"
	mv iteration_times "${pbench}_${i}_iteration_times"
	mv iteration_energy "${pbench}_${i}_iteration_energy"
done


# sudo java energy.Scaler 1 performance
# i=0
# runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} ${timeSlice[2]} Energy -t 8 
# mv execution_time "${pbench}_${i}_execution_time"
# mv kenan_energy "${pbench}_${i}_kenan_energy"
# mv iteration_times "${pbench}_${i}_iteration_times"
# mv iteration_energy "${pbench}_${i}_iteration_energy"

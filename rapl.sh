#!/bin/bash
DEBUG=false
pbench=$1
ptype=$2
iters="$3"
samples="$4"
samplesorg="$4"
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
bench="$pbench"
size="default"
freqOpt=8
freq=('0' '2201000' '2200000' '2100000' '2000000' '1900000' '1800000' '1700000' '1600000' '1500000' '1400000' '1300000' '1200000')
events=('cache-misses' 'cache-references' 'cpu-cycles' 'branches' 'branch-misses' 'cpu-clock' 'page-faults' 'context-switches' 'cpu-migrations');
timeSlice=('0' '8.0' '4' '2.0' '1.0' '0.5' '0.25' '0.125')
hotMin=('0' '50' '100' '150' '200' '250' '300' '350' '400')
hotMax=('0' '100' '150' '200' '250' '300' '350' '400' '1000000')
threads=('2' '4' '8')
eventNum=8
freqScaling=1
kkfreq="0"

runJikesProfile() {
		sudo dist/FullAdaptiveMarkSweep_x86_64-linux/rvm  "-Xmx4000M" "-X:vm:errorsFatal=true" "-X:gc:printPhaseStats=true" "-X:vm:interruptQuantum=${4}" "-X:aos:enable_recompilation=true" "-X:aos:hot_method_time_min=0.1" "-X:aos:hot_method_time_max=1" "-X:kenan:frequency=$frequency" "-X:kenan:samples=$samples"  "-X:aos:frequency_to_be_printed=${2}" "-X:aos:eventcounter=${3}" "-X:aos:enable_counter_profiling=false" "-X:aos:enable_energy_profiling=true" "-X:aos:profiler_file=doubleSampleWindow_1ms.csv" "-X:aos:enable_scaling_by_counters=false" "-X:aos:enable_counter_printer=true" "-cp" "$dacapoJar:." "Harness" "-s" "$size" "-n" "${iters}" "-c" "$callbackClass"  "$bench" &> freq_${kkfreq}
}

echo "$1-$2-$3-$4-$5"
if [ -f kenan.csv ];
then
	sudo rm kenan.csv
fi

timeSlice=$((${timeSlice}))		
#
for((i=1;i<=12;i++))
do
        kkfreq="$i"
       	if [ "$samples" = "0" ]
       	then
       		samples="1"
       	fi
       

       repeat="true"	
       while [ "$repeat" = "true" ]
       do
		
	        killall java
	        echo "Frequency $i, Samples $samples, SampleFreq $frequency"
       		sudo java energy.Scaler $i userspace
       		runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} ${timeSlice[2]} Energy -t 4 
       		sudo mv kenan.csv counter_based_sampling_kenan.${i}.csv
       		sudo mv iteration_times counter_based_sampling_iteration_times_$i
		sudo mv kenan_energy kenan_energy_$i
		sudo mv execution_time execution_time_$i
		echo "Iter count: $itercount, expected: $expected"
		itercount=$(wc -l counter_based_sampling_iteration_times_$i)
		itercount=$(echo $itercount | cut -d' ' -f 1)
		if [ "$itercount" = "$expected" ]
		then
		    repeat="false"
		else
		    
			##The else srhould not be needed anymore
			repeat="false"
			mem=$(grep malloc freq_$i)
			if [ "$mem" = "" ]
			then
				echo "No Malloc"
			else
				samples=$((samples/2))			
			fi
			rm -r scratch
			if [ "$samples" = "0" ]
			then
				repeat="false"
			fi
			
			killall JikesRVM
			killall java
		fi
	done

done
##	
sleep 10 

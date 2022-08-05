#!/bin/bash

## takes two parameters ($1) the benchmark and number of iters ($2)

DEBUG=false
freqOpt=8
# name of the benchmark
pbench=$1
# version of dacapo
ptype=new
# # of iterations
iters="$3"
# set by khaled ran 8 for sunflow 32 for all the rest
samples="$4"
# set by khaled
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

echo "Number of expected lines in iteration_times is $expected"

bench="$pbench"
size="default"
freq=('0' '2201000' '2200000' '2100000' '2000000' '1900000' '1800000' '1700000' '1600000' '1500000' '1400000' '1300000' '1200000')
events=('cache-misses' 'cache-references' 'cpu-cycles' 'branches' 'branch-misses' 'cpu-clock' 'page-faults' 'context-switches' 'cpu-migrations');
timeSlice=('0' '8.0' '4.0' '2.0' '1.0' '0.5' '0.25' '0.125')
threads=('2' '4' '8')
eventNum=8
freqScaling=1

if [ -f kenan.csv ];
then
	rm kenan.csv
fi

kkfreq=0
i=0
timeSlice=$((${timeSlice}))		
repeat="true"

# removing eagerMmapSpaces
runJikesProfile() {
    echo $1 $2 $3 $4 $5
	sudo dist/FullAdaptiveMarkSweep_x86_64-linux/rvm  "-Xmx4000M" "-X:kenan:frequency=$frequency" "-X:kenan:samples=$samples" "-X:vm:errorsFatal=true" "-X:gc:printPhaseStats=true" "-X:vm:interruptQuantum=${4}" "-X:aos:enable_recompilation=true" "-X:aos:hot_method_time_min=0.1" "-X:aos:hot_method_time_max=1"  "-X:aos:frequency_to_be_printed=${2}" "-X:aos:eventcounter=${3}" "-X:aos:enable_counter_profiling=false" "-X:aos:enable_energy_profiling=true" "-X:aos:profiler_file=doubleSampleWindow_1ms.csv" "-X:aos:enable_scaling_by_counters=false" "-X:aos:enable_counter_printer=true" "-cp" "$dacapoJar:." "Harness" "-s" "$size" "-n" "${iters}" "-c" "$callbackClass"  "$bench" &> freq_${kkfreq}
}


# # main body of scripts
# sudo java energy.Scaler 1 ondemand
# #runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} ${timeSlice[2]} Energy -t 8 
# runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} 4 Energy -t 8 


# itercount=$(wc -l iteration_times)
# itercount=$(echo $itercount | cut -d' ' -f 1)

# sudo mv iteration_times counter_based_sampling_iteration_times_$i
# sudo mv kenan.csv counter_based_sampling_kenan.${i}.csv
# #sudo mv kenan_energy_$i
# sudo mv execution_time execution_time_${i}.csv


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
       		runJikesProfile 4 ${freq[$i]} ${events[0]},${events[1]} 4 Energy -t 4 
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
		    
			##The else should not be needed anymore
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
expname=$6
benchname=$1
benchdir="kenan_${benchname}"
echo "$benchdir"

#bash rapl_on_demand.sh $1 $2 $3 $4 $5
if [ -d $benchdir ]
then
	rm -r $benchdir
fi

mkdir $benchdir
mv counter_based* $benchdir/
mv freq_* $benchdir/
mv kenan_energy* $benchdir
mv execution_time* $benchdir
#TODO rename this to be extract_top_5.sh
bash graph_experiment.sh $benchdir
cp -r $benchdir $expname/
rm -r scratch

#!/bin/bash
# Script is called during the second profiling stage 
benchname="$1"
expname="$2"
settings_path="$3"
ssn="$4"
frq="$5"
echo "Trace is $trace"

if [ -d $benchname ];
then
	rm -r $benchname
fi

mkdir $benchname

while read p; 
do
	mname=$(echo $p | cut -d',' -f 4)
	which=$(echo $p | cut -d',' -f 2)
	iters=$(echo $p | cut -d',' -f 3)
	
	for ((i=1;i<=12;i++)); 
	do 
		echo "bash dvfs_on_demand.sh $benchname $which $iters $i $mname $ssn"
		bash dvfs_on_demand.sh $benchname $which $iters $i $mname $ssn $frq
	done

done < "${settings_path}/kenan_${benchname}_settings"

mv execution_time* $benchname/
mv kenan_energy* $benchname/
mv freq* $benchname/
mv iteration_times* $benchname/
mv iteration_energy* $benchname/

cp -r $benchname $expname

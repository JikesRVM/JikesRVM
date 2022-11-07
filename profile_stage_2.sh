#Called during the second profiling stage to get the optimal frequency. 
#Calls dvfs_profiling -> dvfs_on_demand.sh
#name is the name of the experiment
#settings is the path of the settings directory holding the top 5 methods from the profiling stage
#ssn refers to sample number
#frq is 2?  //TODO define what the kenan_frequency option does

name=$1
settings=$2
ssn=$3
frq=$4

if [ ! -d $name ];
then
	mkdir $name
fi

#array=(sunflow fop luindex bloat antlr jython pmd avrora)
array=(luindex sunflow fop jython  antlr bloat pmd avrora)
#array=(luindex)
#array=(sunflow fop)
#array=(pmd)
#array=(luindex)
#array=(antlr)
for i in "${array[@]}"
do
	bash dvfs_profiling.sh $i $name $settings $ssn $frq
done

benchname=$1
expname=$6
benchdir="kenan_${benchname}"
echo "$benchdir"


#joon  
#rapl.sh runs the benchmark for each of the 12 available frequencies on Eevee
#isnt used for any of the profiling stage data was added for experimental purposes
#bash rapl.sh $1 $2 $3 $4 $5

#run dacapo on ondemand governor to get top 5 methods
bash rapl_on_demand.sh $1 $2 $3 $4 $5

if [ -d $benchdir ]
then
	rm -r $benchdir
fi

mkdir $benchdir
mv counter_based* $benchdir/
mv freq_* $benchdir/
mv kenan_energy* $benchdir
mv execution_time* $benchdir
bash graph_experiment.sh $benchdir
cp -r $benchdir $expname/
rm -r scratch

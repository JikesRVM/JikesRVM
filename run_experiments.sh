expname=$6
benchname=$1
benchdir="kenan_${benchname}"
echo "$benchdir"
#bash rapl.sh $1 $2 $3 $4 $5
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
bash graph_experiments.sh $benchdir
cp -r $benchdir $expname/
rm -r scratch

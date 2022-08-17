benchname="$1"
expname="$2"
settings_path="$3"
sn="$4"

echo "sn $sn"

if [ -d $benchname ];
then
	rm -r $benchname
fi

mkdir $benchname

echo "Optimizing bench $benchname"
p=$(cat "${settings_path}/kenan_${benchname}_settings")
echo $p
mname=$(cat "${settings_path}/kenan_${benchname}_settings" | cut -d';' -f 4)
echo $mname
which=$(echo $p | cut -d';' -f 2)
iters=$(echo $p | cut -d';' -f 3)
echo "bash dvfs_on_demand.sh $benchname $which $iters $i $mname $sn"
bash dvfs_on_demand_top.sh $benchname $which $iters "19" "$mname" $sn


mv execution_time* $benchname/
mv kenan_energy* $benchname/
mv freq* $benchname/
mv iteration_times $benchmark/
mv iteration_energy $benchmark/

cp -r $benchname $expname

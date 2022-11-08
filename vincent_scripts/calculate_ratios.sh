# Current directory path
bench=$1
# Experiment directory path
exp=$2
# Settings directory path
settings=$3
#Baseline directory path
baseline=$4

mkdir "$exp/ratios"
bestetmcsv="$exp/ratios/${bench}_etm_best.csv"
bestenergycsv="$exp/ratios/${bench}_energy_best.csv"
besttimecsv="$exp/ratios/${bench}_time_best.csv"

combinedcsv="${bench}_combined.csv"

echo "Checking settings directory ..."

if [ -d "$settings" ];
then
	echo "$settings directory found. Using as the settings directory"
else
	echo "$settings directory not found. Please make sure you have a valid settings directory" 
	return
fi

header="name,f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11"

echo $header > $bestetmcsv
echo $header > $bestenergycsv
echo $header > $besttimecsv

benchsettings="${settings}/${bench}_settings"
echo "Reading $bench Settings : $benchsettings"
while IFS= read -r line
do
   
    mname=$(echo $line | cut -d';' -f 4)
    iters=$(echo $line | cut -d';' -f 3)
    
    bestenergy="${baseline}/${bench}_best_e"
    bestenergy=$(head -1 "$bestenergy")
    besttime="${baseline}/${bench}_best_t"
    besttime=$(head -1 "$besttime")

    echo "best,$bench,$bestenergy,$besttime"

    filepart="$mname"
    fullname="$mname"
    filepart="$fullname"

    bestenergyline="$fullname"
    besttimeline="$fullname"
    bestetmline="$fullname"
    combinedline="$fullname"

    # the reason this is 2 is because we decided to remove the first frequency
    for i in {2..12}
    do
    eng="$exp/$bench/kenan_energy_${bench}_${i}_${filepart}"
    eng=$(head -1 "$eng")
    time="$exp/$bench/execution_time_${bench}_${i}_${filepart}"
    time=$(head -1 "$time")

    bestengr="scale=10;$eng / $bestenergy"
    bestengr=$(bc -l <<< $bestengr)
    
    besttimer="scale=10;$time / $besttime"
    besttimer=$(bc -l <<< $besttimer)

    bestetmr="scale=5; ($eng * $time) / ($bestenergy * $besttime)"
    bestetmr=$(bc -l <<< $bestetmr)

    bestenergyline="$bestenergyline,$bestengr"
    besttimeline="$besttimeline,$besttimer"
    bestetmline="$bestetmline,$bestetmr"

    combinedline="$combinedline,$time;$eng;$timebaseline;$engbaseline"
 
    done

    echo  $bestetmline >> $bestetmcsv
    echo  $besttimeline >> $besttimecsv
    echo  $bestenergyline >> $bestenergycsv
    echo  $combinedline >> $combinedcsv 
done < "${benchsettings}"

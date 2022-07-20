dir=$1
bench=$2

echo $dir
if [ ! -d $dir ];
then
	mkdir $dir
fi

iters=$(wc -l $bench/counter_based_sampling_iteration_times_0)
iters=$(echo $iters | cut -d' ' -f 1)
spath="$dir/${bench}_settings"
##Read method names
rm $spath
input="$bench/top5.csv"
type="old"

array=(sunflow lusearch luindex avrora)
for i in "${array[@]}"
do
	if [ "$bench" = "$i" ];
	then
		type="new"
	fi
done

while IFS= read -r line
do
	mname=$(echo $line | cut -d',' -f 2)
	setting="$bench;$type;$iters;$mname"	
	echo $setting >> $spath
done < "$input"

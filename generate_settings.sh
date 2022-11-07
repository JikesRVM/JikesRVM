dir=$1
bench=$2
iters=$3

echo $dir
if [ ! -d $dir ];
then
	mkdir $dir
fi
#get Number of iterations from reading number of new lines
iters=$(wc -l $dir/$bench/counter_based_sampling_iteration_times_0)
iters=$(echo $iters | cut -d' ' -f 1)
spath="$dir/${bench}_settings"
##Read method names
rm $spath
input="$dir/$bench/top5.csv"
type="old"

array=(sunflow lusearch luindex avrora)
for i in "${array[@]}"
do
	if [ "$bench" = "kenan_$i" ];
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

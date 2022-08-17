#name of experiment
name=$1
#path of settings dir containing top 5 methods
settings=$2
sn=$3
array=()

if [ ! -d $name ];
then
	mkdir $name
fi

array=(sunflow avrora jython fop antlr bloat luindex pmd)
for i in "${array[@]}"
do
	bash optimize_bench_top.sh $i $name $settings $sn
done

exp=$1
settings_dir=$2
baseline_dir=$3
# array=(antlr sunflow luindex pmd avrora jython fop bloat)
# avrora and bloat have inconsistant data
array=(antlr sunflow luindex pmd jython fop)
for i in "${array[@]}"
do
	echo "bash prepare_tem.sh $i $exp"
	bash prepare_tem.sh $i $exp $settings_dir $baseline_dir
	# bash prepare_tem_demand.sh $i $exp
	# bash prepare_tem_powersave.sh $i $exp
	# bash prepare_tem_performance.sh $i $exp
done

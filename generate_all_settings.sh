array=()
array=(pmd luindex avrora jython fop sunflow bloat antlr)
for i in "${array[@]}"
do
	bash generate_settings.sh $1 $i
done

#takes the directory of the experiment as the first agrument
#calls generate_settings.sh 

array=(pmd luindex avrora jython fop sunflow bloat antlr)
for i in "${array[@]}"
do
	bash generate_settings.sh $1 "kenan_$i"
done

#takes the directory of the experiment as the first agrument
#calls generate_settings.sh 
#TODO combine this step with the extract top 5 methods
array=(pmd luindex avrora jython fop sunflow bloat antlr)
for i in "${array[@]}"
do
	bash generate_settings.sh $1 "kenan_$i"
done

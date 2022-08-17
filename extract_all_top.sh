experiment_dir=$1

array=(pmd avrora jython fop sunflow antlr bloat luindex)
#TODO edit proifile scripts to change kenan_bench dir format
for i in "${array[@]}"
do
	bash extract_top.sh $experiment_dir/"kenan_$i"
	cp Package_top.csv $experiment_dir/"kenan_$i"/top5.csv 
done

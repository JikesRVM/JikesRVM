experiment_dir=$1

array=(pmd avrora jython fop sunflow antlr bloat luindex)

for i in "${array[@]}"
do
	bash extract_top.sh $1/$i
	cp Package_top.csv $1/$i/top5.csv 
done

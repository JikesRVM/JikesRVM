array=(pmd avrora jython fop sunflow antlr bloat luindex)

for i in "${array[@]}"
do
	bash extract_top.sh $i
	cp Package_top.csv $i 
	cp Package_top.csv $i/top5.csv 
done

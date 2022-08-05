cd $1
mkdir methods
cd ../
cp header $1
bash cat_all.sh $1
cp cats.sh $1/
cd $1
bash cats.sh > "data.csv"
sed 's/\ //g' data.csv > final.csv
cd ../
python3 genFig.py -i $1/final.csv -m CacheMisses_top.csv -t export_top -c Package -d $1 -b $1

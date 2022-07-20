catall="cat header "
for freq in {0..12}
do	
	#awk '{print $0, "string to append after each line"}' file > new_file
	f="$1/counter_based_sampling_kenan.$freq.csv"
	awk -v fr="$freq" '{print fr,",",$0}' $f > $1/$freq.csv
	catall="${catall} $freq.csv"
done
echo $catall > cats.sh


for bench in kenan_*
do
	echo $bench
	cd $bench
	for freq in  {0..12}	
	do
		iteration_file="counter_based_sampling_iteration_times_${freq}"
		if [ -f ${iteration_file} ]	
		then
			iter_times=$(head -20 ${iteration_file})
			exectime=0
			for iter_time in ${iter_times};
			do
				start=$(echo ${iter_time} | cut -d',' -f 1)
				end=$(echo ${iter_time} | cut -d',' -f 2)
				duration=$((end-start))
				exectime=$((exectime+duration))	
			done	
			echo $exectime > "${freq}_execution_time"	
		fi
	done
	cd ../
done	

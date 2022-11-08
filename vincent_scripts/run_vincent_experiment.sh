#!/bin/bash

# name of the experiment profiling data will be stored there
experiment_dir=$1
# number of iterations per frequency i.e. 20 iterations means each benchmark will be ran for 20 iterations per available frequency
iterations=$2
# number of samples per time interval
samples=$3
# baseline directory i.e. the directory with the unmodified jikes rvm run under original linux governors
baseline_dir=$4
# im not really sure, ask kenan
frequency=2

# Dacapo Benchmarks
benchmarks=(luindex sunflow avrora fop jython antlr bloat pmd)

# generate experiment directories settings directory(top 5 methods will be stored in this settings directory)
mkdir $experiment_dir
mkdir $experiment_dir/settings

# call rapl_on_demand.sh this will run each benchmark on the ondemand governor and print the energy and execution time
for bench in ${benchmarks[@]}; do \
    case $bench in
        # These run on the old dacapo jar (dacapo-2006-10-MR2.jar)
        jython | antlr | bloat | fop | pmd)
            echo -n "running profiling for: $bench" $'\n'
            # call profiling script
            bash method_profiling.sh $bench old $iterations $samples $frequency
            # make the benchmark directory and move all the data into the experiment dir
            benchdir=$bench
            if [ -d $benchdir ]
            then
                rm -r $benchdir
            fi  
            mkdir $benchdir
            mv counter_based* $benchdir/
            mv freq_* $benchdir/
            mv kenan_energy* $benchdir
            mv execution_time* $benchdir
            mv $benchdir $experiment_dir
            ;;
        # These run on the newer dacapo jar (dacapo-9.12-bach.jar)
        sunflow | luindex | avrora)
            echo -n "running profiling for: $bench" $'\n'
            bash method_profiling.sh $bench new $iterations $samples $frequency
            # make the benchmark directory and move all the data into the experiment dir
            benchdir=$bench
            if [ -d $benchdir ]
            then
                rm -r $benchdir
            fi  
            mkdir $benchdir
            mv counter_based* $benchdir/
            mv freq_* $benchdir/
            mv kenan_energy* $benchdir
            mv execution_time* $benchdir
            mv $benchdir $experiment_dir
            ;;
        # Catching Typos
        *)
            echo -n "unknown benchmark!!!!!!!!!" $'\n' 
            ;;
    esac
done

# Extract Top 5 Energy Consuming Methods & generate the settings directory
echo "Extracting Top 5 Energy Consuming Methods" $'\n'
for bench in ${benchmarks[@]}; do \
    bash generate_settings.sh $experiment_dir $iterations
done  

# DVFS Top 5 methods for all available frequencies 
# Top 5 methods are consumed through the settings directory collected in the step before
# Outputs a directory named ${experiment_dir}_dvfs 
for benchmark in ${benchmarks[@]}; do \
    while read p; 
    do
        method_name=$(echo $p | cut -d',' -f 4)
        dacapo_version=$(echo $p | cut -d',' -f 2)
        iters=$(echo $p | cut -d',' -f 3)
        # This will be used to determine which cpu frequency to be scaled 
        # TODO read available frequencies from cpufreq instead of hardcoding
        for ((i=1;i<=12;i++)); 
        do 
            echo "Starting dvfs profiling for $mname frequency $i"
            # echo "bash dvfs_on_demand.sh $benchname $dacapo_version $iters $i $mname $ssn"
            bash dvfs_profiling.sh $benchmark $dacapo_version $iterations $i $method_name $samples $frequency
        done
    done < "${experiment_dir}/settings/${benchmark}_settings"

    if [ -d $benchmark ];
    then
        rm -r $benchmark
    fi
    mkdir $benchmark

    output_dir="${experiment_dir}_dvfs"
    
    mkdir $output_dir

    mv execution_time* $benchmark/
    mv kenan_energy* $benchmark/
    mv freq* $benchmark/
    mv iteration_times* $benchmark/
    mv iteration_energy* $benchmark/
    mv $benchmark $output_dir 
done

# Calculate energy, time, edp difference with experiment and baseline experiment
# Outputs a ratios directory that will be used to draw the heatmaps
for benchmark in ${benchmarks[@]}; do \
    bash calculate_ratios.sh $benchmark $experiment_dir "${experiment_dir}/settings" $baseline_dir
done


# Generate heatmaps
# Outputs three heatmaps under a new subdirectory called heatmaps
mkdir "$experiment_dir/heatmaps"
python3 data_processing.py --function profiling_generate_settings --experiment_dir $experiment_dir  --iterations $iterations


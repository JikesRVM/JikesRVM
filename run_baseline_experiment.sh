#!/bin/bash

# Experiment name
# I named the baseline experiments baseline_20iterations in order to not be confused with the Vincent experiments
expname=$1
# number of iterations per frequency
iters=$2

mkdir $expname

# sudo bash vincent_scripts/baseline_profiling.sh sunflow new $iters   $expname
# sudo bash vincent_scripts/baseline_profiling.sh jython old $iters $expname
# sudo bash vincent_scripts/baseline_profiling.sh pmd old $iters  $expname
# sudo bash vincent_scripts/baseline_profiling.sh fop old $iters $expname
# sudo bash vincent_scripts/baseline_profiling.sh avrora new $iters $expname
sudo bash vincent_scripts/baseline_profiling.sh luindex new $iters  $expname
# sudo bash vincent_scripts/baseline_profiling.sh antlr old $iters $expname
# sudo bash vincent_scripts/baseline_profiling.sh bloat old $iters $expname

mv *execution_time $expname
mv *kenan_energy   $expname
mv *iteration_times $expname
mv *iteration_energy $expname


python3 vincent_scripts/data_processing.py --function calculate_min --experiment_dir $expname
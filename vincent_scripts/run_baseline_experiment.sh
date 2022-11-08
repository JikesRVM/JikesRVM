#!/bin/bash

# Experiment name
# I named the baseline experiments baseline_20iterations in order to not be confused with the Vincent experiments
expname=$1
# number of iterations per frequency
iters=$2

mkdir $expname

sudo bash run_benchmark_baseline.sh sunflow new 10   $expname
sudo bash run_benchmark_baseline.sh jython old $iters $expname
sudo bash run_benchmark_baseline.sh pmd old $iters  $expname
sudo bash run_benchmark_baseline.sh fop old $iters $expname
sudo bash run_benchmark_baseline.sh avrora new $iters $expname
sudo bash run_benchmark_baseline.sh luindex new $iters  $expname
sudo bash run_benchmark_baseline.sh antlr old $iters $expname
sudo bash run_benchmark_baseline.sh bloat old $iters $expname

mv *execution_time $expname
mv *kenan_energy   $expname
mv *iteration_times $expname
mv *iteration_energy $expname

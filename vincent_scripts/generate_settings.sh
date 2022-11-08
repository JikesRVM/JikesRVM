#!/bin/bash

# path of the experiment dir
experiment_dir=$1
# number of iterations per frequency i.e. 20 iterations means each benchmark will be ran for 20 iterations per available frequency
iterations=$2

python3 data_processing.py --function generate_heatmaps --experiment_dir $experiment_dir  --iterations $iterations

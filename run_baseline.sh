expname=$1
iters=$2

mkdir $expname

sudo bash run_benchmark_baseline.sh sunflow new 10   $expname
sudo bash run_benchmark_baseline.sh jython old 40 $expname
sudo bash run_benchmark_baseline.sh pmd old 40  $expname
sudo bash run_benchmark_baseline.sh fop old 40 $expname
# sudo bash run_benchmark_baseline.sh avrora new 40 $expname
sudo bash run_benchmark_baseline.sh luindex new 40  $expname
sudo bash run_benchmark_baseline.sh antlr old 40 $expname
sudo bash run_benchmark_baseline.sh bloat old 40 $expname

mv *execution_time $expname
mv *kenan_energy   $expname
mv *iteration_times $expname
mv *iteration_energy $expname

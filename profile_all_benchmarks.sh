#profile stage 1 

expname=$1
iters=$2
if [ -d $expname ]
then
	rm -r $expname
fi
mkdir $expname

sudo bash run_experiments.sh fop old $iters 32 2 $expname
sudo bash run_experiments.sh pmd old $((iters)) 32 2 $expname
sudo bash run_experiments.sh jython old $((iters)) 32 2 $expname
sudo bash run_experiments.sh bloat old $((iters)) 32 2 $expname
sudo bash run_experiments.sh antlr old $((iters)) 32 2 $expname
sudo bash run_experiments.sh luindex new $iters 32 2 $expname
sudo bash run_experiments.sh sunflow new 10 8 2  $expname
sudo bash run_experiments.sh avrora new $iters 32 2 $expname

expname=$1
iters=$2
if [ -d $expname ]
then
	rm -r $expname
fi
mkdir $expname

sudo bash profile.sh fop old $iters 32 2 $expname
sudo bash profile.sh pmd old $((iters)) 32 2 $expname
sudo bash profile.sh jython old $((iters)) 32 2 $expname
sudo bash profile.sh bloat old $((iters)) 32 2 $expname
sudo bash profile.sh antlr old $((iters)) 32 2 $expname
sudo bash profile.sh luindex new $iters 32 2 $expname
sudo bash profile.sh sunflow new 10 8 2  $expname
sudo bash profile.sh avrora new $iters 32 2 $expname


# sudo bash run_experiments.sh fop old $iters 32 2 $expname
# sudo bash run_experiments.sh pmd old $((iters)) 32 2 $expname
# sudo bash run_experiments.sh jython old $((iters)) 32 2 $expname
# sudo bash run_experiments.sh bloat old $((iters)) 32 2 $expname
# sudo bash run_experiments.sh antlr old $((iters)) 32 2 $expname
# sudo bash run_experiments.sh luindex new $iters 32 2 $expname
# sudo bash run_experiments.sh sunflow new 10 8 2  $expname
# sudo bash run_experiments.sh avrora new $iters 32 2 $expname

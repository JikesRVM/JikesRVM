me="files.txt"
n_path="/home/kmahmou1/jikes/jikesrvm/rvm/src/org/jikesrvm/energy"
k_path="/home/kmahmou1/newjikesrvm/rvm/src/org/jikesrvm/energy"
while read -r line
do
	    name="$line"
	    echo "Inspecting Chanes of $name .... Stay tuned!" 
	    diff "$n_path/$name" "$k_path/$name"	

done < "$me"

echo $n_path
echo $k_path

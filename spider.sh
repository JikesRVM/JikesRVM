#!/bin/bash

source_dir="/home/yangxi/jike/openJDKonJikes/jikesrvm/MMTk/src /home/yangxi/jike/openJDKonJikes/jikesrvm/common/options/src /home/yangxi/jike/openJDKonJikes/jikesrvm/generated/main/java /home/yangxi/jike/openJDKonJikes/jikesrvm/generated/configurations/BaseBaseImmix_x86_64-linux/java /home/yangxi/jike/openJDKonJikes/jikesrvm/generated/ia32-32/main/java /home/yangxi/jike/openJDKonJikes/jikesrvm/rvm/src /home/yangxi/jike/openJDKonJikes/jikesrvm/MMTk/ext/vm/jikesrvm /home/yangxi/jike/openJDKonJikes/jikesrvm/external/tuningforklib/src"


for d in $source_dir; do 
#    echo $d; 
    find $d -name '*.java' | xargs cat | grep  '^import java[a-Z.]*' | grep -o 'java[a-Z.]*' | sort
done

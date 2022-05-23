#!bin/bash

#ant -Dconfig.include.aos=true -Dconfig.default-heapsize.maximum=5000 -Dconfig.runtime.compiler=opt -Dconfig.bootimage.compiler=opt -Dconfig.assertions=none -Dconfig.include.perfevent=false

ant  -Dconfig.include.aos=true -Dconfig.bootimage.compiler=opt -Dconfig.assertions=none 
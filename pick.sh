#!/bin/bash
file=./target/BaseBaseImmix_x86_64-linux/BootImageWriterOutput.txt  
echo $file
cat $file | grep 'Object Not Present'
echo " "
cat $file | grep 'bootImageTypeField entry has'

file=./dist/BaseBaseImmix_x86_64-linux/BootImageWriterOutput.txt  
echo $file
cat $file | grep 'Object Not Present'
echo " "
cat $file | grep 'bootImageTypeField entry has'
# echo $file
# cat $file | grep 'Object Not Present' | grep -o '[\[L]*java.[a-Z.$]*' | uniq | sort 
# echo " "
# cat $file | grep 'bootImageTypeField entry has' | grep -o '[\[L]*java.[a-Z.$]*' | uniq | sort

#file=./dist/BaseBaseImmix_x86_64-linux/BootImageWriterOutput.txt  
# echo $file
# cat $file | grep 'Object Not Present' | grep -o '[\[L]*java.[a-Z.$]*' | uniq | sort 
# echo " "
# cat $file | grep 'bootImageTypeField entry has' | grep -o '[\[L]*java.[a-Z.$]*' | uniq | sort

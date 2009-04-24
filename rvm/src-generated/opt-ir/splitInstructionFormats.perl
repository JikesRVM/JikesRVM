#!/usr/bin/perl
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Eclipse Public License (EPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/eclipse-1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

# split InstructionFormats.RAW into 1 class per file

$outputDir = shift(@ARGV);
$inputFile = shift(@ARGV);
$infile = 0;

open(STDIN, "<$inputFile") || die "cannot redirect standard input";

while (<>) {
    if (m/\#\#NEW_FILE_STARTS_HERE (\S+)\#\#/) {
        $currentFile = $1;
        open(WORKING_FILE, ">$outputDir/$currentFile");
        $infile = 1;
    } elsif ($infile) {
        print(WORKING_FILE $_);
    }
}

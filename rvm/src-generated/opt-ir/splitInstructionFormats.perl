#!/usr/bin/perl
#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# split InstructionFormats.RAW into 1 class per file
# @author Dave Grove 
# @date 01/08/2001.

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

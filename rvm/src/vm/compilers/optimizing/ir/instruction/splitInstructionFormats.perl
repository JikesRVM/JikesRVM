#!/usr/bin/perl
#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# split InstructionFormats.RAW into 1 class per file
# author Dave Grove 01/08/2001.

$outputDir = shift(@ARGV);
$infile = 0;

while (<>) {
    if (m/\#\#NEW_FILE_STARTS_HERE (\S+)\#\#/) {
	$currentFile = $1;
	open(WORKING_FILE, ">$outputDir/instructionFormats/$currentFile");
	$infile = 1;
    } elsif ($infile) {
	print(WORKING_FILE $_);
    }
}

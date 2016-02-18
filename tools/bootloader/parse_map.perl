#!/usr/bin/perl -w
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


# Extract all method names
# and entry points from RVM.map file.
# Output these to stdout as stabs
# debugging information, in correct syntax
# for input to GNU assembler, as.

use strict;

my $MAPFH;                      # file handle on RVM.map file
my $ASMFH;                      # file handle on output file
my $mapfilename = shift(@ARGV); # text version of RVM.map file name (with path prefix)
my $asmfilename = shift(@ARGV); # asm version of RVM.map

my $count;    # iteration counter (bump on each method found)
my $start;    # should we start recording method name/entry point data?

print "Processing $mapfilename \n";
print "Generating $asmfilename \n";

# first open RVM.map file
open (MAPFH,"< $mapfilename") || die "Unable to open input file $mapfilename: $!\n";
open (ASMFH,"> $asmfilename") || die "Unable to open output file $asmfilename: $!\n";

# Output stabs file header directives
# Note this file info is invalid - doesn't matter
print ASMFH <<STABS_HEADER;
.stabs \"/DUMMY/\",100,0,0,.Ltext0
.stabs \"dumymap.s\",100,0,315,.Ltext0
        
.text
.Ltext0:

STABS_HEADER


# Now go through RVM.map
# in the map table, each line should look like
# slot offset category contents details args
# (Rightmost fields may be missing in some entries.)
# We are interested in lines with category=code.
# For these lines,
#   extract
#      * contents (entry pt, address in hex)
#      * details (method name, string )
# and output as a stabs entry using
# GNU assembler .stabs directives


#Skip JTOC entries (for now)
while (<MAPFH>) {
  chomp;
  if (/Method Map/) {
    last;
  }
}

$count = 0;
while(<MAPFH>) {
  chomp;
  my ($offset, $type, $method, $args, $ret);
  if (! (($offset, $type, $method, $args, $ret) = m/\.\s+\.\s+code\s+(0x[0-f]+)\s+< \S+, L(\S+); >.(\S+) \((\S*)\)(\S+)/)) {
    if (/0x/) {
      print STDERR "Warning, skipping: $_ \n";
    }
    next;
  }

  # Mangled name for constructor
  $method =~ s/<init>/__init/g;

  # Replace slashes in type name with dots
  $type =~ s|/|.|g;

  # Note that GNU assembler labels
  # have a smaller set of allowable chars
  # than JikesRVM method names
  # See info:as "Symbol Names" for full details
  # See rvm/src/org/jikesrvm/classloader/NativeMethod.java
  # for details of mangling algorithm
  # Note that we only mangle args, not classname/methodname
  my $sig = "$args\__$ret";
  $sig =~ s/[\[]/_3/g;   # replace '[' with "_3"
  $sig =~ s/[;]/_2/g;    # replace ';' with "_2"
  $sig =~ s/[\/]/_/g;    # replace '/' with '_'
  
  # Fully qualified mangled name
  my $mangled = "$type.$method\__$sig";  # "__" separates name from args

  print ASMFH <<STABS_ENTRY;
.stabs "$mangled:F(0,0)",36,0,0,$offset
.type $mangled,function
.global $mangled
$mangled:
  nop
.Lscope$count:       
  .stabs "",36,0,0,.Lscope$count-$mangled

STABS_ENTRY
  $count++;
}

close(MAPFH);
close(ASMFH);

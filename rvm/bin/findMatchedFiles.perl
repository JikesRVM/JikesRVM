#! /usr/bin/perl -w
#
# (C) Copyright IBM Corp. 2001, 2003.
#
# $Id$
#
# @author Peter Sweeney
# @date   11/2/2001
# @modified Steven Augart
# @date   6/9/2003
#	Added check for internal error.


#
# Given input in the form of 
#	value
#	filename
# list files that have a value greater than zero.
# The input is generated from a command in the form of:
#	"find . -exec grep -c "string" {} \;"
# where value counts how many times string occurs in a file.

$debug = 0;

@inputs = <STDIN>;

for ($i=0; $i < @inputs; $i+=2) {
   chomp($file = $inputs[$i]);
   unless (defined($inputs[$i + 1])) {
       print STDERR "Internal error in execution of findDeviantFiles; ODD # of input lines to findMatchedFiles.perl: \$inputs[$i + 1] is undefined. \@inputs is", scalar @inputs, "\n\t\$file is $file\n";
       next;
   }
   chomp($value =$inputs[($i+1)]);	
   if ($debug>=1) {print "$i: line is '$file:$value'\n";}
   if ($value eq 0) {
      if($debug>=1){print "found file $file with no matches";}
      if (! ($file =~ /\.dat/ || $file =~ /CVS/ || $file =~ /.template/ ||
	     $file =~ /[0-9]d[0-9]/ || $file =~ /\.cvsignore/ || $file =~ /[\.\/]expected/ ||
	     $file =~ /\/config/) ) {
	 if($debug>=1){print "   valid\n";}
	 print "$file\n";
      } else {
	 if($debug>=1){print " invalid\n";}
      }
   }
}

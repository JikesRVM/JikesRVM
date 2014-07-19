#! /usr/bin/perl -w
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

#
# Usage: computeAggregateBestScore [execution times file]
#
# The execution times file contains the execution time for each benchmark on a single line.
# Example:
# 1356
# 3240
# 10024
# (empty line)

# Computes the aggregate best score for the DaCapo benchmarks.
#
# SPECjvm98 provides a score for each benchmark that is used to compute the aggregate
# best score. For the DaCapo benchmarks, we just compute the geometric mean of the
# execution times and take the inverse.
#
# We do not use a standard function for this because it would add more depedencies.
# The SPECjvm98 aggregate best score is also computed without a standard function.

use warnings;
use strict;

if ($#ARGV < 0) {
  die "Must provide file name as first argument.";
}

open EXECUTION_TIMES, $ARGV[0] or die "Error opening file '$ARGV[0]': $!";

my @executionTimes = ();
while (<EXECUTION_TIMES>) {
  push(@executionTimes, $_);
}

my $product = 1;
$product *= $_ for @executionTimes;

my $numberOfBenchmarks = scalar(@executionTimes);
my $geometricMean = $product ** (1/$numberOfBenchmarks);
my $aggregateBestScore = 0;

# The mean will be zero if at least one of tests failed
if ($geometricMean > 0) {
  $aggregateBestScore = 1 / $geometricMean;
}
 
printf("Bottom Line: Result: %10s\n", $aggregateBestScore);


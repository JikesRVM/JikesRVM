#!/usr/bin/perl

#
# (C) Copyright IBM Corp. 2001
#
# $Id$

# This script takes the result file from running Run-gather-DNA and
# produces a simple text table of the compilation rates and speedups
# of the various compilers.  These geometric mean lines can then be
# fed into the VM_CompilerDNA.java file in the adaptive system.

# Note:  I am a Perl novice.  Feel free to improve the quality of this code!
# @author Michael Hind, 10/30/01
#

$NUM_COMPILERS=4;
$TOTAL_BENCH=7;
$NUM_PERF_RUNS=5;

$fileName = $ARGV[0];

if (!$fileName) {
    print "Usage: computeDNA.pl file\n";
    exit;
}

open(in_file, $fileName) || die("Can't open $fileName\n");

$run = 0;
$compiler = 0;
$numBench = 0;
$processingCompRate=true;
print "   Compilation Rates  (BC/MilliSec)\n\n";
print "Bench\t\tBase\tOpt 0\tOpt 1\tOpt 2\n";
while(<in_file>){

    if ($processingCompRate eq true) {
	# this code is used to process the 1st half of the file, the comp rate data
	
	# sample string
	# "======= _200_check Finished in 1.392 secs"
	
	# This says match ("m/") the following
	#   \S+ 1 or more any non-white space
	#   ()  give it a name, $1, $2, etc
	if (m/======= (\S+) Finished in (\S+) secs/) {
	    $benchmark = $1;
	    # remember the benchmarks as we see them
	    $benchmarks{$numBench} = $benchmark;
	}
	
	# Base	310	65	505.80	5.36	172.0	32.1
	if ($run==0) {
	    if (m/Base\t(\d+)\t(\d+)\t(\S+)/) {
		$rate = $3;
		&pad_with_tab($benchmark);
		print "\t".$rate;
		$rates{$benchmark}{$run} = $rate;
		$run++;
	    }
	} else {
	    if (m/Opt.*\t(\d+)\t(\d+)\t(\S+)/) {
		$rate = $3;
#	    print $benchmark." opt ".($run-1)." rate: ".$rate."\n";
		print "\t".$rate;
		$rates{$benchmark}{$run} = $rate;
		$run++;
		if ($run==$NUM_COMPILERS) {
		    $run=0;
		    print "\n";
		    $numBench++;
		}
	    }
	}
	# Is it time to compute the geometric mean?
	if ($numBench == $TOTAL_BENCH) {
	    print "Geo Mean";
	    for ($comp=0; $comp<$NUM_COMPILERS; $comp++) {
		$prod=1;
		for ($i=0; $i<$numBench; $i++) {
		    $prod *= $rates{$benchmarks{$i}}{$comp};
		}
		printf("\t%1.2f", ($prod ** (1/$numBench)));
	    }
	    $processingCompRate=false;
	    $numBench=0;
	    $run=0;
	    print "\n\n";

	    print "   Min time of $NUM_PERF_RUNS Runs \n";
	    print "Bench\t\tBase\tOpt 0\tOpt 1\tOpt 2\n";
	}
    } else {

	# this code is used to process the 2nd half of the file, the speedup data
	if (m/======= (\S+) Finished in (\S+) secs/) {
	    $benchmark = $1;
	    $time = $2;
	    # remember the benchmarks as we see them
	    $benchmarks{$numBench} = $benchmark;

	    $times{$benchmark}{$run} = $time;
#	    print $benchmark."\trun:".$run." time:".$time."\n";

	    # check for new min
	    if ($run==0) {
		$min{$benchmark} = $time;
		if ($compiler==0) {
		    &pad_with_tab($benchmark);
		    print "\t";
		}
	    } else {
		if ($time < $min{$benchmark}) {
		    $min{$benchmark} = $time;
		}
	    }

	    $run++;
	    if ($run == $NUM_PERF_RUNS) {
		print $min{$benchmark}."\t";
		$time{$benchmark}{$compiler}=$min{$benchmark};
		$compiler++;
		if ($compiler == $NUM_COMPILERS) {
		    print "\n";
		    $compiler = 0;
		    $numBench++;
		}
		$run=0;
	    }
	}

    }
}

print "\n   Speedup numbers, relative to Baseline\n";
print "\t\tOpt 0\tOpt 1\tOpt 2\n";
for ($i=0; $i<$numBench; $i++) {
    &pad_with_tab($benchmarks{$i});

    # skip the 0th compiler
    for ($comp=1; $comp<$NUM_COMPILERS; $comp++) {
	printf("\t%1.2f", ($time{$benchmarks{$i}}{0}/
			   $time{$benchmarks{$i}}{$comp}));
	$speedups{$benchmarks{$i}}{$comp}=($time{$benchmarks{$i}}{0}/
					  $time{$benchmarks{$i}}{$comp});
    }
    print "\n";
}

## Now compute the Geo Mean
print "Geo Mean";
for ($comp=1; $comp<$NUM_COMPILERS; $comp++) {
    $prod=1;
    for ($i=0; $i<$numBench; $i++) {
	$prod *= $speedups{$benchmarks{$i}}{$comp};
    }
    printf("\t%1.2f", ($prod ** (1/$numBench)));
}

print "\n";

sub pad_with_tab {
    local ($string) = @_;
    if (length($string) < 8) {
	print $string."\t";
    } else {
	print $string;
    }
    
}








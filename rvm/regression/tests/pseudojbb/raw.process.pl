#!/usr/bin/perl
#
# (C) Copyright IBM Corp. 2001
#
# $Id:
#
# @author: Peter F. Sweeney
# @date: September 19, 2003

use POSIX;

###########################################################################
#
# USAGE:
#  ./raw.process.pl input_file
#
# where inputfile is values computed by raw.preprocess.
#
# Generates two files:
#  values.xls      contains the median HPM values for each <(thread|VP), HPM_event> pair.
#  statistics.xls  computes the median, average, stdev, var, min, max and 
#                  stdev as per centage of median for all HPM events in a particular context.
#
###########################################################################

# require "utils.pl";
undef (%debug);
$debug=0;

# hardcoded for now.  ug.
undef (%input_file);
$input_file = $ARGV[0];

if ($input_file eq "") {
   print "\nMust specify input file as first parameter\n";
   exit;
}

$expectedExecutions = 5;

chop($dir = `dirname $input_file`);


if($debug>=1) { print "input_file: $input_file, dir: $dir, expectedExecutions: $expectedExecutions\n"; }

# Step 1: combine all the runs to create the summary file (take median of each run set)
&combine_executions();

exit;


#
# For expected executions of a context, find the median and generate
# statistics.
#
# Assume that not same execution number does not occur multiple times for 
# the same context.
#
sub combine_executions
{

   &my_system("rm -f $dir/value");
   &my_system("rm -f $dir/statistics");
   
   print "open input file $input_file\n";
   open(INPUT, "<$input_file") || die("Can't read open file \"$input_file\"!\n");
   @data = <INPUT>;
   print "length of data ".(scalar @data)."\n";
   @sorted_data = sort(@data);
   print "length of sorted data ".(scalar @sorted_data)."\n";
   
   undef (%value_filename);
   $value_filename = "$dir/values.xls";
   open(VALUES, ">$value_filename") || die("Can't write open file \"$value_filename\"!\n");

   undef (%statistics_filename);
   $statistics_filename = "$dir/statistics.xls";
   open(STATISTICS, ">$statistics_filename") || die("Can't write open file \"$statistics_filename\"!\n");

# for a given context, compute the statistics and pick the median.
   $prev_context   = "";
   $prev_execution = -1;

   $median = 0; $average = 0; $min = 0; $max = 0; $stdev = 0; $stdevPC = 0; $executions = 0;

   $numBadExecutions = 0;
# array for contexts
   $trips = 0;
FILE:
   foreach $string (@sorted_data) {
      
      undef (%context);
      undef (%execution);
      undef (%value);

      if($debug>=14){print "$trips: $string";}
      $trips++;
      if (&parse_string($string) == -1) {
	 print "\n***combine_executions() skip illegal file \"$file\"!***\n\n";
	 print "call next FILE 1\n";
	 next FILE;
      }

      if ($execution < 1 || $execution > $expectedExecutions) {
	 if($debug>=3) {print "\n\n***0 <= $execution < $expectedExecutions!***\n\tDon't save context!***\n";}
	 $numBadExecutions++;
	 print STATISTICS "***$prev_context bad execution number $execution\n";
	 next FILE;
      } elsif ($prev_context eq "" || $prev_context ne $context) {
	 # new context
	 if($debug>=14) { print "new context: $context\n";}
	 if ($prev_context ne "") {
	    # save old context
	    if($executions != $expectedExecutions) {
	       # check to see if an execution failed: that is, VP counter value not found!
	       $point = rindex($prev_context, ".");	# counter number
	       $word = substr($prev_context, 0, $point);
	       $point = rindex($word, ".");		# counter event
	       $event = substr($word, $point+1, length($word));
	       $word = substr($word, 0, $point);
	       if ($event eq "REAL_TIME") {
		  $point = rindex($word, ".");		# VP or thread?
		  $word  = substr($word, $point+1, length($word));
		  $word  = substr($word, 0, 2);		# prefix
		  if ($word eq "VP") {
		     $numBadExecutions++;
		     if($debug>=4){print "***$prev_context has ".($expectedExecutions-$executions)." missing executions\n";}
		     for ($i=0; $i<$expectedExecutions; $i++) {
			if (&decomma($values[$i]) == 0) {
			   if($debug>=4){print "***$prev_context missing execution ".($i+1)."\n";}
			   print STATISTICS "***$prev_context missing execution ".($i+1)."\n";
			} else {
			   if($debug>=4){print "$i: $values[$i]\n";}
			}
		     }
		     &reset_variables;
		     print "call next FILE 2\n";
		     next FILE;
		  } else {
		     if ($debug>=4) {print "!!!$prev_context has missing event $event as thread $word!!!\n";}
		  }
	       } else {
		  if($debug>=4){print "!!!$prev_context has missing event $event!!!\n";}
	       }
	    } # executions != expected executions
	    # compute average and array of decomma values
	    for ($i =0; $i<$expectedExecutions; $i++) {
	       $int_values[$i] = &decomma($values[$i]);
	       $average += $int_values[$i];
	    }
	    @sorted_int_values = sort by_number @int_values;
	    $min     = $sorted_int_values[0];
	    $max     = $sorted_int_values[$expectedExecutions-1];
	    $median  = $sorted_int_values[($expectedExecutions-1)/2];
	    $average = int(($average/$expectedExecutions)+0.5);
	    # compute standard deviation
	    $sum = 0;
	    for ($i =0; $i<$expectedExecutions; $i++) {
	       $temp = $sorted_int_values[$i] - $average;
	       $sum += $temp*$temp;
	    }
	    $stdev = int(sqrt($sum/$expectedExecutions)+0.5);
	    if ($median > 0) {
	       $stdevPC = int(($stdev/$median)*10000)/100;
	    } else {
	       $stdevPC = 0;
	    }
	    if($debug>=4) { 
	       print "old context: $prev_context: \n   min $min; median $median; max $max; avg $average; stdev $stdev ($stdevPC%); executions $executions\n"; 
	       for ($i =0; $i<$expectedExecutions; $i++) {
		  if($debug>=4) {print "$i: $sorted_int_values[$i]\n";}
	       }
	    }
	    # save results
	    print VALUES "$prev_context $median\n";
	    print STATISTICS "$prev_context $min $median $max $average $stdev $stdevPC $executions\n";
	 }
	 &reset_variables;
      }
      if ($values[($execution-1)] == 0) {
	 $values[($execution-1)] = $value;
      } else {
	 print "***$context values[".($execution-1)."] = ".$values[($executions-1)]." != 0, value = $value, skip!***\n";
      }
      if($debug>=14) {print "values[$execution] = $values[$executions] = $value\n";}
      $executions++;
   } # foreach
   if ($numBadExecutions > 0) {
      print "\n\nNumber of bad executions is $numBadExecutions\n";
      print STATISTICS "\n\nNumber of bad executions is $numBadExecutions\n";
   }
}

sub reset_variables 
{
   # new context - reset results
   $median = $average = $min = $max = $stdev = $stdevPC = $executions = 0;
   $prev_context = $context;
   # undef (@values);
   for ($i =0; $i < $expectedExecutions; $i++) {
      $values[$i] = 0;
   }
   undef (@int_values);
   undef (@sorted_int_values);
}

#
# parse string where string is a dot, ".", separated string of values of the form:
#   benchmark.appThreads.transactions.machine.numProcs.image.gc.pinned.mode.group.VPID.thread.TID.event.counter.execution=value
# where
#  benchmark     benchmark name
#  appThreads    number of application threads (e.g. pseudojbb is the number of warehouses)
#  transactions  number of transcations)
#    config      appThreads.transactions
#  machine   machine type (e.g. P4)
#  numProcs  number of processors
#  image     aos or jit mode: aos, base, opt0, opt1, opt2
#  gc        type of GC
#  pinned    virtual processor fixed or notFixed to a kernel thread.
#  mode      encoding of mode (e.g. 13 is user and kernel and group)
#  group     encoding of group
#  VPID      virtual processor number
#  thread    name of thread
#  TID       thread ID
#  event     name of hardware event
#  counter   HPM counter number for event
#  execution execution number
#  value     HPM value for event in this context.
#
# Set the variable values:
#  context   substring to the left of the "=" excluding exeuction.
#  execution 
#  value
#
sub parse_string
{
   local($string) = @_;
   undef (%point);
   undef (%word);
   chop($string);

#  find "="
   $point = index($string, "=");   
   $word  = substr($string, 0, $point);
   $value = substr($string, $point+1, length($string));
#  benchmark name
   $point    = rindex($word, ".");   
   $context  = substr($word, 0, $point);
   $execution= substr($word, $point+1,length($string)); 
   if($debug>=13) {
      print "parse_string() returns execution: $execution, value: $value, context: $context\n";
   }
}


#
# Read the contents of an output file to determine HPM events and values for benchmark
#
#
sub read_file
{
    local($file) = @_;

#    print ("Reading $file.  Stragtegy: $strategy\n");

    open (INPUT,"<$dir/$file");

#   states    
    local($foundBenchmark) = "false";
    
#   identifier
    local($VPid) = "undefined";    
    local($Tid)  = "undefined";    
    local($id);

    while (<INPUT>) {
       local($line) = $_;
       
       if($debug>=6) {print "$line";}
       if ($foundBenchmark eq "false") {
	  if (m/VM_HPMs.notifyAppStart\((\w+)/) {
	     if ($1 eq $benchmark) {
		if($debug>=3){print "found start of $benchmark\n";}
		$foundBenchmark = "true";
	     } else {
		if($debug>=3){print "found start of $1 ne $benchmark\n";}
	     }
	  }
       } else {
	  # foundBenchmark = true
	  if (m/VM_HPMs.notifyAppStart\((\w+)\) finished/) {
	     if ($1 ne $benchmark) {
		print "\n***read_file($file) VM_HPMs.notifyAppStart($1) finished but not $benchmark!***\n\n";
		exit;
	     }
	     if($debug>=3){print "found end of start of $benchmark\n";}
	     $foundBenchmark = "false";
	     break;
	  }
	  # thread or vp
	  if (m/Virtual Processor: (\w+)/) {
	     $numVP = $1;
	     if($debug>=4){print "found Virtual Processor $numVP\n";}
	     $VPid = "VP".$numVP;
	     $Tid  = "undefined";
	  } elsif (m/ThreadIndex: (\w+) \((\w+)\) ((\w+\.*)+)/) {
	     $localTID   = $1;
	     $globalTID  = $2;
	     $word       = $3;
	     $point = rindex($word, ".");
	     $threadName = substr($word, $point+1, length($word));
	     if($debug>=4){print "found Thread: $localTID ($globalTID) $threadName\n";}
	     $Tid = $threadName.$globalTID;
	  } else {
	     # HPM event and value: 0: REAL_TIME           :246,294,403
	     if (m/(\w+):\s*(\w+)\s*:((\w+|\,)*)/) {
		$counterNum   = $1;
		$counterName  = $2;
		$counterValue = $3;
		if ($Tid eq "undefined") {
		   $id = $VPid;
		} else {
		   $id = $VPid. "." .$Tid;
		}
		$datum=$prefix.".".$id.".".$counterName.".".$counterNum.".".$execution."=".$counterValue;
		if($debug>=5){print "found $datum\n";}
		print OUTPUT $datum."\n";
	     }
	  }
       }
    }
    close (INPUT);
}

#
sub decomma
{
   local($value) = @_;

#   if ($debug>=3) {print "decomma($string)\n";}

   $done = 0;
   while ($done == 0) {
      $point = index($value, ",");
      if ($point == -1) {
	 $done = 1;
      } else {
	 $value = substr($value,0,$point) . substr($value, $point+1,length($value));
      }
   }
#   if ($debug>=3) {print "decomma() returns $value\n";}
   return $value;
}

sub my_system
{
    local($command) = @_;

    print "Executing: $command\n";
    system("$command");
}

sub by_number
{
   if ($a < $b) {
      -1;
   } elsif ($a == $b) {
      0;
   } elsif ($a > $b) {
      1;
   }
}

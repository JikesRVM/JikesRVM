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
#  ./raw.compute.pl dir
#
# where dir is the directory to find output files created by raw.run.
#
# Generates a summary file that contains the 
#  benchmark.appThreads.transactions.machine.procs.image.gc.bound.mode.group.VP.thread.TID.event.counter.execution=value
#
###########################################################################


# require "utils.pl";
undef (%debug);
$debug=0;

# hardcoded for now.  ug.
$dir = $ARGV[0];

# Step 1: combine all the runs to create the summary file (take median of each run set)
&combine_runs();

exit;


# Read in N runs and output a summary file (median of N runs)
sub combine_runs
{
   &my_system("rm -f $dir/summary");
   
   print "open directory $dir\n";
   opendir(DIR, "$dir") || die("Can't open dir \"$dir\"!\n");
   @all_files = readdir(DIR);
   @sorted_files = sort(grep(/^output\./,@all_files));
   
   undef (%filename);
   $output_filename = "$dir/summary";
   open(OUTPUT, ">$output_filename") || die("Can't open file \"$output_filename\"!\n");

# read in all summary files
 FILE:
   foreach $file (@sorted_files) {
      
      undef (%benchmark);
      undef (%config);
      undef (%machine);
      undef (%numProcs);
      undef (%image);
      undef (%gc);
      undef (%bound);
      undef (%mode);
      undef (%group);
      undef (%execution);

      if (&parse_filename($file) == -1) {
	 print "\n***combine_runs() skip illegal file \"$file\"!***\n\n";
	 next FILE;
      }

      if ($debug>=3) {
	 print "benchmark: $benchmark, config: $config, machine: $machine, numProcs: $numProcs, image: $image, gc: $gc, bound: $bound, mode: $mode, group: $group, execution:  $execution\n";
      }

      undef (%prefix);
      $prefix = $benchmark.".".$config.".".$machine.".".$numProcs.".".$image.".".$gc.".".$bound.".".$mode.".".$group;
      if($debug>=3){print "prefix: $prefix\n";}

      &read_file($file);
   }
}

#
# parse filename where filename is a dot, ".", separated string of values of the form:
#   output.benchmark_appThreads.transactions.machine.numProcs.image.gc.bound.mode.group.execution
# where
#  benchmark     benchmark name
#  appThreads    number of application threads (e.g. pseudojbb is the number of warehouses)
#  transactions  number of transcations)
#    config      appThreads.transactions
#  machine   machine type (e.g. P4)
#  numProcs  number of processors
#  image     aos or jit mode: aos, base, opt0, opt1, opt2
#  gc        type of GC
#  bound     virtual processor fixed or notFixed to a kernel thread.
#  mode      encoding of mode (e.g. 13 is user and kernel and group)
#  group     encoding of group
#  execution execution number
#
# todo: eliminate redundant config.
#
sub parse_filename
{
   local($filename) = @_;
   undef (%point);
   undef (%word);
   if($debug>=3) {print "parse_file_name($filename)\n";}
#  peel off "output." prefix
   $point = index($filename, ".");   
   $filename = substr($filename, $point+1);
#  benchmark name
   $point    = index($filename, "_");   
   $benchmark= substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  config    application threads "." number of transactions
   $point    = index($filename, ".");   
   $config   = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
   $point    = index($filename, ".");   
   $config   = $config . "." .substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  machine   machine type (e.g. P4)
   $point    = index($filename, ".");   
   $machine  = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  numProcs  number of processors
   $point    = index($filename, ".");   
   $numProcs = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  image     aos or jit mode: aos, base, opt0, opt1, opt2
   $point    = index($filename, ".");   
   $image    = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  gc        type of GC: SemiSpace, MarkSweep, CopyMS
   $point    = index($filename, ".");   
   $gc       = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  bound     virtual processor fixed or notFixed to a kernel thread.
   $point    = index($filename, ".");   
   $bound    = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  mode      encoding of mode (e.g. 13 is user and kernel and group)
   $point    = index($filename, ".");   
   $mode     = substr($filename, 0, $point);
   $filename = substr($filename, $point+1); 
#  group     encoding of group
   $point    = index($filename, ".");   
   $word     = substr($filename, 0, $point);
   if ($word >= 400) {
      $group = $word - 400;
   } else {
      $group = $word;
   }
   $filename = substr($filename, $point+1); 
#  execution execution number
   $point    = index($filename, ".");   
   if ($point == -1) { # not found
      $execution = $filename;
   } else {
      print "***parse_filename($filename) illegal suffix!***\n";
      return -1;
   }
   return 0;
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
    local($validEntry)  = "false";    
    local($id);

    while (<INPUT>) {
       local($line) = $_;
       
       if($debug>=6) {print "$line";}
       if ($foundBenchmark eq "false") {
	  if (m/VM_HPMs.notifyAppComplete\((\w+)/) {
	     if ($1 eq $benchmark) {
		if($debug>=3){print "found start of $benchmark\n";}
		$foundBenchmark = "true";
	     } else {
		if($debug>=3){print "found start of $1 ne $benchmark\n";}
	     }
	  }
       } else {
	  # foundBenchmark = true
	  if (m/VM_HPMs.notifyExit/) {
	     if($debug>=3){print "found notifyExit\n";}
	     $foundBenchmark = "false";
	     break;
	  }
	  # thread or vp
	  if (m/Dump aggregate HPM counter values for/) {
	     $validEntry  = "false";
	  } elsif (m/Virtual Processor: (\w+)/) {
	     $numVP = $1;
	     if($debug>=4){print "found Virtual Processor $numVP\n";}
	     $VPid = "VP".$numVP;
	     $Tid  = "undefined";
	     $validEntry = "true";
	  } elsif (m/ThreadIndex: (\w+) \((\w+)\) ((\w+\.*)+)/) {
	     $localTID   = $1;
	     $globalTID  = $2;
	     $word       = $3;
	     $point = rindex($word, ".");
	     $threadName = substr($word, $point+1, length($word));
	     if($debug>=4){print "found Thread: $localTID ($globalTID) $threadName\n";}
#	     $Tid = $threadName.".".$globalTID;
	     $Tid = $threadName;
	     $validEntry = "true";
	  } else {
	     if ($validEntry eq "true") {
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
#		   $counterValue = &decomma($counterValue);
		   $datum=$prefix.".".$id.".".$counterName.".".$counterNum.".".$execution."=".$counterValue;
		   if($debug>=5){print "found $datum\n";}
		   print OUTPUT $datum."\n";
		}
	     }
	  }
       }
    }
    
    close (INPUT);
}


# required functions

sub my_system
{
    local($command) = @_;


    print "Executing: $command\n";
    system("$command");

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


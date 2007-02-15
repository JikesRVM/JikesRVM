#!/usr/bin/perl
#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001, 2003
#
# $Id: night-sanity-run 10860 2006-10-03 19:25:15Z dgrove-oss $
#
# Parse the various nightly sanity logs and produce a html summary.
#
# usage: nightlyreport.pl [-x <input xml>|-r <input root>] [-o <outputfile>] [-s <scp target directory>]
#
use Sys::Hostname;
require "getopts.pl";
&Getopts('x:r:o:s:');
die "Need to specify an input with either -x or -r" unless ($opt_x ne "" || $opt_r ne "");
die "Need to specify an output file to use -s" unless ($opt_s eq "" || $opt_o ne "");

# constants etc
my @shortmonths = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
my @days = ("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
my @shortdays = ("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");
my $COLORS = 16;
my $RED = "#FF0000";
my $GREEN = "#00FF00";
my $BLACK = "#000000";
my $normalfont = "font-weight:normal;";
my $boldfont = "font-weight:bold;";
my $reporturl = "http://cs.anu.edu.au/people/Steve.Blackburn/jikesrvm";
my $basesfcodeurl = "http://svn.sourceforge.net/viewvc/jikesrvm/rvmroot/trunk";
my $host = hostname();

# key variables
my @sanity = ();
my @error = ();
my @results = ();
my @stackid = ();
my @stacks = ();
my @cmd = ();
my %perf = ();
my %imagesize = ();
my %jvm98details = ();
my @javadocerrs = ();
my @optdetails = ();
my $svnstamp;
my $svnrevision;
my $sanityconfig;
my $perfconfig;
my $ran = 0;
my $passed = 0;

#
# dig out the data
#
if ($opt_x ne "") {
  # use an xml report as input
  getxml($opt_x, \@sanity, \@error, \@results, \@cmd, \@stackid, \@stacks, \$ran, \$passed, \%perf, \%jvm98details, \@javadocerrs, \@optdetails, \$svnstamp, \$svnrevision, \%imagesize);
} else {
  # dig it out of unstructured data within the specified root directory
  my $root = $opt_r;
  if (!(-e $root)) {
    $root = `pwd`;
    ($root) = $root =~ /^(.+)\s*$/;
  }
  getsanity($root, \@sanity, \@error, \@results, \@cmd, \@stackid, \@stacks, \$ran, \$passed);
  getperf($root, \%perf, \%jvm98details);
  getdetails($root, \@javadocerrs, \@optdetails, \$svnstamp, \$svnrevision, \$sanityconfig, \$perfconfig);
  getimagesize($root, \%imagesize);
}

#
# produce the html summary
#

# determine the output stream
my $html;
if ($opt_o ne "") {
  open($html, ">$opt_o");
} else {
  $html = STDOUT;
}

# create the html
printhtmlhdr($html);
gensummary($html, \%perf, \%imagesize, $ran, $passed, $svnstamp, $svnrevision, $sanityconfig, $perfconfig);
genbuildfailures($html, \@sanity, \@error, \@stackid);
gentestfailures($html, \@sanity, \@error, \@stackid);
genperfdetails($html, \%perf, \%jvm98details);
genstacks($html, \@sanity, \@error, \@cmd, \@stackid, \@stacks);
genjavadoc($html, \@javadocerrs);
genoptdetails($html, \@optdetails);
gentestsuccesses($html, \@sanity, \@error);
printhtmlftr($html);

# close the output file, if any
if ($opt_o ne "") {
  close($html);
}

#
# propogate result file via scp if requested
#
if ($opt_s ne "") {
#  my $webtarget = "steveb\@littleblue.anu.edu.au:public_html/jikesrvm/$host/".getdatestr($svnstamp).".html";
  my $webtarget = "$opt_s/".getdatestr($svnstamp).".html";
  $cmd = "scp $opt_o $webtarget";
#  print "$cmd\n";
  system($cmd);
}

#
# we're done
#
exit(0);

#
# generate html for the report summary
#
sub gensummary {
  my ($html, $perf, $imagesize, $ran, $passed, $svnstamp, $svnrevision, $sanityconfig, $perfconfig) = @_;
  print $html "<h2>Summary</h2>\n";
  print $html "<table columns=\"2\">\n";#
  print $html "<tr><td align=\"right\">Regression tests:<td><b>";
  my $failed = ($ran - $passed);
  if ($failed > 0) {
    print $html "<font color=\"#ff0000\">Failed $failed</font>/$ran</b> (<font color=\"#ff0000\">".int(100*$failed/$ran)."%</font>)</tr>\n";
  } else {
    print $html "<font color=\"#00ff00\">PASSED $passed/$ran (100%)</font></b></tr>\n";
  }
  print $html "<tr><td align=\"right\">JavaDoc errors:<td><b>".scalar(@javadocerrs)."</b></tr>\n";
  print $html "<tr><td align=\"right\">jbb2000 score:<td><b>".${$perf}{"jbb2000"}."</b></tr>\n";
  print $html "<tr><td align=\"right\">jvm98 best:<td><b>".${$perf}{"jvm98-bottomline"}."</b></tr>\n";
  print $html "<tr><td align=\"right\">jvm98 first:<td><b>".${$perf}{"jvm98-firstrun"}."</b></tr>\n";
  my $csz = sprintf("%.2fMB", ${$imagesize}{"code"}/1024);
  my $dsz = sprintf("%.2fMB", ${$imagesize}{"data"}/1024);
  my $rsz = sprintf("%.2fMB", ${$imagesize}{"rmap"}/1024);
  my $tsz = sprintf("%.2fMB", (${$imagesize}{"code"}+${$imagesize}{"data"}+${$imagesize}{"rmap"})/1024);
  print $html "<tr><td align=\"right\">Image size:<td><b>$tsz</b> (code: $csz, data: $dsz, rmap: $rsz)</tr>\n";
  
  my $svnurl = "http://svn.sourceforge.net/viewvc/jikesrvm?view=rev&revision=$svnrevision";
  print $html "<tr><td align=\"right\">Revision:<td><b><a href=\"$svnurl\">$svnrevision</a></b></tr>\n";
  print $html "<tr><td align=\"right\">Checkout at:<td>$svnstamp</tr>\n";
  print $html "<tr><td align=\"right\">Sanity config:<td>$sanityconfig</tr>\n";
  print $html "<tr><td align=\"right\">Performance config:<td>$perfconfig</tr>\n";
  print $html "</table>\n";
  print $html "<hr>\n";
  print $html "<h2>Details</h2>\n";
  print $html "<ul>\n";
  print $html "\t<li><a href=\"#buildfailures\">Build Failures</a></li>\n";
  print $html "\t<li><a href=\"#testfailures\">Regression Failures</a></li>\n";
  print $html "\t<li><a href=\"#perf\">Performance Details</a></li>\n";
  print $html "\t<li><a href=\"#stacktraces\">Stack Traces</a></li>\n";
  print $html "\t<li><a href=\"#javadoc\">Javadoc Errors</a></li>\n";
  print $html "\t<li><a href=\"#optdetails\">Opt Compiler Details:</a></li>\n";
  print $html "\t<ul>\n";
  print $html "\t\t<li><a href=\"#opt0details\">Level 0</a></li>\n";
  print $html "\t\t<li><a href=\"#opt1details\">Level 1</a></li>\n";
  print $html "\t\t<li><a href=\"#opt2details\">Level 2</a></li>\n";
  print $html "\t</ul>\n";
  print $html "\t<li><a href=\"#testsuccesses\">Regression Successes</a></li>\n";
  print $html "</ul>\n";
}

#
# generate html for the performance details
#
sub genperfdetails {
  my ($html, $perf, $jvm98details) = @_;
  print $html "<h2><a id=\"perf\">Performance Details<a></h2>\n";
  print $html "<h3>SPECjbb2000</h3>\n";
  print $html "jbb2000 score: ".${$perf}{"jbb2000"}."<br>\n";
  print $html "<h3>SPECjvm98</h3>\n";
  print $html "<table columns=\"3\">\n";
  my $run = "";
  foreach $key (sort keys %{$jvm98details}) {
    my $r;
    my $i;
    ($r,$i) = split(/:/, $key);
    if ($r ne $run) {
      $run = $r;
      print $html "<tr><th colspan=\"3\">".($run eq "best" ? "Best Run" : "First Run")."</th></tr>\n";
      print $html "<tr><th>Benchmark</th><th>Time</th><th>Ratio</th></tr>\n";
    }
    my $value = ${$jvm98details}{$key};
    my $bm;
    my $time;
    my $ratio;
    ($bm, $time, $ratio) = $value =~ /\s*(\S+)\s+Time:\s+(.+)\s+Ratio:\s+(.+)\s*/;
    print $html "<tr><td align=\"right\">$bm<td align=\"right\">$time<td align=\"right\">$ratio</tr>\n";
  }
  print $html "</table>\n";
}


#
# generate html for the javadoc errors
#
sub genjavadoc {
  my ($html, $javadocerrs) = @_;
  print $html "<h2><a id=\"javadoc\">".@{$javadocerrs}." Javadoc Errors</a></h2>\n";
  if (@{$javadocerrs} != 0) {
    print $html "<table columns=\"3\">\n";
    my $err;
    my $errno = 1;
    foreach $err (@{$javadocerrs}) {
       my $source;
       my $errmsg;
       ($source, $errmsg) = $err =~ /^(.+) (.+)$/;
       print $html "<tr><td>$errno<td align\"right\">$source<td>$errmsg</tr>\n";
       $errno++;
    }
    print $html "</table>\n";
  }
}

#
# generate html for opt compiler breakdown
#
sub genoptdetails {
  my ($html, $optdetails) = @_;
  print $html "<h2><a id=\"optdetails\">Opt Compiler Breakdown</a></h2>\n";
  my $str;
  my $level = 0;
  foreach $str (@{$optdetails}) {
	print $html "<h3><a id=\"opt".$level."details\">Opt level $level compilation breakdown for SPECjvm98 size 100</a></h3>\n";
	print $html "<pre>$str</pre>\n";
    $level++;
  }
  if (@{$javadocerrs} != 0) {
    print $html "<table columns=\"3\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
    my $err;
    my $errno = 1;
    foreach $err (@{$javadocerrs}) {
       my $source;
       my $errmsg;
       ($source, $errmsg) = $err =~ /^(.+) (.+)$/;
       print $html "<tr><td>$errno<td align\"right\">$source<td>$errmsg</tr>\n";
       $errno++;
    }
    print $html "</table>\n";
  }
}


#
# generate html listing each of the build test failures
#
sub genbuildfailures {
  my ($html, $sanity, $errors, $stackid) = @_;
  $errnum = 0;
  for($s = 0; $s <= $#{$sanity}; $s++) {
    my $error = ${$errors}[$s];
    if ($error =~ /FAILED to build/) {
      $errnum++;
    }
  }
  print $html "<h2><a id=\"buildfailures\">$errnum Build Failures</a></h2>\n";
  if ($errnum > 0) {
    print $html "<table columns=\"3\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
    print $html "<tr><td><th>Build</th><th>Synopsis</th></tr>";
    $errnum = 0;  
    for($s = 0; $s <= $#{$sanity}; $s++) {
      my $error = ${$errors}[$s];
      if ($error =~ /FAILED to build/) {
        ($foo, $errstr) = split(/:/,$error);
        if ($errstr eq "") {
          $errstr = "unknown";
        }
        $errnum++;
        my $build;
        my $test;
        ($build,$test) = split(/:/, ${$sanity}[$s]);
        print $html "<tr>\n";
        print $html "\t<td>$errnum\n";
        print $html "\t<td>$build\n";
        my $stkidx = ${$stackid}[$s];
        if ($stkidx != -1) {
          print $html "\t<td><a href=\"#stk_$stkidx\">$errstr</a>\n"
        } else {
          print $html "\t<td>$errstr\n";
        }
        print $html "</tr>\n";
      }
    }
    print $html "</table>\n";
  }
}

#
# generate html listing each of the regression test successes
#
sub gentestsuccesses {
  my ($html, $sanity, $errors) = @_;
  $errornum = 0;
  my $passnum = 0;
  for($s = 0; $s <= $#{$sanity}; $s++) {
    if (!${$errors}[$s]) {
      $passnum++;
    }
  }
  print $html "<h2><a id=\"testsuccesses\">$passnum Regression Test Successes</a></h2>\n";
  print $html "<table columns=\"3\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
  print $html "<tr><th></th><th align=\"left\" >Build</th><th align=\"left\" >Test</th></tr>\n";
  $passnum = 0;
  for($s = 0; $s <= $#{$sanity}; $s++) {
    if (!${$errors}[$s]) {
      $passnum++;
      my $build;
      my $test;
      ($build,$test) = split(/:/, ${$sanity}[$s]);
      print $html "<tr>\n";
      print $html "\t<td><a id=\"pass_$s\">$passnum</a>\n";
      print $html "\t<td>$build\n";
      print $html "\t<td>$test\n";
      print $html "</tr>\n";
    }
  }
  print $html "</table>\n";
}

#
# generate html listing each of the regression test failures
#
sub gentestfailures {
  my ($html, $sanity, $errors, $stackid) = @_;
  $errornum = 0;
  $errnum = 0;
  for($s = 0; $s <= $#{$sanity}; $s++) {
    my $error = ${$errors}[$s];
    if ($error && !($error =~ /FAILED to build/)) {
      $errnum++;
    }
  }
  print $html "<h2><a id=\"testfailures\">$errnum Regression Test Failures</a></h2>\n";
  print $html "<table columns=\"4\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
  print $html "<tr><th></th><th align=\"left\" >Build</th><th align=\"left\" >Test</th><th align=\"left\" >Synopsis</th></tr>\n";
  $errnum = 0;
  for($s = 0; $s <= $#{$sanity}; $s++) {
    my $error = ${$errors}[$s];
    if ($error && !($error =~ /FAILED to build/)) {
      $error =~ s/Error[:] //;
      $errnum++;
      my $build;
      my $test;
      ($build,$test) = split(/:/, ${$sanity}[$s]);
      my $cmd =  ${$cmds}[$s];
      print $html "<tr>\n";
      print $html "\t<td><a id=\"fail_$s\">$errnum</a>\n";
      print $html "\t<td>$build\n";
      print $html "\t<td>$test\n";
      if ($error =~ /OutOfMemoryError/) {
        $error = "OutOfMemoryError";
      } elsif ($error =~ /NullPointerException/) {
        $error = "NullPointerException";
      } elsif ($error eq "" || $error =~ /-- Stack --/) {
        $error = "unknown";
      }
      print $html "\t<td>";
      my $stkidx = ${$stackid}[$s];
      if ($stkidx != -1) {
        print $html "<a href=\"#stk_$stkidx\">$error</a>\n"
      } else {
        print $html "$error\n";
      }
      print $html "</tr>\n";
    }
  }
  print $html "</table>\n";
}

#
# generate html for each of the unique stack traces
#
sub genstacks {
  my ($html, $sanity, $errors, $cmds, $stackid, $stacks) = @_;
  print $html "<h2><a id=\"stacktraces\">Stack Traces</a></h2>\n";
  print $html "<table columns=\"2\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
  for($s = 0; $s <= $#{$stacks}; $s++) {
    print $html "<tr><td><a id=\"stk_$s\"><b>".($s+1)."</b></a></tr>\n";
    print $html "<tr><td colspan=\"2\"><i>Failing tests:</i></tr>\n";
    my $err;
    for ($err = 0; $err < $#{$stackid}; $err++) {
      if (${$stackid}[$err] == $s) {
        my $build = "";
        my $test = "";
        ($build,$test) = split(/:/, ${$sanity}[$err]);      
        print $html "<tr><td><td>$build $test</tr>\n";
      }
    }
    my $stack = ${$stacks}[$s];
    print $html "<tr><td colspan=\"2\"><i>Stack trace:</i></tr>\n";
    print $html "<tr><td width=\"40\"><td>$stack\n</tr>\n";    
  }
  print $html "</table>\n";
}

#
# produce a date string for the file name, given the svn checkout stamp
#
sub getdatestr {
  my ($svnstamp) = @_;
  $svnstamp =~ s/\s\s/ /g;
  ($day, $mon, $mday, $time, $tz, $year) = split(/ /, $svnstamp);
  for ($m = 0; $m < @shortmonths && $shortmonths[$m] ne $mon; $m++) {}
  my $today = sprintf("%04d%02d%02d", $year, $m+1, $mday);
  if ($today eq "") {
    my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = localtime time;
    $today = sprintf("%04d%02d%02d", $year+1900, $mon+1, $mday);
  }
  return $today;
}

#
# read in data from the xml report
#
sub getxml {
  my ($reportfilename, $sanity, $error, $results, $cmd, $stackidx, $stacks, $ran, $passed, $perf, $jvm98details, $javadocerrs, $optdetails, $svnstamp, $svnrevision, $imagesize) = @_;
  if ($reportfilename =~ /[.]gz/) {
    open(XML, "zcat $reportfilename |") or die "Could not open $reportfilename";
  } else {
    open(XML, "$reportfilename") or die "Could not open $reportfilename";
  }
  my ($intest, $thistest, $test, $result, $output, $command, $time, $configuration);
  $intest = $thistest = $time = 0;
  while (<XML>) {
    if (/<revision>/) {
      (${$svnrevision}) = /<revision>(\d+)<\/revision>/;
    } elsif ($intest == 0 && /<time>[A-Z][a-z][a-z]\s/) {
      (${$svnstamp}) = /<time>(.+)<\/time>/;
    } elsif (/<test-configuration>/) {
      $_ = <XML>;
      while (!/<id>/) { $_ = <XML>; }
      ($configuration) = /<id>(.+)<\/id>/;
    } elsif (/<test>/) {
      ${$stackidx}[$thistest] = -1;
      $intest = 1;
    } elsif (/<\/test>/) {
      ${$sanity}[$thistest] = "$configuration:$test";
      ${$ran}++;
      if ($result ne "EXCLUDED") {
	${$cmd}[${$ran}] = $command;
	if ($result eq "SUCCESS") {
	  ${$passed}++;
	} else {
	  my $stackid = -1;
	  my $errmsg = getxmlerrmsg($result, $time, $output, \$stackid, $stacks);
	  ${$error}[$thistest] = $errmsg;
	  ${$stackidx}[$thistest] = $stackid;
#	  print "$test $configuration $result $errmsg $stackid\n";
	}
      } else {
	${$error}[$thistest] = $result;
      }
      $intest = 0;
      $thistest++;
    } elsif (/<id>/ && $intest) {
      ($test) = /<id>(.+)<\/id>/;
    } elsif (/<command>/ && $intest) {
      ($command) = /<command>(.+)<\/command>/;
    } elsif (/<time>/ && $intest) {
      ($time) = /<time>(.+)<\/time>/;
    } elsif (/<result>/ && $intest) {
      ($result) = /<result>(.+)<\/result>/;
 #     print "$test $configuration $result\n";
    } elsif (/<output>/ && $intest) {
      ($output) = /<output>(.+)$/;
      $output .= "\n";
      $_ = <XML>;
      while (!(/<\/output>/)) {
	$output .= "$_\n";
	$_ = <XML>;
      }
    } elsif ($test eq "ImageSizes" && $configuration eq "production" && /<statistics>/) {
      $_ = <XML>;
      while (!(/<\/statistics>/)) {
	my ($part, $value) = /<statistic key=\"(.+).size\" value=\"(.+)\"/;
	${$imagesize}{$part} = $value/1024;
	$_ = <XML>;
      }
    } elsif ($test eq "SPECjbb2000" && $configuration eq "production" && /<statistics>/) {
      $_ = <XML>;
      while (!(/<\/statistics>/)) {
	my ($value) = /<statistic key="score" value="(.+)"/;
	${$perf}{"jbb2000"} = $value;
	$_ = <XML>;
      }
    } elsif ($test eq "SPECjvm98" && $configuration eq "production" && /<statistics>/) {
     my ($score, $time, $ratio, $bm, $count);
      $_ = <XML>;
      $ratio = $time = -1;
      $count = 0;
      while (!(/<\/statistics>/)) {
	my ($bm, $run, $metric, $value) = /<statistic key="([^.]+).([^.]+).([^.]+)" value="(.+)"/;
	if ($bm eq "aggregate") {
	  if ($run eq "first") {
	    ${$perf}{"jvm98-firstrun"} = $value;
	  } else {
	    ${$perf}{"jvm98-bottomline"} = $value;
	  }
	} else {
	  if ($metric eq "time") { 
	    $time = $value; 
	  } elsif ($metric eq "ratio") {
	    $ratio = $value;
	  }
	  if ($ratio != -1 && $time != -1) {
	    $value = "$bm Time: $time Ratio: $ratio";
	    ${$jvm98details}{"$run:$count"} = $value;
	    $ratio = $time = -1;
	    $count++;
	  }
	}
	$_ = <XML>;
      }
     }
  }
  close(XML);
}

#
# read in sanity details from run.log
#
sub getsanity {
  my ($basedir, $sanity, $error, $results, $cmd, $stackidx, $stacks, $ran, $passed) = @_;
  my $resultdir = "";
  my $resultfile = "";
  my $thiserror = "";
  my $thistest = 0;
  my $performancemode = 0;
  my $compilationmode = 0;
  open(IN, "$basedir/results/run.log");
  while (<IN>) {
    my $build = "";
    my $bm = "";
    if (/You are sane \S+ .+/ || /You are NOT sane. All tests for/ || /You are NOT sane /) {
      ${$stackidx}[$thistest] = -1;
      my ($bmsuffix) = $resultfile =~ /([.]O\d)[.]/;  # check for Opt level suffix
      if ($performancemode) {
        $bmsuffix = ".perf$bmsuffix";
      } elsif ($compilationmode) {
        $bmsuffix = ".comp$bmsuffix";
      }
      if (/You are sane \S+ .+/) {
         ($build, $bm) = /You are sane (\S+) (.+)\s*$/;
        $bm .= $bmsuffix;
        ${$passed}++;
        ${$sanity}[$thistest] = "$build:$bm";
        ${$ran}++;
      } elsif (/You are NOT sane. All tests for/) {
        ($build) = /You are NOT sane. All tests for (\S+) are failures/;
        ${$sanity}[$thistest] = "$build:";
        $thiserror =~ /Build status: FAILED to build (.+) at /;
		$thiserror = getbuilderr($basedir, $build, \$stackid, $stacks);
        ${$stackidx}[$thistest] = $stackid;
        (${$error}[$thistest]) = $thiserror;
      } elsif (/You are NOT sane /) {
        ($build, $bm) = /You are NOT sane (\S+) (.+)/;
        $bm .= $bmsuffix;
        ${$sanity}[$thistest] = "$build:$bm";
        my $suite; my $test;
        ($suite, $test) = split(/ /, $bm);
        if ($test) {
          $resultfile .= ".$test";
        }
        my $stackid = -1;
        ${$error}[$thistest] = geterrmsg($basedir, $resultfile, $thiserror, \$stackid, $stacks);
        ${$stackidx}[$thistest] = $stackid;
        ${$ran}++;
      }
      $thistest++;
    } elsif (/OVERTIME/) {
      ($thiserror) = /(.+)$/;
    } elsif (/[*]+ [[]check[]] Error 1/) {
      ($thiserror) = /(.+)$/;
    } elsif (/^Results to /) {
      ($resultsdir) = /Results to .+results.(.+testing.tests.+)$/;
      $performancemode = 0;
      $compilationmode = 0;
   } elsif (/^Performance mode: results to /) {
      $performancemode = 1;
    } elsif (/^Measure compilation mode/) {
      $compilationmode = 1;
    } elsif (/^Running .+ with limit/) {
      my $outfile;
      ($outfile) = /output to ['](.+)[']/;
      $resultfile = "$resultsdir/$outfile";
      ${$results}[$thistest] = $resultfile;
      $_ = <IN>;
      (${$cmd}[${$ran}]) = /.+bin.(rvm .+)$/;
    } elsif (/Build status: FAILED to build/) {
      ($thiserror) = /(.+)$/;
    }
  }
  close(IN);
}

#
# read in performance details from performance.log
#
sub getperf {
  my ($basedir, $perf, $jvm98details) = @_;
  open(IN, "$basedir/results/performance.log");
  my $run = "";
  my $resultcount = 0;
  while (<IN>) {
    my $score;
    if (/.+Time:.+Ratio:/) {
      ${$jvm98details}{"$run:$resultcount"} = $_;
      $resultcount++;
    } elsif (/Bottom Line: Result:\s+(\d+.\d+)/) {
      ($score) = /Bottom Line: Result:\s+(\d+.\d+)\s*$/;
      ${$perf}{"jvm98-bottomline"} = $score;
    } elsif (/First Run Result:\s+(\d+.\d+)/) {
      ($score) = /First Run Result:\s+(\d+.\d+)/;
      ${$perf}{"jvm98-firstrun"} = $score;
    } elsif (/Bottom Line: Valid run, Score is\s+\d+/) {
      ($score) = /Bottom Line: Valid run, Score is\s+(\d+)/;
      ${$perf}{"jbb2000"} = $score;
    } elsif (/Best Run/) {
      $run = "best";
      $resultcount = 0;
    } elsif (/First Run/) {
      $run = "first";
      $resultcount = 0;
    }
  }
  close(IN);
}

sub getxmlerrmsg {
  my ($result, $time, $output, $stackid, $stacks) = @_;
  my $error = scanerrorstring($output, $stackid, $stacks);
  if ($result =~ /OVERTIME/ && $error eq "") {
    $error = sprintf("OVERTIME: %2.2f sec", $time/1000);
  } else {
    $error = "Error: ".$error;
  }
  return $error;
}
#
# Dig out the error message and stack trace (if any) for a given 
# regression failure
#
sub geterrmsg {
  my ($base, $resultfile, $error, $stackid, $stacks) = @_;
  if ($error =~ /OVERTIME/) {
    ($error) = $error =~ /.+(OVERTIME .+have passed[)]).+/;
  } elsif ($error =~ /Build status: FAILED/) {
    ($error) = $error =~ /Build status: (FAILED to build .+) at /;
  } else {
    $error = "Error: ".scanerrorlog("$base/results/$resultfile", $stackid, $stacks);
  }
  return $error;
}

#
# Dig out the error message and stack trace (if any) for a given 
# build failure
#
sub getbuilderr {
  my ($base, $build, $stackid, $stacks) = @_;
  $error = scanerrorlog("$base/images/$build/RVM.trace", $stackid, $stacks);
  return "FAILED to build $build:$error";
}

#
# Scan an error log and extract a stack trace if possible.
#
sub scanerrorlog {
  my ($logfile, $stackid, $stacks) = @_;
  open(ERR, "$logfile");
  my $error = "";
  while (<ERR>) {
    $error .= $_;
  }
  close(ERR);
  return scanerrorstring($error, $stackid, $stacks);
}

#
# Scan an error string and extract a stack trace if possible.
#
sub scanerrorstring {
  my ($errstr, $stackid, $stacks) = @_;
  my $inerror = 0;
  my $error = "";
  my $stack = "";
  my $linesleft = 100;  # max stack dump depth
  foreach $line (split('\n', $errstr)) {
    $_ = $line."\n";
    if (!$inerror) {
      if (/Exception in thread/) {
        $inerror = 1;
        if (/Exception in thread \".+\":/) {
          ($error) = /Exception in thread \".+\" (.+)[:]/;
        } else {
          ($error) = /Exception in thread .+[:] (.+)$/;
        }
        $stack = $_;
      } elsif (/java.lang.NullPointerException/) {
        $inerror = 1;
        $error = "java.lang.NullPointerException";
        $stack = $_;
      } elsif (/-- Stack --/) {
        $inerror = 2;
        $error = "-- Stack --";
      } elsif (/REF outside heap/) {
        $inerror = 3;
        $error = /validRef: (.+)$/;
        $stack = $_;
      }
    } elsif ($linesleft > 0) {
      if ((($inerror == 1) && /^\s+at .+[:]\d+[)]\s*$/) ||
          (($inerror == 2) && /^\s+L.+ at line \d+$/)) {
	if ((/sysFail/ || /Assert; fail/) && $error eq "-- Stack --") {
	   $error = "Assertion failure";
	}
#	$stack .= $stacklet."\n";
	$stack .= markupstackline($_); #$stacklet."\n";
        $linesleft--;
      } elsif ($inerror == 3) {
        $stack .= $_;
        if (/Dumping stack starting at frame with bad ref/) {
          $error = "Bad stack map";
        }
        if (/-- Stack --/) {
          $inerror = 2;
        }
        $linesleft--;
      } elsif (/-- Stack --/ || /Exception in thread/) {
        $linesleft = 0;
      }
    }
  }
  if ($stack) {
    $stack =~ s/\n/<br>/g;
    my $s;
    for ($s = 0; $s < @{$stacks} && ${$stacks}[$s] ne $stack; $s++) {};
    ${$stackid} = $s;
    if ($s == @{$stacks}) {
      ${$stacks}[$s] = $stack;
    }
  }
  return "$error";
}

#
# mark up a line from a stack trace with a link to the svn code
#
sub markupstackline {
  my ($stacklet) = @_;
  $stacklet =~ /^(.+)$/;
  my ($class, $line);
  if (/^\s+at/) {
    ($class, $line) = /^\s+at\s(\S+)[.][^.]+\(\S+[:](\d+)\)/;
    $class =~ s/[.]/\//g;
  } elsif (/^\s+L.+ at/) {
    ($class, $line) = /^\s+L(\S+)[;]\s.+ at line (\d+)/;
  }
  my $url;
  if ($class =~ /com.ibm.jikesrvm/) {
    $url = "$basesfcodeurl/rvm/src/$class.java?view=markup#l_$line";
  } elsif ($class =~ /org.mmtk/) {
    $url = "$basesfcodeurl/MMTk/src/$class.java?view=markup#l_$line";
  }
  if ($url) {
    if (/^\s+at/) {
      my ($base) = $stacklet =~ /(.+[:])\d+\)\s*$/;
      $stacklet = "$base<a href=\"$url\">$line</a>)\n";
    } elsif (/^\s+L.+ at/) {
      my ($base) = $stacklet =~ /(.+ at line )\d+\s*$/;
      $stacklet = "$base <a href=\"$url\">$line</a>\n";
    }
  }
  return "$stacklet";
}

#
# scan the MSG file for javadoc errors and opt compiler reports
#
sub getdetails {
  my ($basedir, $javadocerrs, $optdetails, $svnstamp, $svnrevision, $sanityconfig, $perfconfig) = @_;
  open(IN, "$basedir/MSG");
  my $injavadoc = 0;
  my $optlevel = -1;
  while (<IN>) {
    if (/Opt Level \d compilation breakdown/) {
      ($optlevel) = /Opt Level (\d) compilation breakdown/;
      $injavadoc = 0;
    } elsif ($injavadoc) {
      if (/nightShadow.doc.api.tmp_build.RVM.classes/) {
        my $str;
        ($str) = /RVM.classes.(.+)$/;
        push (@{$javadocerrs}, $str);
      }
    } elsif (/Javadoc Errors/) {
      $injavadoc = 1;
    } elsif (/svn checkout performed/) {
      (${$svnstamp}) = /svn checkout performed on (.+)\s*$/;
    } elsif (/Checked out revision/) {
      (${$svnrevision}) = /Checked out revision (\d+)[.]/;
    } elsif (/Sanity tests were specified in/) {
      (${$sanityconfig}) = /Sanity tests were specified in\s+(.+)\s*$/;
    } elsif (/Performance tests were specified in/) {
      (${$perfconfig}) = /Performance tests were specified in\s+(.+)\s*$/;
    } elsif ($optlevel >= 0) {
      ${$optdetails}[$optlevel] .= $_;
    }
  }
  close(IN);
}

#
# scan the MSG file for javadoc errors and opt compiler reports
#
sub getimagesize {
  my ($basedir, $imagesize) = @_;
  open(DU, "du -k $basedir/images/production/RVM.*.image|");
  while (<DU>) {
    my ($sz, $image) = /(\d+)\s+.+RVM[.](.+)[.]image/;
    ${$imagesize}{$image} = $sz;
  }
  close(DU);
}


#
# create the document header incl style
#
sub printhtmlhdr {
  my ($htmlstream) = @_;
  print $htmlstream "<style>\n";
  my $margin = "10px";
  print $htmlstream "body\n{\n\tmargin-left:        $margin;\n\tmargin-top:         $margin;\n\tmargin-right:       $margin;\n\tmargin-bottom:      $margin;\n\tfont-family:        verdana, arial, helvetica, sans-serif;\n\tfont-size:          x-small;\n\tfont-weight:        normal;\n\tbackground-color:   #FFFFFF;\n\tcolor:              #000000;\n}\n";
  print $htmlstream "tr\n{\n\tfont-size:          x-small;\n\tfont-family:        verdana, arial, helvetica, sans-serif;\n\tfont-weight:        normal;\n\tcolor:              #000000;\n}\n";
  print $htmlstream "td\n{\n\tfont-size:          x-small;\n\tfont-family:        verdana, arial, helvetica, sans-serif;\n\tfont-weight:        normal;\n\tcolor:              #000000;\n}\n";
  print $htmlstream "</style>\n";
  print $htmlstream "<body>\n";  
}

#
# create the document footer
#
sub printhtmlftr {
  my ($htmlstream) = @_;
  print $htmlstream "</body>\n";
}
 

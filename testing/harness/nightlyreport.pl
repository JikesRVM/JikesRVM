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

my $root = shift(@ARGV);
if (!(-e $root)) {
  $root = `pwd`;
  ($root) = $root =~ /^(.+)\s*$/;
}

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

# key variables
my @sanity = ();
my @error = ();
my @results = ();
my @stackid = ();
my @stacks = ();
my @cmd = ();
my %perf = ();
my %jvm98details = ();
my @javadocerrs = ();
my @optdetails = ();
my $svnstamp;
my $svnrevision;
my $sanityconfig;
my $perfconfig;
my $ran = 0;
my $passed = 0;

# dig out the data from the various log files...
#
getsanity($root, \@sanity, \@error, \@results, \@cmd, \@stackid, \@stacks, \$ran, \$passed);
getperf($root, \%perf, \%jvm98details);
getdetails($root, \@javadocerrs, \@optdetails, \$svnstamp, \$svnrevision, \$sanityconfig, \$perfconfig);

# produce the html summary
#
my $html;
#open($html, ">$root/results/".getdatestr($svnstamp).".html");
open($html, ">$root/results/report.html");
printhtmlhdr($html);
gensummary($html, \%perf, $ran, $passed, $svnstamp, $svnrevision, $sanityconfig, $perfconfig);
genbuildfailures($html, \@sanity, \@error, \@stackid);
gentestfailures($html, \@sanity, \@error, \@stackid);
genperfdetails($html, \%perf, \%jvm98details);
genstacks($html, \@sanity, \@error, \@cmd, \@stackid, \@stacks);
genjavadoc($html, \@javadocerrs);
genoptdetails($html, \@optdetails);
printhtmlftr($html);
close($html);

exit(0);

#
# generate html for the report summary
#
sub gensummary {
  my ($html, $perf, $ran, $passed, $svnstamp, $svnrevision, $sanityconfig, $perfconfig) = @_;
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
  
  my $svnurl = "http://svn.sourceforge.net/viewvc/jikesrvm?view=rev&revision=$svnrevision";
  print $html "<tr><td align=\"right\">Revision:<td><b><a href=\"$svnurl\">$svnrevision</a></b></tr>\n";
  print $html "<tr><td align=\"right\">Checkout at:<td>$svnstamp</tr>\n";
  print $html "<tr><td align=\"right\">Sanity config:<td>$sanityconfig</tr>\n";
  print $html "<tr><td align=\"right\">Performance config:<td>$perfconfig</tr>\n";
  print $html "</table>\n";
  print $html "<h3>Details</h3>\n";
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
  print $html "</ul>\n";
  print $html "<hr>\n";
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
  $errornum = 0;
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
# read in sanity details from run.log
#
sub getsanity {
  my ($basedir, $sanity, $error, $results, $cmd, $stackidx, $stacks, $ran, $passed) = @_;
  my $resultdir = "";
  my $resultfile = "";
  my $thiserror = "";
  my $thistest = 0;
  open(IN, "$basedir/results/run.log");
  while (<IN>) {
    my $build = "";
    my $bm = "";
    if (/are.* sane/) {
      ${$stackidx}[$thistest] = -1;
      if (/You are sane/) {
         ($build, $bm) = /You are sane (\S+) (.+)\s*$/;
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
  my $inerror = 0;
  my $error = "";
  my $stack = "";
  my $linesleft = 100;  # max stack dump depth
  while (<ERR>) {
    if (!$inerror) {
      if (/Exception in thread/) {
        $inerror = 1;
        if (/Exception in thread \".+\":/) {
          ($error) = /Exception in thread .+[:] (.+)$/;
        } else {
          ($error) = /Exception in thread \".+\" (.+)[:]/;
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
        $stack .= $_;
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
 

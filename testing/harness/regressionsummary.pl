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
# Produce an email which summarizes a nightly regression run.
#

my $reportrecipient = shift(@ARGV);

# constants etc
my @DAYS = ("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
my @SHORTDAYS = ("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");
my @SHORTMONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
my $COLORS = 16;
my $RED = "#FF0000";
my $GREEN = "#00FF00";
my $BLACK = "#000000";
my $NORMALFONT = "font-weight:normal;";
my $BOLDFONT = "font-weight:bold;";
my $MIMEBOUNDARY = "======".time()."======";
my $SVNURL = "http://svn.sourceforge.net/viewvc/jikesrvm?view=rev&revision=";
my $html = 1;
my $short = 1;

# these will need to change
my $regressiondomain = "anu.edu.au";
my $regressionhost = "rvmx86lnx64";
my $reporturl = "http://cs.anu.edu.au/people/Steve.Blackburn/jikesrvm";
#my $reportrecipient = "Steve.Blackburn\@anu.edu.au";
#my $reportrecipient = "Steve.Blackburn\@anu.edu.au, groved\@us.ibm.com, hindm\@us.ibm.com";
my $platform = "Linux.x86_64.32";

# initialize things
open($out, ">summary.eml");
#open($out, "|sendmail -t");
my $today = 1;
my %allsanity = ();
my %allperf = ();
my %bestperf = ();
my %allerrors = ();
my @allrevisions = ();
my $checkout = "";
my @javadocerrors = ();
my $datestring = "";

# grab the data and process it
$today = getdata(\%allsanity, \%allperf, \%allerrors, \@allrevisions, \$checkout, \@javadocerrors);
$datestring = getdatestringfromcheckout($checkout);
updatebestperf($today, \%allperf, \%bestperf);

# produce the html
printemailhdr($out, getpasses(-1, $today, \%allsanity), $platform);
if ($html) { printhtmlhdr($out); }
printsummary($out, $html, $checkout, $datestring);
printrevisions($out, $html, $today, $checkout, \@allrevisions);
printfailures($out, $html, 1, $today, $datestring, \%allsanity, \%allerrors);
printjavadoc($out, $html, $javadocerrors[$today], $datestring);
printweeklyoverview($out, $html, $today, \%allperf, \%bestperf, \%allsanity);
printsanitytable($out, $html, 0, "build", $short, \%allsanity);
printsanitytable($out, $html, 1, "benchmark", $short, \%allsanity);
if (0) { printfailures($out, $html, 0, $today, \%allsanity, \%allerrors);}
if ($html) { printhtmlftr($out); }
printemailftr($out);
exit(0);

#
# print the email header
#
sub printemailhdr {
  my ($out, $failures, $platform) = @_;
  my $subject = ($failures == 0) ? "SUCCEEDED" : "Failed $failures tests";
  $platform =~ s/[.]/\//g;
  $subject .= " [$platform]";
  print $out "From: regression\@$regressionhost.$regressiondomain\n";
  print $out "To: $reportrecipient\n";
  print $out "MIME-Version: 1.0\n";
  print $out "Subject: $subject\n";
#  print $out "Content-type: multipart/mixed; boundary=\"$MIMEBOUNDARY\"\n";
#  print $out "Content-Base: $reporturl/$regressionhost/\n";
#  print $out "\n--$MIMEBOUNDARY\n";
#  print $out "Content-Type: text/plain; charset=\"iso-8859-1\"\n";
#  print $out "Content-Transfer-Encoding: quoted-printable\n\n";
#  print $out "plain text version would be here\n";
#  print $out "\n--$MIMEBOUNDARY\n";
  print $out "Content-Type: text/html; charset=\"iso-8859-1\"\n";
  print $out "Content-Transfer-Encoding: 7bit\n";
  print $out "Content-Disposition: inline\n";
  print $out "\n";
}

#
# print the email footer
#
sub printemailftr {
  my ($out) = @_;
#  print $out "\n--$MIMEBOUNDARY\n";
}

#
# print the header/summary of this message
#
sub printsummary {
  my ($out, $html, $checkout, $datestring) = @_;
  if ($html) {
    print $out "<h2>Regression summary for $regressionhost, $checkout</h2>\n";
    print $out "Details are available <a href=\"$reporturl/$regressionhost/$datestring.html\">here</a><br>\n";
  } else {
    print $out "Regression summary for $regressionhost, $checkout\n";
    print $out "Details are available at: $datestring.html\n";
  }
}

#
# print the failures, either in summary, or as a full list
#
sub printrevisions {
  my ($out, $html, $today, $checkout, $allrevisions) = @_;
  my $latestrev = ${$allrevisions}[$today];
  my $rev = ${$allrevisions}[(($today - 1) % 7)] + 1;
  if ($html) {
    print $out "Checkout at: $checkout<br>\n";
  } else {
    print $out "Checkout at: $checkout\n";
  } 
  if ($html) {
    print $out "Revisions covered by this sanity run: ";
  } else {
    print $out "Revisions covered by this sanity run: ";
  } 
  while ($rev <= $latestrev) {
    if ($html) {
      print $out  "<a href=\"$SVNURL$rev\">$rev<\/a>";
      print $out  (($rev != $latestrev) ? ", " : "<br>\n");
    } else {
      print $out $rev;
      print $out (($rev != $latestrev) ? ", " : "\n");
    }
    $rev++;
  }
}

#
# print the failures, either in summary, or as a full list
#
sub printfailures {
  my ($out, $html, $summary, $today, $datestring, $allsanity, $allerrors) = @_;
  my %weeklysane = ();
  my %weeklyinsane = ();
  aggregatesanity("day", \%allsanity, \%weeklysane, \%weeklyinsane);

  my @allfailures = getfailures($today, "all failures", $allsanity, \%weeklysane, \%weeklyinsane);
  my @allsuccesses = getfailures($today, "all successes", $allsanity, \%weeklysane, \%weeklyinsane);
  my $pass = $#allsuccesses + 1;
  my $fail = $#allfailures + 1;
  my $run = $pass+$fail;
  my $pct = int(100*($fail/$run));

  my $str;
  if ($summary) {
    $str = "Failed $fail/$run ($pct%)";
  } else {
    $str = "$fail failures";
  }
  if ($html) {
    print $out "<h2>$str</h2>\n";
  } else {
    print $out "$str\n";
  }
  printfailuresummary($out, $html, $today, $datestring, $RED, "new failures", $allsanity, $allerrors, \%weeklysane, \%weeklyinsane);
  if ($summary) {
    printfailuresummary($out, $html, $today, $datestring, $GREEN, "new successes", $allsanity, $allerrors, \%weeklysane, \%weeklyinsane);
  } else {
    printfailuresummary($out, $html, $today, $datestring, $BLACK, "transient failures", $allsanity, $allerrors, \%weeklysane, \%weeklyinsane);
    printfailuresummary($out, $html, $today, $datestring, $BLACK, "persistent failures", $allsanity, $allerrors, \%weeklysane, \%weeklyinsane);
  }
}

#
# update performance bests
#
sub updatebestperf {
  my ($today, $allperf, $bestperf) = @_;
  getbestperf(($today - 1) % 7, $bestperf);
  my $key;
  foreach $key (sort keys %{$allperf}) {
    my ($day,$bm) = split(/:/, $key);
    if ($day == $today) {
      if (${$bestperf}{$bm} < ${$allperf}{"$today:$bm"}) {
        ${$bestperf}{$bm} = ${$allperf}{"$today:$bm"};
      }
    }
  }
  open(OUT, ">results/best.txt");
  foreach $bm (sort keys %{$bestperf}) {
    print OUT "$bm ".${$bestperf}{$bm}."\n";
  }
  close(OUT);
}

#
# extract performance bests from a particular day's archive
#
sub getbestperf {
  my ($day, $bestperf) = @_;
  open(IN, "tar xzf ../archive/$DAYS[$day].$platform.tar.gz results/best.txt --to-stdout|");
  my ($bm, $score);
  while (<IN>) {
    if (($bm, $score) = /(\S+)\s+([0-9.]+)/) {
      ${$bestperf}{$bm} = $score;
    }
  }
  close(IN);
}

#
# print summary info on javadoc failures
#
sub printjavadoc {
  my ($out, $html, $javadocerrors, $datestring) = @_;
  my $str = "$javadocerrors Javadoc errors";
  my $jdurl = "$reporturl/$regressionhost/$datestring.html\#javadoc";
  if ($html) {
    print $out "<h2>$str</h2>\n";
    print $out "Details <a href=\"$jdurl\">here</a><br>\n";
  } else {
    print $out $str;
    print $out "Details here: $jdurl\n"
  }
}

#
# print a day-by-day overview of sanity and performance numbers
#
sub printweeklyoverview {
  my ($out, $html, $today, $allperf, $bestperf, $allsanity) = @_;
  my $str = "Day-by-day overview";
  if ($html) { 
    print $out "<h2>$str</h2>\n";
    printtablehdr($out, "result"); 
  } else {
    print $out "$str\n";
  }
  printsanityoverview($out, $html, $today, $allsanity);
  printperfoverview($out, $html, $today, "jvm98 score", "jvm98-bottomline", $allperf, $bestperf);
  printperfoverview($out, $html, $today, "jbb2000 score", "jbb2000", $allperf, $bestperf);
  if ($html) { print $out "</table>\n";  }
}

#
# print a day-by-day overview of performance
#
sub printperfoverview {
  my ($out, $html, $today, $name, $bmkey, $allperf, $bestperf) = @_;
  if ($html) {
    print $out "<tr>\n";
    print $out "\t<td align=\"right\">$name\n";
  } else {
    print $out "$name ";
  }
  my $d;
  for ($d = 1; $d <= 7; $d++) {
    my $day = ($today + $d) % 7;
    my $score = ${$allperf}{"$day:$bmkey"};
    my $textcolor = "";
    if ($score == "") { 
      $score = "-";
    } else {
      my $best = ${$bestperf}{$bmkey};
      my $delta = int (100*($score - $best)/$best);
      if ($delta < 0) {
        $score = "$score ($delta\%)";
        if ($delta <= -5) {
          $textcolor = "color: red;";
          if ($delta <= -10) {
            $textcolor .= "font-weight: bold;";
          }
        }
      } else {
        $textcolor = "color: green;";
        if ($score == $best) {
           $textcolor .= "font-weight: bold;";
        }
      }
    }
    if ($html) {
      $textcolor .= ($day == $today) ? "" : "background-color: silver;";
      print $out "\t<td style=\"$textcolor\">$score\n";
    } else {
      print $out "$score ";
    }
  }
  if ($html) {
    print $out "</tr>\n";
  } else {
    print $out "\n";
  }
}

#
# print a day-by-day overview of sanity
#
sub printsanityoverview {
  my ($out, $html, $today, $allsanity) = @_;
  my $name = "sanity";
  if ($html) {
    print $out "<tr>\n";
    print $out "\t<td align=\"right\">$name\n";
  } else {
    print $out "$name ";
  }
  my $d;
  for ($d = 1; $d <= 7; $d++) {
    my $day = ($today + $d) % 7;
    my $bad = getpasses(-1, $day, $allsanity);
    my $run = $bad+getpasses(1, $day, $allsanity);
    my $str = "$bad/$run";
    if ($html) {
      my $color = ($day == $today) ? "" : "background-color: silver;";
      print $out "\t<td style=\"$color\">$str\n";
    } else {
      print $out "$str ";
    }
  }
  if ($html) {
    print $out "</tr>\n";
  } else {
    print $out "\n";
  }
}

#
# print a summary list of passes/fails for a given criteria
#
sub printfailuresummary {
  my ($out, $html, $today, $datestring, $color, $type, $allsanity, $allerrors, $weeklysane, $weeklyinsane) = @_;
  my @list = getfailures($today, $type, $allsanity, $weeklysane, $weeklyinsane);
  my $str = ($#list + 1)." $type";
  if ($html) {
    print $out "<h3><font color=\"$color\">$str</font></h3>\n";
  } else {
    print $out "$str\n";
  }
  if ($html) {
    print $out "<table columns=\"3\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
  }
  foreach $fail (sort @list) {
     ($bm,$build) = split(/:/, $fail);
     my $href = "";
     my $error;
     if (${$allsanity}{"$today:$build:$bm"} == -1) {
       $err = ${$allerrors}{"$today:$build:$bm"};
       ($href,$error) = split(/:/, $err, 2);
       if ($href) {
         $href = "$reporturl/$regressionhost/$datestring.html$href";
       }
     }
     if ($bm eq "") { 
       $bm = "All benchmarks";
       if ($html) {
          $bm = "<b>$bm</b>";
       }
     }
     if ($html) {
       print $out "<tr>\n";
       print $out "\t<td align=\"right\" style=\"font-style:italic\">$build\n";
       print $out "\t<td>$bm\n";
       if ($error) {
         if ($href) {
           print $out "\t<td><a href=\"$href\">$error</a>\n";
         } else {
           print $out "\t<td>$error\n";
         }
       }
       print $out "</tr>\n";
     } else {
       print $out "$build $bm $error\n";
     }
  }
  if ($html) {
    print $out "</table>\n";
  }
}

#
# get a list of successes or failures according to some criteria
#
sub getfailures {
  my ($day, $type, $allsanity, $sane, $insane) = @_;
  my @failures = ();
  my $pass = 0;
  my $fail = 0;
  foreach $key (keys %{$allsanity}) {
    ($kday,$kbuild,$kbm) = split(/:/, $key);
    if ($kday eq $day) {
      if (${$allsanity}{$key} == -1) {
        $fail++;
        if ($type eq "new failures" && ${$insane}{"$kbuild:$kbm"} == 1) {
          push (@failures, "$kbm:$kbuild");
        } elsif ($type eq "transient failures" && ${$sane}{"$kbuild:$kbm"} > 1) {
          push (@failures, "$kbm:$kbuild");
        } elsif ($type eq "persistent failures" && ${$insane}{"$kbuild:$kbm"} == 7) {
          push (@failures, "$kbm:$kbuild");
        } elsif ($type eq "all failures") {
          push (@failures, "$kbm:$kbuild");
        }
      } else {
        $pass++;
        if ($type eq "new successes" && ${$sane}{"$kbuild:$kbm"} == 1) {
          push (@failures, "$kbm:$kbuild");
        } elsif ($type eq "all successes") {
          push (@failures, "$kbm:$kbuild");
        }
      }
    }
  }
#  print "==> pass: $day $pass fail: $fail\n";
  return @failures;
}

#
# get a list of successes or failures according to some criteria
#
sub getpasses {
  my ($pass, $day, $allsanity) = @_;
  my $count = 0;
  foreach $key (keys %{$allsanity}) {
    ($kday,$kbuild,$kbm) = split(/:/, $key);
    if ($kday eq $day) {
      if (${$allsanity}{$key} == $pass) {
        $count++;
      }
    }
  }
  return $count;
}


#
# print a sanity table for a week
#
sub printsanitytable {
  my ($out, $html, $truncate, $type, $short, $allsanity) = @_;
  my %sane = ();
  my %insane = ();
  aggregatesanity($type, $allsanity, \%sane, \%insane);
  printonesanitytable($out, $html, $truncate, $type, $short, \%sane, \%insane);
}

#
# print sanity table
#
sub printonesanitytable {
  my ($out, $html, $truncate, $label, $short, $daytargetsane, $daytargetinsane) = @_;
  my %all = ();
  my %allsane = ();
  my %allinsane = ();

  # aggregate results
  foreach $key (keys %{$daytargetsane}) {
    ($day,$tgt) = split(/:/, $key);
    $all{$tgt} = 1;
    $allsane{$tgt} = $allsane{$tgt} + ${$daytargetsane}{$key};
  }
  foreach $key (keys %{$daytargetinsane}) {
    ($day,$tgt) = split(/:/, $key);
    $all{$tgt} = 1;
    $allinsane{$tgt} = $allinsane{$tgt} + ${$daytargetinsane}{$key};
  }

  if ($html) {
    print $out "<h2>Sanity regression: by $label</h2>\n";
    printtablehdr($out, $label);
  }

  my $target;
  foreach $target (sort { ($allsane{$a}/($allsane{$a}+$allinsane{$a})) <=> ($allsane{$b}/($allsane{$b}+$allinsane{$b}))} keys %all) {
    my $skip = 0;
    if ($truncate) {
	  $skip = 1;
	  for ($d = 1; $d <= 7; $d++) {
        my $day = ($today + $d) % 7;
        my $good = ${$daytargetsane}{"$day:$target"};
        my $bad = ${$daytargetinsane}{"$day:$target"};
	    if ($good == 0 || $bad > 0) {
	      $skip = 0;
	    }
	  }
    }
    if (!$skip) {
      if ($html) {
        print $out "<tr>\n";
        print $out "\t<td align=\"right\" $width>$target\n";
      } else {
        print $out "$target ";
      }
      for ($d = 1; $d <= 7; $d++) {
        my $day = ($today + $d) % 7;
        my $good = ${$daytargetsane}{"$day:$target"};
        my $bad = ${$daytargetinsane}{"$day:$target"};
        my $ratio = ($good == 0) ? 0 : $good/($good+$bad);
        my $str = "";
        if ($good == 0) { 
          $str = "-"; 
        } else {
          if ($short) {
            $str = sprintf("%d\%", int(100*$ratio));
          } else {
            $str = sprintf("%d\%(%d/%d)", int(100*$ratio), $good, ($good+$bad));
          }
        }
        if ($html) {
          my $style = getcolorstyle($ratio); 
          $style .= ($day == $today) ? "border-left: 1px solid white;" : "";
          print $out "\t<td align=\"center\" $width style=\"$style\">$str\n";
        } else {
          print $out "$str ";
        }
      }
      if ($html) {
        print $out "</tr>\n";
      } else {
        print $out "\n";
      }
    }
  }
  if ($html) {
    print $out "</table>\n";
  }
}

#
# read in all data from the respective sources
#
sub getdata {
  my ($allsanity, $allperf, $allerrors, $allrevisions, $checkout, $javadocerrors) = @_;
  my $source = "results/report.html";
  my $today = gettodayfromsvn($source, $checkout);
  my @errors = "";
  getdaydata($allsanity, $allperf, $allerrors, $allrevisions, $checkout, $today, $source,$javadocerrors);
  for ($day = 0; $day < 7; $day++) {
    if ($day != $today) {
      $source = "tar xzf ../archive/$DAYS[$day].$platform.tar.gz results/report.html --to-stdout|";
      getdaydata($allsanity, $allperf, $allerrors, $allrevisions, $checkout, $day, $source,$javadocerrors);
    }
  }
  return $today;
}

#
# get a standard date string from the svn checkout string
#
sub getdatestringfromcheckout {
  my ($checkout) = @_;
  $checkout =~ s/  / /;
  my ($wday, $mon, $mday, $time, $tz, $year) = split(/ /, $checkout);
  my $month = 0;
  for ($month = 0; ($month < 12) && ($mon ne $SHORTMONTHS[$month]); $month++) {};
  return sprintf("%04d%02d%02d", $year, $month+1, $mday);
}
  

#
# dig out the day number (perl convention) from the svn timestamp in today's log
#
sub gettodayfromsvn {
  my ($source, $checkout) = @_;
  open (IN, "$source");
  my $value;
  while (<IN>) {
    if (($value) = /Checkout at:<td>(.+)<\/tr>/) {
      ${$checkout} = $value;
      my ($dayname) = $value =~ /^\s*([A-Z][a-z]+)\s.+\s\d+/;
      my $day;
      for ($day = 0; $day < 7; $day++) {
        if ($SHORTDAYS[$day] eq $dayname) {
          return $day;
        }
      }
    }
  }
  close(IN);
  print "=====> DAY NOT PARSED $source $checkout\n";
}

#
# Dig out a day's summary information from the given source
#
sub getdaydata {
  my ($allsanity, $allperf, $allerrors, $allrevisions, $checkout, $day, $source, $javadocerrors) = @_;
  my $regressionfailures = 0;
  my $regressionsuccesses = 0;
  my $buildfailures = 0;
  my $value;
  my $valueb;
  my $pass = 0;
  my $fail = 0;
  open (IN, "$source");
  while (<IN>) {
    if ($day == -1 && (($value) = /Checkout at:<td>(.+)<\/tr>/)) {
      ${$checkout} = $value;
      $day = getdayfromsvn($value);
    } elsif (($value, $valueb) = /Regression tests:<td><b>.+Failed (\d+)<\/font>\/(\d+)<\/b>/) {
#      print "--->$value/$valueb<---\n";
    } elsif (($value) = /Regression tests:<td><b>.+PASSED (\d+)\//) {
#      print "--->$value/$value<---\n";
    } elsif (($value) = /Revision:<td><b>.+>(\d+)<\/a><\/b>/) {
      ${$allrevisions}[$day] = $value;
    } elsif (($value) = /JavaDoc errors:<td><b>(\d+)<\/b>/) {
#      print "--->$day--$value/$valueb<---\n";
      ${$javadocerrors}[$day] = $value;
    }  elsif (($value) = /jbb2000 score:<td><b>(\d+.\d+)<\/b>/) {
      ${$allperf}{"$day:jbb2000"} = $value;
    } elsif (($value) = /jvm98 best:<td><b>(\d+.\d+)<\/b>/) {
      $value = int($value + 0.5); # round
      ${$allperf}{"$day:jvm98-bottomline"} = $value;
    } elsif (($value) = /jvm98 first:<td><b>(\d+.\d+)<\/b>/) {
      $value = int($value + 0.5); # round
      ${$allperf}{"$day:jvm98-firstrun"} = $value;
    } elsif (/Build Failures<\/a><\/h2>/) {
      $buildfailures = 1;
      $regressionsuccesses = 0;
      $regressionfailures = 0;
    } elsif (/Regression Test Failures<\/a><\/h2>/) {
      $regressionfailures = 1;
      $regressionsuccesses = 0;
      $buildfailures = 0;
    } elsif (/Regression Test Successes<\/a><\/h2>/) {
      $regressionfailures = 0;
      $regressionsuccesses = 1;
      $buildfailures = 0;
    } elsif ($buildfailures && /\s+<td>\d+/) {
      $_ = <IN>;
      my ($build) = /\s+<td>(.+)\s*$/;
      $_ = <IN>;
      my ($error) = /\s+<td>(.+)\s*$/;
      ${$allsanity}{"$day:$build:"} = -1;
    } elsif ($regressionfailures && (($value) = /\s+<td><a id="(fail_\d+)">\d+<\/a>/)) {
      my $id = $value;
      $_ = <IN>;
      my ($build) = /\s+<td>(.+)\s*$/;
      $_ = <IN>;
      my ($bm) = /\s+<td>(.+)\s*$/;
      $_ = <IN>;
      my ($error, $tag);
      if (/href=["]/) {
        ($tag, $error) = /\s+<td>.+["](.+)["]>(.+)<\/a>\s*$/;
      } else {
        ($error) = /\s+<td>(.+)\s*$/;
      }
      ${$allsanity}{"$day:$build:$bm"} = -1;
      ${$allerrors}{"$day:$build:$bm"} = "$tag:$error";
    } elsif ($regressionsuccesses && (($value) = /\s+<td><a id="(pass_\d+)">\d+<\/a>/)) {
      my $id = $value;
      $_ = <IN>;
      my ($build) = /\s+<td>(.+)\s*$/;
      $_ = <IN>;
      my ($bm) = /\s+<td>(.+)\s*$/;
      ${$allsanity}{"$day:$build:$bm"} = 1;
    }
  }
  close IN;
}


#
# aggregate sanity stats for one day, either by build or benchmark
#
sub aggregatesanity {
  my ($aggregateby, $allsanity, $sane, $insane) = @_;
  foreach $key (keys %{$allsanity}) {
    ($day,$build,$bm) = split(/:/, $key);
    my $newkey;
    if ($aggregateby eq "build") {
       $newkey = "$day:$build";
    } elsif ($aggregateby eq "benchmark") {
       $newkey = "$day:$bm";
    } else {
       $newkey = "$build:$bm";
    }
    if ($newkey ne "$day:") {
      if (${$allsanity}{$key} == 1) {
        ${$sane}{$newkey} = ${$sane}{$newkey} + 1;
      } else {
        ${$insane}{$newkey} = ${$insane}{$newkey} + 1;
      }
    }
  }
}

#
# aggregate sanity stats for a week
#
sub aggregateweeksanity {
  my ($usebuild, $allsanity, $weeksane, $weekinsane) = @_;
  foreach $key (keys %{$allsanity}) {
    ($day,$build,$bm) = split(/:/, $key);
    $newkey = ($usebuild) ? "$day:$build" : "$day:$bm";
    if ($newkey ne "$day:") {
      if (${$allsanity}{$key} == 1) {
        ${$daybuildsane}{$newkey} = ${$daybuildsane}{$newkey} + 1;
      } else {
        ${$daybuildinsane}{$newkey} = ${$daybuildinsane}{$newkey} + 1;
      }
    }
  }
}

#
# create the document header incl style
#
sub printhtmlhdr {
  my ($html) = @_;
  print $html "<html>\n";
  print $html "<style>\n";
  my $margin = "10px";
  print $html "body\n{\n\tmargin-left:        $margin;\n\tmargin-top:         $margin;\n\tmargin-right:       $margin;\n\tmargin-bottom:      $margin;\n\tfont-family:        verdana, arial, helvetica, sans-serif;\n\tfont-size:          x-small;\n\tfont-weight:        normal;\n\tbackground-color:   #FFFFFF;\n\tcolor:              #000000;\n}\n";
  print $html "tr\n{\n\tfont-size:          x-small;\n\tfont-family:        verdana, arial, helvetica, sans-serif;\n\tfont-weight:        normal;\n\tcolor:              #000000;\n}\n";
  print $html "td\n{\n\tfont-size:          x-small;\n\tfont-family:        verdana, arial, helvetica, sans-serif;\n\tfont-weight:        normal;\n\tcolor:              #000000;\n}\n";
  print $html "</style>\n";
  print $html "<body>\n";  
}

#
# create the document footer
#
sub printhtmlftr {
  my ($out) = @_;
  print $out "</body>\n</html>";
}

#
# create table header
#
sub printtablehdr {
  my ($html, $label) = @_;
  my $tbl = "<table columns=\"8\" style=\"border-collapse:collapse;font-weight:normal;\">\n";
  my $hdr = "<tr style=\"\">\n";
  my $row = "<th align=\"center\" $width style=\"font-weight:normal;font-style:italic\">$label</th>\n";
  for ($d = 1; $d <= 7; $d++) {
    my $day = ($today + $d) % 7;
    my $font = ($day==$today) ? $BOLDFONT : $NORMALFONT;
    $row .= "<th align=\"center\" $width style=\"$font\">".$SHORTDAYS[$day]."</th>\n";
  }
  $row .= "</tr>\n";
  print $html "$tbl$hdr$row";
}

#
# produce a color style statement given a success ratio
#
sub getcolorstyle {
  my ($ratio) = @_;
  my $green;
  my $red;

  if ($ratio > 0.5) {
    $green = (256/($COLORS/2))*int(($COLORS/2) * $ratio);
    if ($green != 0) { $green = $green - 1; }
    $red = (255-$green)/2;
  } else {
    $red = (256/($COLORS/2))*int(($COLORS/2) * (1-$ratio));
    if ($red != 0) { $red = $red - 1; }
    $green = (255-$red)/2;
  }
  return "background-color: rgb($red,$green,0);";
}

dnl -*- coding: iso-8859-1 ; mode: m4 ;-*-
dnl Copyright © IBM Corp. 2003
dnl
dnl $Id$
dnl
dnl This file contains the files used in building the user guide,
dnl in order of appearance.  The in-order appearance is so that we can
dnl generate TAGS tables that will be useful to us.
dnl
dnl The only reason we keep this as M4 source is so that we can
dnl have comments in it.  Running it through m4 will strip the comments.
dnl
dnl @author Steven Augart
dnl @date 2 December 2003
dnl
userguide.tex
trademarks.tex
intro.tex
installation.tex
running.tex
regression.tex
eclipse.tex
gcspy.tex
componentOverview.tex
vmdetails.tex
threads.tex
callbacks.tex
basedetails.tex
optdetails.tex
JMTk.tex
aosdetails.tex
magic.tex
preprocessor.tex
jni.tex
libraries.tex
debugging.tex
profiling.tex
performance.tex
codingstyle.tex
codingstyle-nonjava.tex
faq.tex
ack.tex
cmdline.tex
licenses.tex
cpl-text.tex
contributions.tex
dnl Here are the ones that just include macro definitions and so forth
trademark-macros.tex
remark.tex
dnl And here are the automatically generated ones.
adaptive_options.tex
base_options.tex
jmtk_options.tex
opt_options.tex

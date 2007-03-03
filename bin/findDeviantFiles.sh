#!/bin/bash

export BIN_DIR="`dirname "$0"`"

export MATCH="$BIN_DIR/findMatchedFiles.pl"
export FILTER="awk -f $BIN_DIR/headerExceptionsFilter.awk"

echo "Missing @author"
find . -name .svn -prune -o -name generated -prune -o -name target -prune -o -name dist -prune -o -name results -prune -o -type f ! -name '.project' ! -name '.classpath' ! -name 'build.xml' -print -exec grep -c "@author" {} \; | $MATCH | $FILTER    
echo "Missing Copyright"
find . -name .svn -prune -o -name generated -prune -o -name target -prune -o -name dist -prune -o -name results -prune -o -type f ! -name '.project' ! -name '.classpath' ! -name 'build.xml' -print -exec egrep -c "\([cC]\) Copyright|Copyright ?|Copyright \([cC]\)" {} \; | $MATCH | $FILTER    

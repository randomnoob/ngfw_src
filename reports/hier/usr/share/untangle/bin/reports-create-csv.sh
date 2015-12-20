#!/bin/bash

function doHelp() {
    echo "$0 [options]"
    echo "required options: "
    echo " -f output_file   (filename to write)"
    echo " -d directory     (directory name inside zip containing csv)"
    echo " -r tablename     (backup all tables that include this string)"            
    echo "optional options: "
    echo " -h              (help)"
    echo
}

while getopts "r:f:d:h" opt; do
  case $opt in
    h) doHelp;exit 0;;
    f) FILE=$OPTARG;;
    d) DIR=$OPTARG;;
    r) REGEX=$OPTARG;;
  esac
done

if [ -z "$FILE" ] ; then
    doHelp; 
    exit 1;
fi
if [ -z "$DIR" ] ; then
    doHelp; 
    exit 1;
fi
if [ -z "$REGEX" ] ; then
    doHelp; 
    exit 1;
fi

TMPDIR="`mktemp -d -t csvXXXXXXXX`"

if [ -z "$TMPDIR" ] ; then
    echo "Failed to create tmp dir"
    exit 1
fi

mkdir -p $TMPDIR/$DIR

psql -U postgres -d uvm -t -c "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = 'reports' AND tablename LIKE '%${REGEX}%'" | \
    while read tablename ; do
        if [ -z "$tablename" ] ; then
            continue
        fi
        echo "Creating csv: ${tablename}.csv ..." 
        psql -U postgres -d uvm -A -F"," -c "select * from reports.${tablename}" > $TMPDIR/$DIR/${tablename}.csv
    done
                                                                                                                                                
cd $TMPDIR
echo "Creating zip: $FILE"
zip -r $FILE $DIR

rm -rf $TMPDIR/$DIR
rmdir $TMPDIR

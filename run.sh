#!/bin/sh

CP=${CP:-build/resources/main}

for l in build/libs/*.jar 
do 
	CP=$CP:$l 
done 

for l in libs/* 
do 
	CP=$CP:$l 
done 

CP=$CP:build/libs

RAM_SIZE=$( free -g | awk 'NR==2 {print $4}' )
BATCH_SIZE=$( free -g | awk 'NR==2 {print $4*1500}' )

if [ $RAM_SIZE -eq 0 ]
then
	RAM_SIZE=1
	BATCH_SIZE=1000
fi

if [ $# -gt 0 ]
then
	echo "Loading $1 into aggregator"
	JAVA_ARGS="com.rc.agg.LiveAggregatorFile $1"
else
	JAVA_ARGS="com.rc.agg.LiveAggregatorRandom ${BATCH_SIZE} 20 200"
fi

java -cp $CP \
	-Xmx${RAM_SIZE}g \
	-XX:+UseG1GC \
	-XX:+UseStringDeduplication \
	-XX:MaxGCPauseMillis=200 \
	${JAVA_ARGS}

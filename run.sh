#!/bin/sh


for l in build/libs/* 
do 
	CP=$CP:$l 
done 

for l in libs/* 
do 
	CP=$CP:$l 
done 

RAM_SIZE=$( free -g | awk 'NR==2 {print $4}' )
BATCH_SIZE=$( free -g | awk 'NR==2 {print $4*1666}' )

if [ $# -gt 0 ]
then
	echo "Loading $1 into aggregator"
	JAVA_ARGS="com.rc.agg.LiveAggregatorFile $1"
else
	JAVA_ARGS="com.rc.agg.LiveAggregatorRandom ${BATCH_SIZE} 20 300"
fi

java -cp $CP \
	-Xmx${RAM_SIZE}g \
	-XX:+UseG1GC \
	-XX:+UseStringDeduplication \
	-XX:MaxGCPauseMillis=200 \
	${JAVA_ARGS}

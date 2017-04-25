

for l in libs/* 
do 
	CP=$CP:$l 
done 

java -cp $CP -Xmx10G -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200 com.rc.agg.LiveAggregatorRandom 

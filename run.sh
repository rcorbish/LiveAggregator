

for l in libs/* 
do 
	CP=$CP:$l 
done 

java -cp $CP com.rc.agg.LiveAggregatorRandom 

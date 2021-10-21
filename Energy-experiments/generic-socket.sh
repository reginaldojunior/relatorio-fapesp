#!/bin/bash
function Y {
  #Usage: $0 FILE ALGORITHM RATE
  Memory=16G
  CORES="0,1,2,3"
  export MOA_HOME=/home/reginaldojunior/Documentos/UFscar/Parallel-Classifier-MOA/moa-full/target/moa-release-2019.05.1-SNAPSHOT/
  export RESULT_DIR=/home/reginaldojunior/Documentos/UFscar/experimentos/results/$3
  export REMOTE_DIR=/home/reginaldojunior/Documentos/UFscar/relatorio-fapesp/
  mkdir -p $RESULT_DIR
  faux=${1##*\/}
  onlyname=${faux%%.*}
  date +"%d/%m/%y %T"
  date +"%d/%m/%y %T" >> exper_order-Xeon-16.log
  echo "$2 $1 $3"
  sleep 3
  echo "ssh-${onlyname}-${2##*.}-${3}" >> ${RESULT_DIR}/ssh-log
  echo "java ChannelServer 192.168.0.11 9004 ${REMOTE_DIR}${1##*\/} $3 >> ${RESULT_DIR}/ssh-log &"
  java ChannelServer 192.168.0.11 9004 ${REMOTE_DIR}${1##*\/} $3 >> ${RESULT_DIR}/ssh-log &
  sleep 5
  if [[ $2 == *"MAX"* ]]; then
    #CHUNK
    IDENT="timedchunk"
    echo "$RESULT_DIR/${IDENT}-${onlyname}-${2##*.}-100-16-500-${3}" >> exper_order-Xeon-16.log
    echo "$RESULT_DIR/${IDENT}-${onlyname}-${2##*.}-100-16-500-${3}"
    numactl --physcpubind=${CORES} java -Xshare:off -XX:+UseParallelGC -Xmx$Memory -cp $MOA_HOME/lib/:$MOA_HOME/lib/moa.jar moa.DoTask "ChannelChunksTIMED -l ($2 -s 100 -c 4) -s (ArffFileStream -f ${REMOTE_DIR}$1) -c 500 -e (BasicClassificationPerformanceEvaluator -o -p -r -f) -i -1 -d $RESULT_DIR/dump-${onlyname}-${2##*.}-100-4-500-${3}" > ${RESULT_DIR}/term-${onlyname}-${2##*.}-100-16-500-${3}
  elif [[ ${2} == *"RUNPER"* ]]; then
    #PARALLEL
    IDENT="timedinterleaved"
    echo "$RESULT_DIR/${IDENT}-${onlyname}-${2##*.}-100-16-1-${3}" >> exper_order-Xeon-16.log
    echo "$RESULT_DIR/${IDENT}-${onlyname}-${2##*.}-100-16-1-${3}"
    numactl --physcpubind=${CORES} java -Xshare:off -XX:+UseParallelGC -Xmx$Memory -cp $MOA_HOME/lib/:$MOA_HOME/lib/moa.jar moa.DoTask "ChannelTIMED -l ($2 -s 100 -c 4) -s (ArffFileStream -f ${REMOTE_DIR}$1) -e (BasicClassificationPerformanceEvaluator -o -p -r -f) -i -1 -d $RESULT_DIR/dump-${onlyname}-${2##*.}-100-4-1-${3}" > ${RESULT_DIR}/term-${onlyname}-${2##*.}-100-16-1-${3}
  else
    #SEQUENTIAL OR PARALLEL
    IDENT="timedinterleaved"
    echo "$RESULT_DIR/${IDENT}-${onlyname}-${2##*.}-100-1-1-${3}" >> exper_order-Xeon-16.log
    echo "$RESULT_DIR/${IDENT}-${onlyname}-${2##*.}-100-1-1-${3}"
    numactl --physcpubind=0 java -Xshare:off -XX:+UseParallelGC -Xmx$Memory -cp $MOA_HOME/lib/:$MOA_HOME/lib/moa.jar moa.DoTask "ChannelTIMED -l ($2 -s 100) -s (ArffFileStream -f ${REMOTE_DIR}$1) -e (BasicClassificationPerformanceEvaluator -o -p -r -f) -i -1 -d $RESULT_DIR/dump-${onlyname}-${2##*.}-100-1-1-${3}" > ${RESULT_DIR}/term-${onlyname}-${2##*.}-100-1-1-${3}
  fi
  echo ""
  date +"%d/%m/%y %T"
  date +"%d/%m/%y %T" >> exper_order-Xeon-16.log
}

function X {
  #Usage: $0 FILE ID RS RP RC
  declare -a algs=(
  "meta.AdaptiveRandomForestSequential" "meta.AdaptiveRandomForestExecutorRUNPER" "meta.AdaptiveRandomForestExecutorMAXChunk"
  "meta.OzaBag" "meta.OzaBagExecutorRUNPER" "meta.OzaBagExecutorMAXChunk"
  "meta.OzaBagAdwin" "meta.OzaBagAdwinExecutorRUNPER" "meta.OzaBagAdwinExecutorMAXChunk"
  "meta.LeveragingBag" "meta.LBagExecutorRUNPER" "meta.LBagExecutorMAXChunk"
  "meta.OzaBagASHT" "meta.OzaBagASHTExecutorRUNPER" "meta.OzaBagASHTExecutorMAXChunk"
  "meta.StreamingRandomPatches" "meta.StreamingRandomPatchesExecutorRUNPER" "meta.StreamingRandomPatchesExecutorMAXChunk"
  )
  if [[ $2 == *"ARF"* ]]; then
    ID=0
  elif [[ $2 == "OBag" ]]; then
    ID=3
  elif [[ $2 == "OBagAd" ]]; then
    ID=6
  elif [[ $2 == "LBag" ]]; then
    ID=9
  elif [[ $2 == "OBagASHT" ]]; then
    ID=12
  elif [[ $2 == "SRP" ]]; then
    ID=15
  fi
  #Y $1 ${algs[${ID}]} $3
  #Y $1 ${algs[$(( ID+1 ))]} $4
  Y $1 ${algs[$(( ID+2 ))]} $5
}

#Usage: $0 PATH 
# dataset, algoritmo, taxa de instancias por segundo para produção, 10%, 50%, 90%
X $1airlines.arff ARF 161 244 1046
# X $1airlines.arff ARF 89 135 581
# X $1airlines.arff ARF 17 27 116
# X $1airlines.arff LBag 146 345 1429
# X $1airlines.arff LBag 81 191 794
# X $1airlines.arff LBag 16 38 158
# X $1airlines.arff SRP 148 237 873
# X $1airlines.arff SRP 82 132 485
# X $1airlines.arff SRP 16 26 97
# X $1airlines.arff OBagAd 462 355 2168
# X $1airlines.arff OBagAd 257 197 1204
# X $1airlines.arff OBagAd 51 39 240
# X $1airlines.arff OBagASHT 1060 333 3447
# X $1airlines.arff OBagASHT 589 185 1915
# X $1airlines.arff OBagASHT 117 37 383
# X $1airlines.arff OBag 1530 664 2780
# X $1airlines.arff OBag 850 369 1544
# X $1airlines.arff OBag 170 73 308
# X $1covtypeNorm.arff ARF 411 376 2461
# X $1covtypeNorm.arff ARF 228 209 1367
# X $1covtypeNorm.arff ARF 45 41 273
# X $1covtypeNorm.arff LBag 340 265 1269
# X $1covtypeNorm.arff LBag 189 147 705
# X $1covtypeNorm.arff LBag 37 29 141
# X $1covtypeNorm.arff SRP 126 116 715
# X $1covtypeNorm.arff SRP 70 64 397
# X $1covtypeNorm.arff SRP 14 12 79
# X $1covtypeNorm.arff OBagAd 686 334 1587
# X $1covtypeNorm.arff OBagAd 381 185 881
# X $1covtypeNorm.arff OBagAd 76 37 176
# X $1covtypeNorm.arff OBagASHT 769 349 1509
# X $1covtypeNorm.arff OBagASHT 427 193 838
# X $1covtypeNorm.arff OBagASHT 85 38 167
# X $1covtypeNorm.arff OBag 918 353 1801
# X $1covtypeNorm.arff OBag 510 196 1000
# X $1covtypeNorm.arff OBag 102 39 200
X $1GMSC.arff ARF 767 500 2459
# X $1GMSC.arff ARF 426 277 1366
# X $1GMSC.arff ARF 85 55 273
# X $1GMSC.arff LBag 991 580 3325
# X $1GMSC.arff LBag 550 322 1847
# X $1GMSC.arff LBag 110 64 369
# X $1GMSC.arff SRP 440 316 1445
# X $1GMSC.arff SRP 244 175 803
# X $1GMSC.arff SRP 48 35 160
# X $1GMSC.arff OBagAd 2719 1029 7268
# X $1GMSC.arff OBagAd 1510 571 4037
# X $1GMSC.arff OBagAd 302 114 807
# X $1GMSC.arff OBagASHT 4383 1154 11234
# X $1GMSC.arff OBagASHT 2435 641 6241
# X $1GMSC.arff OBagASHT 487 128 1248
# X $1GMSC.arff OBag 3253 1019 6689
# X $1GMSC.arff OBag 1807 566 3716
# X $1GMSC.arff OBag 361 113 743
# X $1elecNormNew.arff ARF 489 501 2765
# X $1elecNormNew.arff ARF 271 278 1536
# X $1elecNormNew.arff ARF 54 55 307
# X $1elecNormNew.arff LBag 869 554 3883
# X $1elecNormNew.arff LBag 483 308 2157
# X $1elecNormNew.arff LBag 96 61 431
# X $1elecNormNew.arff SRP 249 300 1492
# X $1elecNormNew.arff SRP 138 166 829
# X $1elecNormNew.arff SRP 27 33 165
# X $1elecNormNew.arff OBagAd 2113 773 5752
# X $1elecNormNew.arff OBagAd 1174 429 3195
# X $1elecNormNew.arff OBagAd 234 85 639
# X $1elecNormNew.arff OBagASHT 2274 775 5585
# X $1elecNormNew.arff OBagASHT 1263 430 3103
# X $1elecNormNew.arff OBagASHT 252 86 620
# X $1elecNormNew.arff OBag 2637 946 5753
# X $1elecNormNew.arff OBag 1465 525 3196
# X $1elecNormNew.arff OBag 293 105 639

date +"%d/%m/%y %T" >> exper_order-Xeon-16.log
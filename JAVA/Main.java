package com.baidu;

import java.io.*;
import java.util.*;


public class Main {
    static int[][] res = null;          //合理分配的某一时刻的矩阵
    static List<int[][]> allresmatrix;  //储存所有时刻的矩阵
    static List<int[]> allsurplus;      //储存每个时刻节点剩余带宽
    static boolean[][] isRich ;         //节点和用户连通表
    static Map<Site , Integer> siteIndex;
    static Set<Integer>[] everyTimesites;
    static List<Integer> increaseOrder;
    static Map<Integer , Set<Integer>> assignedSitesTime;
    static Set<String> alreadyAssignedTimeSites;
    public static void main(String[] args) throws IOException {
		// 读取Qos超参数信息
        int qos = readMaxQos("/data/config.ini");
		// 读取demand表格信息,返回user每个时刻的需求列表
        User[] users = readDemands("/data/demand.csv");
		// 读取Site_bandwidth表格信息,返回sites列表
        List<Site> sites = readSite_bandwidth("/data/site_bandwidth.csv");
		// 读取Qos表格信息,返回数组
        int[][] qosMap = readQos(users.length , sites.size() , "/data/qos.csv");
		//把每个user可以用的sites ， 以及每个site可以给哪个user提供服务记录下来
        addAvailableSites(users , sites , qosMap , qos);
		
        Arrays.sort(users , (x , y) -> (x.availableSitesSize - y.availableSitesSize));
        sites.sort((x , y) -> {
            return  x.numsUserCanUser - y.numsUserCanUser;
        });
		
		//记录每个site对应的分配矩阵下标
        siteIndex = new HashMap<>();
        for (int j = 0; j < sites.size(); j++) {
            siteIndex.put(sites.get(j) , j);
        }
		
		//记录哪个user和哪个site是连接的
        isRich = new boolean[users.length][sites.size()];
        for (int i = 0; i < users.length; i++) {
            List<Site> siteList = users[i].availableSites;
            for (int j = 0 ; j < siteList.size() ; j++) {
                isRich[i][siteIndex.get(siteList.get(j))] = true;
            }
        }
		
        alreadyAssignedTimeSites = new HashSet<>();
        assignedSitesTime = new HashMap<>();
        increaseOrder = new ArrayList<>();
        allresmatrix = new ArrayList<>();
        allsurplus = new ArrayList<>();
        everyTimesites = new Set[users[0].demand.size()];
        for (int i = 0; i < everyTimesites.length; i++) {
            everyTimesites[i] = new HashSet<>();
        }
		//暴力递归获取每个时刻的一个合理的分配矩阵
        solve(users , sites);
		//调整贪心调整每个时刻的分配矩阵
        directionalConcentration(sites , new HashSet<Integer>());
		// 输出最终结果
        output(users , sites , "/output/solution.txt");
    }
	
    //调整贪心调整每个时刻的分配矩阵
	public static void directionalConcentration( List<Site> sites , Set<Integer> alreadyAssigned){
        if (alreadyAssigned.size() == sites.size() - 1){
            return;
        }
        Map<Integer , int[]> maxConsumeMap = new HashMap<>();
        int[] maximumBandwidths = new int[sites.size()];
        for (int k = 0; k < sites.size(); k++) {
            if (!alreadyAssigned.contains(k)){
                maxConsumeMap.put(k , new int[allresmatrix.size()]);
            }
            maximumBandwidths[k] = sites.get(k).maximumBandwidth;
        }
        //找到每个节点每个时刻能够集中的最大值并存到数组中，用maxConsumeMap储存
        for (int l = 0; l < allresmatrix.size(); l++) {
            int[][] matrix = allresmatrix.get(l);
            int[] surplus2 = allsurplus.get(l);
            int n = surplus2.length;
            for (int i = 0; i < n; i++) {
                if (alreadyAssigned.contains(i)){
                    continue;
                }
                int surplus = surplus2[i];
                for (int j = 0; j < matrix.length; j++) {
                    /*if (alreadyAssigned.contains(j)){
                        continue;
                    }*/
                    if (surplus == 0){
                        break;
                    }
                    if (isRich[j][i]){
                        for (int k = 0; k < n; k++) {
                            if (surplus == 0){
                                break;
                            }
                            if (k != i && matrix[j][k] > 0 && !alreadyAssigned.contains(k)){
                                int transfer = Math.min(surplus , matrix[j][k] );
                                surplus -= transfer;
                            }
                        }
                    }
                }
                maxConsumeMap.get(i)[l] = maximumBandwidths[i] - surplus;
            }
        }
        int max = 0;
        int maxIndex = maxConsumeMap.keySet().iterator().next();
        Set<Integer> maxlinjieIndex = new HashSet<>();
        if (alreadyAssigned.size() <= sites.size() / 4){
            //集中后5%时刻最小的1/4个节点直接集中，并把前95%设置为0；
            max = Integer.MAX_VALUE;
            for (int integer : maxConsumeMap.keySet()) {
                int[] turn = maxConsumeMap.get(integer);
                int linjie = (int) ((allresmatrix.size()) - Math.ceil(0.95 * (allresmatrix.size())));
                PriorityQueue<Integer> priorityQueue = new PriorityQueue<>( (x , y) -> (turn[x] - turn[y]));
                for (int i = 0; i < linjie; i++) {
                    priorityQueue.offer(i);
                }
                if (!priorityQueue.isEmpty()){
                    for (int i = linjie; i < turn.length; i++) {
                        if (turn[i] > turn[priorityQueue.peek()]){
                            priorityQueue.poll();
                            priorityQueue.offer(i);
                        }
                    }
                }
                int sum = 0;
                Set<Integer> indexs = new HashSet<>();
                while (!priorityQueue.isEmpty()){
                    int index = priorityQueue.poll();
                    sum += turn[index];
                    indexs.add(index);
                }
                if (sum < max){
                    max = sum;
                    maxIndex = integer;
                    maxlinjieIndex = indexs;
                }
            }
            int[] temp = maxConsumeMap.get(maxIndex).clone();
            int linjie = (int) (Math.ceil(0.95 * (allresmatrix.size())));
            Arrays.sort(temp);
            if (temp[linjie - 1] == 0){
                for (int l : maxlinjieIndex) {
                    int[][] matrix = allresmatrix.get(l);
                    int[] surplus2 = allsurplus.get(l);
                    int n = surplus2.length;
                    for (int i = 0; i < matrix.length; i++) {
                        if (surplus2[maxIndex] == 0){
                            break;
                        }
                        if (isRich[i][maxIndex]){
                            for (int j = 0; j < n; j++) {
                                if (alreadyAssigned.contains(j)){
                                    continue;
                                }
                                if (surplus2[maxIndex] == 0){
                                    break;
                                }
                                if ( j != maxIndex && matrix[i][j] > 0){
                                    int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                    matrix[i][j] -= transfer;
                                    matrix[i][maxIndex] += transfer;
                                    surplus2[maxIndex] -= transfer;
                                    surplus2[j] += transfer;
                                }
                            }
                        }
                    }
                    if (surplus2[maxIndex] != 0){
                        for (int i = 0; i < matrix.length; i++) {
                            if (surplus2[maxIndex] == 0){
                                break;
                            }
                            if (isRich[i][maxIndex]){
                                for (int j : alreadyAssigned) {
                                    if (alreadyAssigned.contains(j)){
                                        continue;
                                    }
                                    if (surplus2[maxIndex] == 0){
                                        break;
                                    }
                                    if ( j != maxIndex && matrix[i][j] > 0){
                                        int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                        matrix[i][j] -= transfer;
                                        matrix[i][maxIndex] += transfer;
                                        surplus2[maxIndex] -= transfer;
                                        surplus2[j] += transfer;
                                    }
                                }
                            }
                        }
                    }
                    if (maxConsumeMap.get(maxIndex)[l] != 0){
                        everyTimesites[l].add(maxIndex);
                    }
                }
            }else {
                int upperLimitValue = 0;
                int minupperLimitValue = upperLimitValue;
                for (int l = 0; l < allresmatrix.size(); l++) {
                    int[][] matrix = allresmatrix.get(l);
                    int[] surplus2 = allsurplus.get(l);
                    int n = surplus2.length;
                    if (maxlinjieIndex.contains(l)){
                        for (int i = 0; i < matrix.length; i++) {
                            if (surplus2[maxIndex] == 0){
                                break;
                            }
                            if (isRich[i][maxIndex]){
                                for (int j = 0; j < n; j++) {
                                    if (alreadyAssigned.contains(j)){
                                        continue;
                                    }
                                    if (surplus2[maxIndex] == 0){
                                        break;
                                    }

                                    if ( j != maxIndex && matrix[i][j] > 0){
                                        int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                        matrix[i][j] -= transfer;
                                        matrix[i][maxIndex] += transfer;
                                        surplus2[maxIndex] -= transfer;
                                        surplus2[j] += transfer;
                                    }
                                }
                            }
                        }
                        if (maxConsumeMap.get(maxIndex)[l] != 0){
                            everyTimesites[l].add(maxIndex);
                        }
                    }else {
                        if (maximumBandwidths[maxIndex] - surplus2[maxIndex] >  upperLimitValue){
                            int needtogive = maximumBandwidths[maxIndex] - surplus2[maxIndex] -  upperLimitValue;
                            for (int k = 0; k  < matrix.length ; k++) {
                                if (needtogive == 0){
                                    break;
                                }
                                if (isRich[k][maxIndex] && matrix[k][maxIndex] > 0){
                                    int cangeive = Math.min(needtogive , matrix[k][maxIndex]);
                                    for (int m = 0; m < surplus2.length ; m++) {
                                        if (cangeive == 0){
                                            break;
                                        }
                                        if (alreadyAssigned.contains(m)){
                                            continue;
                                        }else {
                                            if (m != maxIndex && isRich[k][m]){
                                                int traner = Math.min(cangeive , surplus2[m]);
                                                needtogive -= traner;
                                                cangeive -= traner;
                                                matrix[k][maxIndex] -= traner;
                                                matrix[k][m] += traner;
                                                surplus2[m] -= traner;
                                                surplus2[maxIndex] += traner;
                                            }
                                        }
                                    }
                                }
                            }
                            minupperLimitValue = Math.max(minupperLimitValue , maximumBandwidths[maxIndex] - surplus2[maxIndex]);
                        }
                        if (maximumBandwidths[maxIndex] - surplus2[maxIndex] <  upperLimitValue){
                            int need =   upperLimitValue - (maximumBandwidths[maxIndex] - surplus2[maxIndex]);
                            for (int k = 0; k < matrix.length; k++) {
                                if (need == 0){
                                    break;
                                }
                                if (isRich[k][maxIndex]){
                                    for (int m = 0; m < surplus2.length ; m++) {
                                        if (need == 0){
                                            break;
                                        }
                                        if (alreadyAssigned.contains(m)){
                                            continue;
                                        }
                                        if (m != maxIndex  && matrix[k][m] > 0 ){
                                            int cangeive = Math.min(need , matrix[k][m]);
                                            need -= cangeive;
                                            matrix[k][maxIndex] += cangeive;
                                            matrix[k][m] -= cangeive;
                                            surplus2[m] += cangeive;
                                            surplus2[maxIndex] -= cangeive;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (minupperLimitValue > upperLimitValue){
                    //如果出现匀不出去的情况，把匀不出去的最大的作为阈值
                    upperLimitValue = minupperLimitValue;
                    for (int l = 0; l < allresmatrix.size(); l++) {
                        int[][] matrix = allresmatrix.get(l);
                        int[] surplus2 = allsurplus.get(l);
                        if (!maxlinjieIndex.contains(l)) {
                            if (maximumBandwidths[maxIndex] - surplus2[maxIndex] >  upperLimitValue){
                                int needtogive = maximumBandwidths[maxIndex] - surplus2[maxIndex] -  upperLimitValue;
                                for (int k = 0; k  < matrix.length ; k++) {
                                    if (needtogive == 0){
                                        break;
                                    }
                                    if (isRich[k][maxIndex] && matrix[k][maxIndex] > 0){
                                        int cangeive = Math.min(needtogive , matrix[k][maxIndex]);
                                        for (int m = 0; m < surplus2.length ; m++) {
                                            if (alreadyAssigned.contains(m)){
                                                continue;
                                            }
                                            if (cangeive == 0){
                                                break;
                                            }
                                            if (m != maxIndex && isRich[k][m]){
                                                int traner = Math.min(cangeive , surplus2[m]);
                                                needtogive -= traner;
                                                cangeive -= traner;
                                                matrix[k][maxIndex] -= traner;
                                                matrix[k][m] += traner;
                                                surplus2[m] -= traner;
                                                surplus2[maxIndex] += traner;
                                            }
                                        }
                                    }
                                }
                            }
                            if (maximumBandwidths[maxIndex] - surplus2[maxIndex] <  upperLimitValue){
                                int need =   upperLimitValue - (maximumBandwidths[maxIndex] - surplus2[maxIndex]);
                                for (int k = 0; k < matrix.length; k++) {
                                    if (need == 0){
                                        break;
                                    }
                                    if (isRich[k][maxIndex]){
                                        for (int m = 0; m < surplus2.length ; m++) {
                                            if (need == 0){
                                                break;
                                            }
                                            if (alreadyAssigned.contains(m)){
                                                continue;
                                            }
                                            if (m != maxIndex  && matrix[k][m] > 0 ){
                                                int cangeive = Math.min(need , matrix[k][m]);
                                                need -= cangeive;
                                                matrix[k][maxIndex] += cangeive;
                                                matrix[k][m] -= cangeive;
                                                surplus2[m] += cangeive;
                                                surplus2[maxIndex] -= cangeive;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }else {
            //后3/4贪心集中，找后5%最大的节点集中，其前95%设置阈值集中
            for (int integer : maxConsumeMap.keySet()) {
                int[] turn = maxConsumeMap.get(integer);
                int linjie = (int) ((allresmatrix.size()) - Math.ceil(0.95 * (allresmatrix.size())));
                PriorityQueue<Integer> priorityQueue = new PriorityQueue<>( (x , y) -> (turn[x] - turn[y]));
                for (int i = 0; i < linjie; i++) {
                    priorityQueue.offer(i);
                }
                if (!priorityQueue.isEmpty()){
                    for (int i = linjie; i < turn.length; i++) {
                        if (turn[i] > turn[priorityQueue.peek()]){
                            priorityQueue.poll();
                            priorityQueue.offer(i);
                        }
                    }
                }
                int sum = 0;
                Set<Integer> indexs = new HashSet<>();
                while (!priorityQueue.isEmpty()){
                    int index = priorityQueue.poll();
                    sum += turn[index];
                    indexs.add(index);
                }
                if (sum > max){
                    max = sum;
                    maxIndex = integer;
                    maxlinjieIndex = indexs;
                }
            }
            int[] temp = maxConsumeMap.get(maxIndex).clone();
            int linjie = (int) (Math.ceil(0.95 * (allresmatrix.size())));
            Arrays.sort(temp);
            if (max == 0 || maxlinjieIndex.size() == 0 /*|| max / maxlinjieIndex.size() < 1000*/){
                return;
            }
            if (temp[linjie - 1] == 0){
                for (int l : maxlinjieIndex) {
                    int[][] matrix = allresmatrix.get(l);
                    int[] surplus2 = allsurplus.get(l);
                    int n = surplus2.length;
                    for (int i = 0; i < matrix.length; i++) {
                        if (surplus2[maxIndex] == 0){
                            break;
                        }
                        if (isRich[i][maxIndex]){
                            for (int j = 0; j < n; j++) {
                                if (alreadyAssigned.contains(j)){
                                    continue;
                                }
                                if (surplus2[maxIndex] == 0){
                                    break;
                                }
                                if ( j != maxIndex && matrix[i][j] > 0){
                                    int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                    matrix[i][j] -= transfer;
                                    matrix[i][maxIndex] += transfer;
                                    surplus2[maxIndex] -= transfer;
                                    surplus2[j] += transfer;
                                }
                            }
                        }
                    }
                    if (surplus2[maxIndex] != 0){
                        for (int i = 0; i < matrix.length; i++) {
                            if (surplus2[maxIndex] == 0){
                                break;
                            }
                            if (isRich[i][maxIndex]){
                                for (int j : alreadyAssigned) {
                                    if (alreadyAssigned.contains(j)){
                                        continue;
                                    }
                                    if (surplus2[maxIndex] == 0){
                                        break;
                                    }
                                    if ( j != maxIndex && matrix[i][j] > 0){
                                        int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                        matrix[i][j] -= transfer;
                                        matrix[i][maxIndex] += transfer;
                                        surplus2[maxIndex] -= transfer;
                                        surplus2[j] += transfer;
                                    }
                                }
                            }
                        }
                    }
                    if (maxConsumeMap.get(maxIndex)[l] != 0){
                        everyTimesites[l].add(maxIndex);
                    }
                }
            }else {
                //设置上限值
                /*int upperLimitValue = 1000000 * 5/ (4 * sites.size()) - (alreadyAssigned.size() - sites.size() / 5)* 1000000 * 25 / (16 * sites.size() * sites.size());*/
                int upperLimitValue = 13200 - (int)(alreadyAssigned.size() - sites.size() / 4) * 10;
                int min = Arrays.stream(temp).min().getAsInt();
                if( min <=  upperLimitValue){
                    long sum = 0;
                    int numszero = 0;
                    for (int i : temp) {
                        sum += i;
                        if (i == 0){
                            numszero++;
                        }
                    }
                    sum -= max;
                    if (sum / (linjie - numszero) < upperLimitValue){
                        upperLimitValue = (int) (sum / (linjie - numszero));
                    }
                }else {
                    upperLimitValue = min;
                }
                if (temp[(int) (0.68 * allresmatrix.size())] == 0){
                    upperLimitValue = 0;
                }
                upperLimitValue = Math.max(0 , upperLimitValue);
                int minupperLimitValue = upperLimitValue;
                for (int l = 0; l < allresmatrix.size(); l++) {
                    int[][] matrix = allresmatrix.get(l);
                    int[] surplus2 = allsurplus.get(l);
                    int n = surplus2.length;
                    if (maxlinjieIndex.contains(l)){
                        for (int i = 0; i < matrix.length; i++) {
                            if (surplus2[maxIndex] == 0){
                                break;
                            }
                            if (isRich[i][maxIndex]){
                                for (int j = 0; j < n; j++) {
                                    if (alreadyAssigned.contains(j)){
                                        continue;
                                    }
                                    if (surplus2[maxIndex] == 0){
                                        break;
                                    }
                                    if ( j != maxIndex && matrix[i][j] > 0){
                                        int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                        matrix[i][j] -= transfer;
                                        matrix[i][maxIndex] += transfer;
                                        surplus2[maxIndex] -= transfer;
                                        surplus2[j] += transfer;
                                    }
                                }
                            }
                        }
                        if (surplus2[maxIndex] != 0){
                            for (int i = 0; i < matrix.length; i++) {
                                if (surplus2[maxIndex] == 0){
                                    break;
                                }
                                if (isRich[i][maxIndex]){
                                    for (int j : alreadyAssigned) {
                                        if (alreadyAssigned.contains(j)){
                                            continue;
                                        }
                                        if (surplus2[maxIndex] == 0){
                                            break;
                                        }
                                        if ( j != maxIndex && matrix[i][j] > 0){
                                            int transfer = Math.min(surplus2[maxIndex] , matrix[i][j]);
                                            matrix[i][j] -= transfer;
                                            matrix[i][maxIndex] += transfer;
                                            surplus2[maxIndex] -= transfer;
                                            surplus2[j] += transfer;
                                        }
                                    }
                                }
                            }
                        }

                        if (maxConsumeMap.get(maxIndex)[l] != 0){
                            everyTimesites[l].add(maxIndex);
                        }
                    }else {
                        if (maximumBandwidths[maxIndex] - surplus2[maxIndex] >  upperLimitValue){
                            int needtogive = maximumBandwidths[maxIndex] - surplus2[maxIndex] -  upperLimitValue;
                            for (int k = 0; k  < matrix.length ; k++) {
                                if (needtogive == 0){
                                    break;
                                }
                                if (isRich[k][maxIndex] && matrix[k][maxIndex] > 0){
                                    int cangeive = Math.min(needtogive , matrix[k][maxIndex]);
                                    for (int m = 0; m < surplus2.length ; m++) {
                                        if (alreadyAssigned.contains(m)){
                                            continue;
                                        }
                                        if (cangeive == 0){
                                            break;
                                        }
                                        if (m != maxIndex && isRich[k][m]){
                                            int traner = Math.min(cangeive , surplus2[m]);
                                            needtogive -= traner;
                                            cangeive -= traner;
                                            matrix[k][maxIndex] -= traner;
                                            matrix[k][m] += traner;
                                            surplus2[m] -= traner;
                                            surplus2[maxIndex] += traner;
                                        }
                                    }
                                }
                            }
                            minupperLimitValue = Math.max(minupperLimitValue , maximumBandwidths[maxIndex] - surplus2[maxIndex]);
                        }

                        if (maximumBandwidths[maxIndex] - surplus2[maxIndex] <  upperLimitValue){
                            int need =   upperLimitValue - (maximumBandwidths[maxIndex] - surplus2[maxIndex]);
                            for (int k = 0; k < matrix.length; k++) {
                                if (need == 0){
                                    break;
                                }
                                if (isRich[k][maxIndex]){
                                    for (int m = 0; m < surplus2.length ; m++) {
                                        if (need == 0){
                                            break;
                                        }
                                        if (alreadyAssigned.contains(m)){
                                            continue;
                                        }
                                        if (m != maxIndex  && matrix[k][m] > 0 ){
                                            int cangeive = Math.min(need , matrix[k][m]);
                                            need -= cangeive;
                                            matrix[k][maxIndex] += cangeive;
                                            matrix[k][m] -= cangeive;
                                            surplus2[m] += cangeive;
                                            surplus2[maxIndex] -= cangeive;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                //如果出现匀不出去的情况，把匀不出去的最大的作为阈值
                if ( minupperLimitValue > upperLimitValue){
                    upperLimitValue = minupperLimitValue;
                    for (int l = 0; l < allresmatrix.size(); l++) {
                        int[][] matrix = allresmatrix.get(l);
                        int[] surplus2 = allsurplus.get(l);
                        if (!maxlinjieIndex.contains(l)) {
                            if (maximumBandwidths[maxIndex] - surplus2[maxIndex] >  upperLimitValue){
                                int needtogive = maximumBandwidths[maxIndex] - surplus2[maxIndex] -  upperLimitValue;
                                for (int k = 0; k  < matrix.length ; k++) {
                                    if (needtogive == 0){
                                        break;
                                    }
                                    if (isRich[k][maxIndex] && matrix[k][maxIndex] > 0){
                                        int cangeive = Math.min(needtogive , matrix[k][maxIndex]);
                                        for (int m = 0; m < surplus2.length ; m++) {
                                            if (alreadyAssigned.contains(m)){
                                                continue;
                                            }
                                            if (cangeive == 0){
                                                break;
                                            }
                                            if (m != maxIndex && isRich[k][m]){
                                                int traner = Math.min(cangeive , surplus2[m]);
                                                needtogive -= traner;
                                                cangeive -= traner;
                                                matrix[k][maxIndex] -= traner;
                                                matrix[k][m] += traner;
                                                surplus2[m] -= traner;
                                                surplus2[maxIndex] += traner;
                                            }
                                        }
                                    }
                                }
                            }
                            if (maximumBandwidths[maxIndex] - surplus2[maxIndex] <  upperLimitValue){
                                int need =   upperLimitValue - (maximumBandwidths[maxIndex] - surplus2[maxIndex]);
                                for (int k = 0; k < matrix.length; k++) {
                                    if (need == 0){
                                        break;
                                    }
                                    if (isRich[k][maxIndex]){
                                        for (int m = 0; m < surplus2.length ; m++) {
                                            if (need == 0){
                                                break;
                                            }
                                            if (alreadyAssigned.contains(m)){
                                                continue;
                                            }
                                            if (m != maxIndex  && matrix[k][m] > 0 ){
                                                int cangeive = Math.min(need , matrix[k][m]);
                                                need -= cangeive;
                                                matrix[k][maxIndex] += cangeive;
                                                matrix[k][m] -= cangeive;
                                                surplus2[m] += cangeive;
                                                surplus2[maxIndex] -= cangeive;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        alreadyAssigned.add(maxIndex);
        directionalConcentration(sites , alreadyAssigned);
    }

    public static void doSum( List<Site> sites){
        List<Integer>[] res = new List[sites.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = new ArrayList<>();
        }
        for (int i = 0; i < allsurplus.size(); i++) {
            int[] suplus = allsurplus.get(i);
            int[]  max= new int[sites.size()];
            for (int i1 = 0; i1 < sites.size(); i1++) {
                max[i1] = sites.get(i1).maximumBandwidth;
            }
            for (int j = 0; j < max.length; j++) {
                int use = max[j] - suplus[j];
                res[j].add(use);
            }
        }
        int sum = 0;
        int max = 0;
        for (List<Integer> re : res) {
            if (re.size() == 0){
                continue;
            }
            re.sort((x , y) -> (x - y));
            int temp = (int)Math.ceil(0.95 * allresmatrix.size());
            temp = Math.max(0 ,temp - 1);
            sum += re.get(temp - 1);
            max = Math.max(max , re.get(re.size() - 1));
            System.out.println( re.size()+ "  "+ re);
        }
        System.out.println(sum);
    }
    //暴力递归获取每个时刻的一个合理的分配矩阵
    public static void solve(User[] users , List<Site> sites) throws IOException {
        int allTime = users[0].demand.size();
        int[] numsConcentrationtimes = new int[sites.size()];
        for (int i = 0; i < allTime; i++) {
            violentEnumeration( numsConcentrationtimes ,users , sites , i);
        }
        /*addone();*/
    }
    //获取矩阵，以及分配后的剩余可用带宽数组
    public static void violentEnumeration(int[] numsConcentrationtimes ,User[] users , List<Site> sites , int time) throws IOException {
        int[] demands = new int[users.length];
        for (int j = 0 ; j < users.length ; j++) {
            User user = users[j];
            demands[j] = user.demand.get(time);
        }
        int[] maximumBandwidths = new int[sites.size()];
        for (int j = 0; j < sites.size(); j++) {
            maximumBandwidths[j] = sites.get(j).maximumBandwidth;
        }
        /* int lenuser = users.length , lensite = sites.size();*/
        int[][] fenpei = new int[users.length][sites.size()];
        int[] surplus = maximumBandwidths.clone();
        generateTable(fenpei , surplus , demands , isRich , 0 , siteIndex ,users );
        int[] surplus2 = new int[sites.size()];
        for (int i = 0; i < surplus2.length; i++) {
            int sum = 0;
            for (int j = 0; j < res.length; j++) {
                sum += res[j][i];
            }
            surplus2[i] = sites.get(i).maximumBandwidth - sum;
        }
        /*matrixadjustment(numsConcentrationtimes ,surplus2 , isRich , maximumBandwidths);*/


        /*for (int i = 0; i < res.length; i++) {
            System.out.println(Arrays.toString(res[i]) +"   " +  Arrays.stream(res[i]).sum() +  "  " +  (Arrays.stream(res[i]).sum() == users[i].demand.get(time)));
        }*/
        /*for (int i = 0; i < res[0].length; i++) {
            int sum = 0;
            for (int j = 0; j < res.length; j++) {
                sum += res[j][i];
            }
            if (sum > sites.get(i).maximumBandwidth){
                System.out.println(false);
            }
            *//*System.out.println(sum + "  " +   sites.get(i).maximumBandwidth + "  "+ (sum <= sites.get(i).maximumBandwidth));*//*
        }
        System.out.println("===========================");*/
        /*for (int j = 0; j < res.length; j++) {
            textWrite.write(users[j].id + ":");
            boolean flag = true;
            for (int k = 0; k < res[j].length; k++) {
                if (res[j][k] != 0){
                    if (flag){
                        textWrite.write("<" + sites.get(k).id +","+ res[j][k] + ">");
                        flag = false;
                    }else {
                        textWrite.write(",<" + sites.get(k).id +","+ res[j][k] + ">");
                    }
                }
            }
            textWrite.write( "\n");
        }*/
        allresmatrix.add(res);
        allsurplus.add(surplus2);
        res = null;
    }

	// 输出最终结果
    public static void output(User[] users , List<Site> sites , String pathname ) throws IOException {
        File writeFile = new File(pathname);
        BufferedWriter textWrite = new BufferedWriter(new FileWriter(writeFile));
        for (int i = 0; i < allresmatrix.size(); i++) {
            int[][] matrix = allresmatrix.get(i);
            /*for (int l = 0; l < matrix.length; l++) {
                System.out.println(Arrays.toString(matrix[l]) +"   " +  Arrays.stream(matrix[l]).sum() +  "  " +  (Arrays.stream(matrix[l]).sum() == users[l].demand.get(i)) + "   " + users[l].id);
            }
            System.out.println("=========================");*/

            /*for (int l = 0; l < matrix[0].length; l++) {
                int sum = 0;
                for (int j = 0; j < matrix.length; j++) {
                    sum += matrix[j][l];
                }
                System.out.print( sum + "  ");
            }
            System.out.println();*/
            for (int j = 0; j < matrix.length; j++) {
                textWrite.write(users[j].id + ":");
                boolean flag = true;
                for (int k = 0; k < matrix[j].length; k++) {
                    if (matrix[j][k] != 0){
                        if (flag){
                            textWrite.write("<" + sites.get(k).id +","+ matrix[j][k] + ">");
                            flag = false;
                        }else {
                            textWrite.write(",<" + sites.get(k).id +","+ matrix[j][k] + ">");
                        }
                    }
                }
                textWrite.write( "\n");
            }
            textWrite.flush();
        }
    }

    public static void generateTable(int[][] fenpei, int[] surplus , int[] demands, boolean[][] isRich , int index , Map<Site , Integer> siteIndex , User[] users ){
        if (res != null){
            return;
        }
        if (index >= fenpei.length){
            if (res == null){
                res = new int[fenpei.length][];
                for (int i = 0; i < res.length; i++) {
                    res[i] = fenpei[i].clone();
                }
            }
            return;
        }
        List<Site> siteList = users[index].availableSites;
        int demand = demands[index];
        generateHang(fenpei , surplus , demands , isRich , demand , users , index , 0 , siteList.size() , siteList , siteIndex);
    }
    public static void generateHang(int[][] fenpei, int[] surplus , int[] demands, boolean[][] isRich ,int demand ,  User[] users ,int index ,int i , int n , List<Site> siteList , Map<Site , Integer> siteIndex){
        if (res != null){
            return;
        }
        int j = siteIndex.get(siteList.get(i));
        if (i == n - 1 || demand == 0){
            if (surplus[j] >= demand){
                fenpei[index][j] = demand;
                surplus[j] -= demand;
                generateTable(fenpei , surplus , demands , isRich , index + 1 , siteIndex,users);
                surplus[j] += demand;
                fenpei[index][j] = 0;
            }
            return;
        }
        for (int k = Math.min(demand , surplus[j]); k >= 0 ; k--) {
            if (res != null){
                return;
            }
            if (surplus[j] >= k){
                fenpei[index][j] = k;
                surplus[j] -= k;
                generateHang(fenpei , surplus ,demands , isRich , demand - k , users , index , i + 1 , n , siteList , siteIndex);
                surplus[j] += k;
                fenpei[index][j] = 0;
            }
        }
    }


	// 读取Qos超参数信息
    public static Integer readMaxQos(String pathname) throws IOException {
        File train = new File(pathname);
		// 相当于python的 file = open(path)
        BufferedReader textFile  = new BufferedReader(new FileReader(train));
        String line = textFile.readLine(); // 读取第一行数据 [config]
        line = textFile.readLine();        // 读取第二行数据 qos_constraint=400
        return Integer.valueOf(line.substring(line.indexOf("=") + 1));
    }

	// 读取demand表格信息 , 返回user每个时刻的需求列表
    public static User[] readDemands( String pathname ) throws IOException {
        File train = new File(pathname);
        BufferedReader textFile = null;
        textFile = new BufferedReader(new FileReader(train));
        String[] ids = textFile.readLine().split(",");
        int n = ids.length - 1;
        User[] users = new User[n];
		// 给用户命名
        for (int i = 1; i <= n; i++) {
            users[i - 1] = new User(ids[i]);
        }
        String line = "";
        while ( (line = textFile.readLine()) != null){
            String[] demands = line.split(",");
            for (int i = 1; i <= n; i++) {
                users[i - 1].demand.add(Integer.valueOf(demands[i]));
            }
        }
        return users;
    }
	// 读取 Site_bandwidth 表格信息 返回每个site的类
	public static List<Site>  readSite_bandwidth(String pathname) throws IOException {
        File train = new File(pathname);
        BufferedReader textFile = null;
        textFile = new BufferedReader(new FileReader(train));
        textFile.readLine();
        String line = "";
        List<Site> sites = new ArrayList<>();
        while ((line = textFile.readLine()) != null){
            String[] site = line.split(",");
            sites.add(new Site(site[0] , Integer.valueOf(site[1])));
        }
        return sites;
    }
	// 读取 Qos 表格 返回矩阵
    public static int[][] readQos(int usersLength , int sitesLength , String pathname ) throws IOException {
        File train = new File(pathname);
        BufferedReader textFile = null;
        textFile = new BufferedReader(new FileReader(train));
        String line = textFile.readLine();
        int temp = 0;
        int[][] qosMap = new int[sitesLength][usersLength];
        while ((line = textFile.readLine()) != null){
            String[] qos = line.split(",");
            for (int i = 1; i <= usersLength; i++) {
                qosMap[temp][i - 1] = Integer.valueOf(qos[i]);
            }
            temp++;
        }
        return qosMap;
    }

	// 补充每个客户user,节点site满足Qos条件下可以匹配的对象
	public static void addAvailableSites(User[] users , List<Site> sites ,int[][] qosMap , int qos){
        int userLen = users.length , sitesLen = sites.size();
        for (int i = 0; i < sitesLen; i++) {
            for (int j = 0; j < userLen; j++) {
                if (qosMap[i][j] < qos){
                    users[j].availableSites.add(sites.get(i));
                    users[j].availableSitesSize++;
                    sites.get(i).userCanUser.add(users[j]);
                    sites.get(i).numsUserCanUser++;
                }
            }
        }
    }
}

class User {
	String id;
	List<Integer> demand;
	List<Site> availableSites;
	int availableSitesSize;
	public User(String id) {
		this.id = id;
		this.demand = new ArrayList<>();
		availableSites = new ArrayList<>();
		availableSitesSize = 0;
	}
}

class Site {
	String id;
	int maximumBandwidth;
	int availableBandwidth;
	List<User> userCanUser;
	int numsUserCanUser;
	public Site(String id, int maximumBandwidth) {
		this.id = id;
		this.maximumBandwidth = maximumBandwidth;
		availableBandwidth = maximumBandwidth;
		userCanUser = new ArrayList<>();
		numsUserCanUser = 0;
	}
	public boolean useBandwidth(int bandwidth ){
		if (availableBandwidth < bandwidth){
			return false;
		}
		availableBandwidth -= bandwidth;
		return true;
	}
}









package com.baidu;

import java.io.*;
import java.util.*;


public class Main {
    static Map<String , User> findUserById;
    static Map<String , Site> findSiteById;
    static int[][] res = null;					//合理分配的某一时刻的矩阵
    static List<int[][]> allresmatrix;			//储存所有时刻的矩阵
    static List<int[]> allsurplus;				//储存每个时刻节点剩余带宽
    static boolean[][] isRich ;					//节点和用户连通表
    static Map<Site , Integer> siteIndex;
    static Set<Integer>[] everyTimesites;
    static List<Integer> increaseOrder;
    static Map<Integer , Set<Integer>> assignedSitesTime;
    static Set<String> alreadyAssignedTimeSites;
    static List<Integer> minSitescan;
    static int[] maximumBandwidths;
	
    public static void main(String[] args) throws IOException {
        long time1 = System.currentTimeMillis();
        findUserById = new HashMap<>();
        findSiteById = new HashMap<>();
		// 读取Qos超参数信息
        int qos = readMaxQos("C:data\\config.ini");
		// 读取demand表格信息,返回user每个时刻的需求列表
        User[] users = readDemands("C:data\\demand.csv");
		// 读取Site_bandwidth表格信息,返回sites列表
        List<Site> sites = readSite_bandwidth("C:data\\site_bandwidth2.csv");
		// 读取Qos表格信息,更新user site
        readQos(qos, users.length , sites.size() , "C:data\\qos.csv");
		// user 排序
        Arrays.sort(users , (x , y) -> (x.availableSitesSize - y.availableSitesSize));
		// site 节点排序
        sites.sort((x , y) -> {
            if (x.numsUserCanUser == y.numsUserCanUser){
                return x.maximumBandwidth - y.maximumBandwidth;
            }else {
                return  x.numsUserCanUser - y.numsUserCanUser;
            }
        });
		//记录每个site对应的分配矩阵下标
        siteIndex = new HashMap<>();
        for (int j = 0; j < sites.size(); j++) {
            siteIndex.put(sites.get(j) , j);
        }
		// 获取 user , site 的0,1匹配表
        isRich = new boolean[users.length][sites.size()];
        for (int i = 0; i < users.length; i++) {
			// 获取客户i可以匹配的site列表
            List<Site> siteList = users[i].availableSites;
            for (int j = 0 ; j < siteList.size() ; j++) {
				// 获取0,1匹配表
                isRich[i][siteIndex.get(siteList.get(j))] = true;
            }
        }
		// 获取节点最大带宽列表
        maximumBandwidths = new int[sites.size()];
        for (int k = 0; k < sites.size(); k++) {
            maximumBandwidths[k] = sites.get(k).maximumBandwidth;
        }
		
        minSitescan = new ArrayList<>();
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
		
        doSum(sites);
		// 输出最终结果
        output(users , sites ,"C:\\Users\\ZhangLingLing\\Desktop\\华为软件杯\\线下调试数据\\data\\solution2.txt");
        long time2 = System.currentTimeMillis();
        System.out.println(time2 - time1);
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
	//调整贪心调整每个时刻的分配矩阵
    public static void directionalConcentration( List<Site> sites , Set<Integer> alreadyAssigned){
        if (alreadyAssigned.size() == sites.size() - 1){
            return;
        }
        Map<Integer , int[]> maxConsumeMap = findmaxConsumeMap(sites , alreadyAssigned);
        int max = 0;
        int maxIndex = maxConsumeMap.keySet().iterator().next();
        Set<Integer> maxlinjieIndex = new HashSet<>();
        if (alreadyAssigned.size() <= sites.size() / 4){
            max = Integer.MAX_VALUE;
            for (int integer : maxConsumeMap.keySet()) {
                int[] turn = maxConsumeMap.get(integer);
                int linjie = (int) ((allresmatrix.size()) - Math.ceil(0.95 * (allresmatrix.size())));
                PriorityQueue<Integer> priorityQueue = findtheLargestNumber(linjie , turn);
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
                comeTogetherTop5(maxIndex , maxlinjieIndex , maxConsumeMap , alreadyAssigned);
            }else {
                int upperLimitValue = 0;
                int minupperLimitValue = comeTogetherLast95(upperLimitValue , maxIndex , maxlinjieIndex , maximumBandwidths , alreadyAssigned);
                if (minupperLimitValue > upperLimitValue){
                    comeTogetherLast95(minupperLimitValue , maxIndex , maxlinjieIndex , maximumBandwidths , alreadyAssigned);
                }
            }
        }else {
            for (int integer : maxConsumeMap.keySet()) {
                int[] turn = maxConsumeMap.get(integer);
                int linjie = (int) ((allresmatrix.size()) - Math.ceil(0.95 * (allresmatrix.size())));
                PriorityQueue<Integer> priorityQueue = findtheLargestNumber(linjie , turn);
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
                comeTogetherTop5(maxIndex , maxlinjieIndex , maxConsumeMap , alreadyAssigned);
            }else {
                //设置上限值
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
                comeTogetherTop5(maxIndex , maxlinjieIndex , maxConsumeMap , alreadyAssigned);
                int minupperLimitValue = comeTogetherLast95(upperLimitValue , maxIndex , maxlinjieIndex , maximumBandwidths , alreadyAssigned);
                if ( minupperLimitValue > upperLimitValue){
                    comeTogetherLast95(minupperLimitValue , maxIndex , maxlinjieIndex , maximumBandwidths , alreadyAssigned);
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
	
	public static Map<Integer , int[]> findmaxConsumeMap(List<Site> sites , Set<Integer> alreadyAssigned){
        Map<Integer , int[]> maxConsumeMap = new HashMap<>();
        for (int k = 0; k < sites.size(); k++) {
            if (!alreadyAssigned.contains(k)){
                maxConsumeMap.put(k , new int[allresmatrix.size()]);
            }
        }
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
        return maxConsumeMap;
    }
	
    public static PriorityQueue<Integer> findtheLargestNumber(int linjie , int[] turn){
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
        return priorityQueue;
    }
	
    public static void comeTogetherTop5(int maxIndex , Set<Integer> maxlinjieIndex ,Map<Integer , int[]> maxConsumeMap , Set<Integer> alreadyAssigned){
        for (int l : maxlinjieIndex){
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
    }
	
    public static int comeTogetherLast95(int upperLimitValue , int maxIndex , Set<Integer> maxlinjieIndex , int[] maximumBandwidths , Set<Integer> alreadyAssigned){
        int minupperLimitValue = upperLimitValue;
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
        return minupperLimitValue;
    }
	



	// 获取矩阵，以及分配后的剩余可用带宽数组
    public static void violentEnumeration(int[] numsConcentrationtimes ,User[] users , List<Site> sites , int time) throws IOException {
        int[] demands = new int[users.length];
        for (int j = 0 ; j < users.length ; j++) {
            User user = users[j];
            demands[j] = user.demand.get(time);
        }
        int[][] fenpei = new int[users.length][sites.size()];
        int[] surplus = maximumBandwidths.clone();
		// 列匹配
        generateTable(fenpei , surplus , demands , isRich , 0 , siteIndex ,users );
        int[] surplus2 = new int[sites.size()];
        for (int i = 0; i < surplus2.length; i++) {
            int sum = 0;
            for (int j = 0; j < res.length; j++) {
                sum += res[j][i];
            }
            surplus2[i] = sites.get(i).maximumBandwidth - sum;
        }
        allresmatrix.add(res);
        allsurplus.add(surplus2);
        res = null;
    }
	// 列匹配
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
	// 行匹配
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
        BufferedReader textFile  = new BufferedReader(new FileReader(train));
        String line = textFile.readLine();
        line = textFile.readLine();
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
        for (int i = 1; i <= n; i++) {
            User user = new User(ids[i]);
            users[i - 1] = user;
            findUserById.put(ids[i] , user);
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
	// 读取 Qos 表格,补充 user site 类
    public static void readQos(int qos ,int usersLength , int sitesLength , String pathname ) throws IOException {
        File train = new File(pathname);
        BufferedReader textFile = null;
        textFile = new BufferedReader(new FileReader(train));
        String[] usersId = textFile.readLine().split(",");
        int temp = 0;
        int[][] qosMap = new int[sitesLength][usersLength];
        String line = "";
        while ((line = textFile.readLine()) != null){
            String[] qosline = line.split(",");
            Site site = findSiteById.get(qosline[0]);
            for (int i = 1; i <= usersLength; i++) {
                if (Integer.valueOf(qosline[i]) < qos){
                    User user = findUserById.get(usersId[i]);
                    site.userCanUser.add(user);
                    site.numsUserCanUser++;
                    user.availableSites.add(site);
                    user.availableSitesSize++;
                }
            }
            temp++;
        }
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
            Site site1 = new Site(site[0] , Integer.valueOf(site[1]));
            sites.add(site1);
            findSiteById.put(site[0] , site1);
        }
        return sites;
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

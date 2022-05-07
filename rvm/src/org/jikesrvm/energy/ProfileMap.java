package org.jikesrvm.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jikesrvm.VM;


public class ProfileMap {
	/**Record profiling information for each method */
	public static ConcurrentHashMap<String, List<MethodProfile>> profileMap;
	/**Record irst invocation. This only be used when the number of recursive calls is more than a certain threshold*/
	public static ConcurrentHashMap<String, Double[]> firtInvokMap;
	/**Number of threshold for recursive calls */
	public static int RECURSIVE_NUM = 10;
	/**long method set*/
	public static ConcurrentHashMap<Integer, Boolean> longMethodMap;
	/**short method set*/
	public static ConcurrentHashMap<Integer, Boolean> shortMethodMap;
	
	public static void initProfileMap() {
		profileMap = new ConcurrentHashMap<String, List<MethodProfile>>();
//		firtInvokMap = new ConcurrentHashMap<String, Double[]>();
		longMethodMap = new ConcurrentHashMap<Integer, Boolean>();
		shortMethodMap = new ConcurrentHashMap<Integer, Boolean>();
	}
	
	public static String createKey(int threadId, int methodId) {
		return threadId + "#" + methodId;
		
	}
	
	/**
	 * Insert long method into the map
	 * @param eventId
	 * @param threadId
	 * @param methodId
	 * @param value
	 */
	public static void insertLongMethodMap(int cmid) {
		longMethodMap.put(cmid, true);
	}
	
	public static boolean isLongMethod(int cmid) {
		return longMethodMap.containsKey(cmid);
	}
	/**
	 * Insert short method into the map
	 * @param eventId
	 * @param threadId
	 * @param methodId
	 * @param value
	 */
	public static void insertShortMethodMap(int cmid) {
		shortMethodMap.put(cmid, true);
	}
	
	public static boolean isShortMethodMap(int cmid) {
		return shortMethodMap.containsKey(cmid);
	}
	
	public static void put(String key, Double[] profileAttrs) {
		if (profileMap.containsKey(key)) {
			
			List<MethodProfile> profiles = profileMap.get(key);
			MethodProfile firstEntry = profiles.get(0);
			//The number of recursive calls is greater than a threshold, we don't profile it
			if (profiles.size() >= RECURSIVE_NUM) {
				//Let the first entry record the total number of recursive call for this method
				String[] info = key.split("#");
				firstEntry.isOverProfiled = true;
				//Keep counting the number of recursive calls
				firstEntry.count++;
				return;
			}
			firstEntry.count++;
			profiles.add(new MethodProfile(profiles.size() + 1, profileAttrs));
		} else {
			
			List<MethodProfile> profiles = new ArrayList<MethodProfile>();
			profiles.add(new MethodProfile(1, profileAttrs));
			
			profileMap.put(key, profiles);
//			firtInvokMap.put(key, counters);
		}
	}
	
	/**
	 * Get specific counter value
	 * @param eventId
	 * @param threadId
	 * @param methodId
	 * @return
	 */
	public static double getValue(int eventId, String key) {
		if (profileMap.containsKey(key)) {
			List<MethodProfile> list = profileMap.get(key);
			MethodProfile entry = list.get(list.size() - 1);
			
			return entry.attrs[eventId] != null ? entry.attrs[eventId] : 0.0d;
		}
		return 0.0d;
	}
	
	/**
	 * Get profiling information list
	 * @param threadId
	 * @param methodId
	 * @return
	 */
	public static Double[] getList(String key) {
		if (profileMap.containsKey(key)) {
			List<MethodProfile> list = profileMap.get(key);
			MethodProfile entry = list.get(list.size() - 1);
			return entry.attrs;
		}
		return null;
	}
	
	/**
	 * Remove the most recent method profile (behave like a stack)
	 * @param threadId
	 * @param methodId
	 * @return
	 */
	public static Double[] remove(String key) {
		List<MethodProfile> list = profileMap.get(key);
		//Only one profiling entry
		if (list.size() == 1) {
			return profileMap.remove(key).get(0).attrs;
		}
		
		//If the current method is recursively called over the threshold, we only need to calculate first call
		MethodProfile firstEntry = list.get(0);

		
		if (firstEntry.isOverProfiled) {
			String[] info = key.split("#");
			if (--firstEntry.count == 1) {
				return profileMap.remove(key).get(0).attrs;
			}
			//Record the profiling information of first calls only. Drop the other recursive calls
			return null;
		}
		--firstEntry.count;
		//Number of recursive call is less than threshold
		return list.remove(list.size() - 1).attrs; 
		
		
	}
	public static boolean contaisKey(int eventId, String key) {
		return profileMap.containsKey(key);
	}
	
}

class MethodProfile {
	/**Count the number of recursive calls*/
	public int count = 0;
	/**Profiling attributes of the method*/
	public Double[] attrs;
	/**Indicate if the number of recursive calls is more than threshold*/
	public boolean isOverProfiled = false;
	
	public MethodProfile (int count, Double[] attrs) {
		this.count = count;
		this.attrs = attrs;
	}
}

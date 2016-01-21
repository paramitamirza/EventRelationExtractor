package task;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SortMapByValue {
	public static boolean ASC = true;
	public static boolean DESC = false;
	
	public static void main(String[] args) {
		//Creating dummy unsorted map
		Map<Integer, Double> unsortMap = new HashMap<Integer, Double>();
		unsortMap.put(1, 0.55);
		unsortMap.put(0, 0.80);
		unsortMap.put(3, 0.20);
		unsortMap.put(2, 0.70);
		
		System.out.println("Before sorting....");
		printMap(unsortMap);
		
		System.out.println("After sorting descending order....");
		Map<Integer, Double> sortedMapDesc = sortByComparator(unsortMap, DESC);
		printMap(sortedMapDesc);
	}
	
	public static Map<Integer, Double> sortByComparator(Map<Integer, Double> unsortMap, final boolean order) {
		List<Map.Entry<Integer, Double>> list = new LinkedList<Map.Entry<Integer, Double>>(unsortMap.entrySet());
		
		//Sorting the list based on values
		Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>()
				{
					public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
						if (order) {
							return o1.getValue().compareTo(o2.getValue());
						} else {
							return o2.getValue().compareTo(o1.getValue());
						}
					}
				});
		
		//Maintaining insertion order with the help of LinkedList
		Map<Integer, Double> sortedMap = new LinkedHashMap<Integer, Double>();
		for (Map.Entry<Integer, Double> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		
		return sortedMap;
	}
	
	public static void printMap(Map<Integer, Double> map) {
		for(Map.Entry<Integer, Double> entry : map.entrySet()) {
			System.out.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
		}
	}
}

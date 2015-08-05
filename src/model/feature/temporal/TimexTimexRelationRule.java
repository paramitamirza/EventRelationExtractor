package model.feature.temporal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import parser.entities.Timex;

public class TimexTimexRelationRule {
	
	private String relType;
	
	public TimexTimexRelationRule(Timex t1, Timex t2, Timex dct) {
		
		this.setRelType("O");
		
		if (!t1.getID().equals(t2.getID())) {
			if (t1.getType().equals("DATE") && t2.getType().equals("TIME") &&
					t2.getValue().contains(t1.getValue())) {
				this.setRelType("INCLUDES");
			} else if (t1.getType().equals("TIME") && t2.getType().equals("DATE") &&
					t1.getValue().contains(t2.getValue())) {
				this.setRelType("IS_INCLUDED");
			} else if (t1.getType().equals("DATE") && t2.getType().equals("DATE")) {
				if (t1.getValue().equals(t2.getValue())) {
					this.setRelType("SIMULTANEOUS");
				} else if (t2.getValue().contains(t1.getValue())) {
					this.setRelType("INCLUDES");
				} else if (t1.getValue().contains(t2.getValue())) {
					this.setRelType("IS_INCLUDED");
				} else {
					this.setRelType(getTmxDateRelation(t1.getValue(), t2.getValue(), dct.getValue()));
				}
			} else if (t1.getType().equals("TIME") && t2.getType().equals("TIME")) {
				String[] dateTime1 = {t1.getValue(), ""};
				if (t1.getValue().split("T").length > 1) {
					dateTime1 = t1.getValue().split("T");
				} 
				String[] dateTime2 = {t2.getValue(), ""};
				if (t2.getValue().split("T").length > 1) {
					dateTime2 = t2.getValue().split("T");
				} 
				if (dateTime1[0].equals(dateTime2[0])) {
					if (!dateTime1[1].equals("") && !dateTime1[2].equals("")) {
						this.setRelType(getTimeRelation(getTimeComponents(dateTime1[1]), 
								getTimeComponents(dateTime2[1])));
					}
				} else {
					this.setRelType(getTmxDateRelation(dateTime1[0], dateTime2[0], dct.getValue()));
				}
			}
		}
	}
	
	private String getTmxDateRelation(String date1, String date2, String dctStr) {
		String[] eraArr = {"PAST_REF", "PRESENT_REF", "FUTURE_REF"};
		List<String> eraList = Arrays.asList(eraArr);
		
		Date d1 = getDateComponents(date1);
		Date d2 = getDateComponents(date2);
		Date dct = getDateComponents(dctStr.split("T")[0]);
		
		if (!d1.getEra().equals("") && !d2.getEra().equals("") &&
				eraList.contains(d1.getEra()) && eraList.contains(d2.getEra())) {
			if (eraList.indexOf(d1.getEra()) < eraList.indexOf(d2.getEra())) {
				return "BEFORE";
			} else if (eraList.indexOf(d1.getEra()) > eraList.indexOf(d2.getEra())) {
				return "AFTER";
			} else {
				return "SIMULTANEOUS";
			}
		} else if (!d1.getEra().equals("") && d2.getEra().equals("") &&
				eraList.contains(d1.getEra())) {
			if (d1.getEra().equals("PAST_REF")) {
				if (getDateRelation(d2, dct).equals("BEFORE")) {
					return "INCLUDES";
				} else {
					return "BEFORE";
				}
			} else if (d1.getEra().equals("PRESENT_REF")) {
				if (getDateRelation(d2, dct).equals("SIMULTANEOUS")) {
					return "INCLUDES";
				} else {
					return getDateRelation(d2, dct);
				}
			} else if (d1.getEra().equals("FUTURE_REF")) {
				if (getDateRelation(d2, dct).equals("AFTER")) {
					return "INCLUDES";
				} else {
					return "AFTER";
				}
			}
		} else if (d1.getEra().equals("") && !d2.getEra().equals("") &&
				eraList.contains(d2.getEra())) {
			if (d2.getEra().equals("PAST_REF")) {
				if (getDateRelation(d1, dct).equals("BEFORE")) {
					return "IS_INCLUDED";
				} else {
					return "AFTER";
				}
			} else if (d2.getEra().equals("PRESENT_REF")) {
				if (getDateRelation(d1, dct).equals("SIMULTANEOUS")) {
					return "IS_INCLUDED";
				} else {
					return getDateRelation(d1, dct);
				}
			} else if (d2.getEra().equals("FUTURE_REF")) {
				if (getDateRelation(d1, dct).equals("AFTER")) {
					return "IS_INCLUDED";
				} else {
					return "BEFORE";
				}
			}
		} else {
			return getDateRelation(d1, d2);
		}
		return null;
	}
	
	private String getDateRelation(Date d1, Date d2) {
		if (d1.getYear() < d2.getYear()) {
			return "BEFORE";
		} else if (d1.getYear() > d2.getYear()) {
			return "AFTER";
		} else {
			if (!d1.getMonthArr().isEmpty() && d2.getMonthArr().isEmpty()) {
				if (d2.getMonth() == 0) {
					return "IS_INCLUDED";
				} else {
					if (d1.getMonthArr().contains(d2.getMonth())) { 
						return "INCLUDES";						
					} else if (d2.getMonth() < d1.getMonthArr().get(0)) { 
						return "AFTER";
					} else if (d2.getMonth() > d1.getMonthArr().get(d1.getMonthArr().size()-1)) { 
						return "BEFORE";
					}
				}
			} else if (d1.getMonthArr().isEmpty() && !d2.getMonthArr().isEmpty()) {
				if (d1.getMonth() == 0) {
					return "INCLUDES";
				} else {
					if (d2.getMonthArr().contains(d1.getMonth())) { 
						return "IS_INCLUDED";						
					} else if (d1.getMonth() < d2.getMonthArr().get(0)) { 
						return "BEFORE";
					} else if (d1.getMonth() > d2.getMonthArr().get(d2.getMonthArr().size()-1)) { 
						return "AFTER";
					}
				} 
			} else if (!d1.getMonthArr().isEmpty() && !d2.getMonthArr().isEmpty()) {
				if (d1.getMonthArr().get(0) == d2.getMonthArr().get(0)) {
					return "SIMULTANEOUS";
				} else if (d1.getMonthArr().get(d1.getMonthArr().size()-1) < d2.getMonthArr().get(0)) {
					return "BEFORE";
				} else if (d2.getMonthArr().get(d2.getMonthArr().size()-1) < d1.getMonthArr().get(0)) {
					return "AFTER";
				}
			} else {
				if (d1.getMonth() < d2.getMonth()) {
					return "BEFORE";
				} else if (d1.getMonth() > d2.getMonth()) {
					return "AFTER";
				} else {
					if (d1.getDay() < d2.getDay()) {
						return "BEFORE";
					} else if (d1.getDay() > d2.getDay()) {
						return "AFTER";
					} else {
						return "SIMULTANEOUS";
					}
				}
			}
		}
		return null;
	}
	
	private String getTimeRelation(Time t1, Time t2) {
		String[] partDayArr = {"MO", "AF", "EV", "NI"};
		List<String> partDayList = Arrays.asList(partDayArr);
		
		if (!t1.getRange().isEmpty() && t2.getRange().isEmpty()) {
			if (t1.getRange().contains(t2.getHour())) { 
				return "INCLUDES";						
			} else if (t2.getHour() < t1.getRange().get(0)) { 
				return "AFTER";
			} else if (t2.getHour() > t1.getRange().get(t1.getRange().size()-1)) { 
				return "BEFORE";
			}
		} else if (t1.getRange().isEmpty() && !t2.getRange().isEmpty()) {
			if (t2.getRange().contains(t1.getHour())) { 
				return "IS_INCLUDED";						
			} else if (t1.getHour() < t2.getRange().get(0)) { 
				return "BEFORE";
			} else if (t1.getHour() > t2.getRange().get(t2.getRange().size()-1)) { 
				return "AFTER";
			} 
		} else if (!t1.getRange().isEmpty() && !t2.getRange().isEmpty()) {
			if (partDayList.contains(t1.getPartDay()) && partDayList.contains(t2.getPartDay())) {
				if (partDayList.indexOf(t1.getPartDay()) < partDayList.indexOf(t2.getPartDay())) {
					return "BEFORE";
				} else if (partDayList.indexOf(t1.getPartDay()) > partDayList.indexOf(t2.getPartDay())) {
					return "AFTER";
				} else {
					return "SIMULTANEOUS";
				}
			}
		} else {
			if (t1.getHour() < t2.getHour()) {
				return "BEFORE";
			} else if (t1.getHour() > t2.getHour()) {
				return "AFTER";
			} else {
				if (t1.getMinute() < t2.getMinute()) {
					return "BEFORE";
				} else if (t1.getMinute() > t2.getMinute()) {
					return "AFTER";
				} else {
					if (t1.getSecond() < t2.getSecond()) {
						return "BEFORE";
					} else if (t1.getSecond() > t2.getSecond()) {
						return "AFTER";
					} else {
						return "SIMULTANEOUS";
					}
				}
			}
		}
		return null;
	}
	
	private Date getDateComponents(String date) {
		Date d = new Date();
		
		String[] cols = date.split("-");
		if (cols.length == 1) {
			if (cols[0].matches("\\d+")) {
				d.setYear(Integer.valueOf(cols[0]));
			} else {
				d.setEra(cols[0]);
			}
		} else if (cols.length == 2) {
			if (cols[0].matches("\\d+") && cols[1].matches("\\d+")) {
				d.setYear(Integer.valueOf(cols[0])); 
				d.setMonth(Integer.valueOf(cols[1]));
			} else if (cols[1].startsWith("Q")) {
				if (cols[1].endsWith("1")) {
					d.getMonthArr().add(1);
					d.getMonthArr().add(2);
					d.getMonthArr().add(3);
				} else if (cols[1].endsWith("2")) {
					d.getMonthArr().add(4);
					d.getMonthArr().add(5);
					d.getMonthArr().add(6);
				} else if (cols[1].endsWith("3")) {
					d.getMonthArr().add(7);
					d.getMonthArr().add(8);
					d.getMonthArr().add(9);
				} else if (cols[1].endsWith("4")) {
					d.getMonthArr().add(10);
					d.getMonthArr().add(11);
					d.getMonthArr().add(12);
				}
			}
		} else if (cols.length == 3 && cols[0].matches("\\d+") && 
				cols[1].matches("\\d+") && cols[2].matches("\\d+")) {
			d.setYear(Integer.valueOf(cols[0])); 
			d.setMonth(Integer.valueOf(cols[1]));
			d.setDay(Integer.valueOf(cols[2]));
		}
		
		return d;
	}
	
	private Time getTimeComponents(String time) {
		Time t = new Time();
		
		String[] cols = time.split("-");
		if (cols.length == 1) {
			if (cols[0].matches("\\d+")) {
				t.setHour(Integer.valueOf(cols[0]));
			} else {
				if (cols[0].equals("MO")) {
					t.setPartDay(cols[0]);
					t.getRange().add(1); t.getRange().add(2);
					t.getRange().add(3); t.getRange().add(4);
					t.getRange().add(5); t.getRange().add(6);
					t.getRange().add(7); t.getRange().add(8);
					t.getRange().add(9); t.getRange().add(10);
					t.getRange().add(11);
				} else if (cols[0].equals("AF")) {
					t.setPartDay(cols[0]);
					t.getRange().add(13); t.getRange().add(14);
					t.getRange().add(15); t.getRange().add(16);
				} else if (cols[0].equals("EV")) {
					t.setPartDay(cols[0]);
					t.getRange().add(17); t.getRange().add(18);
					t.getRange().add(19); t.getRange().add(20);
				} else if (cols[0].equals("NI")) {
					t.setPartDay(cols[0]);
					t.getRange().add(21); t.getRange().add(22);
					t.getRange().add(23); t.getRange().add(24);
					t.getRange().add(0);
				}
			} 
		} else if (cols.length == 2 && 
				cols[0].matches("\\d+") && cols[1].matches("\\d+")) {
			t.setHour(Integer.valueOf(cols[0]));
			t.setMinute(Integer.valueOf(cols[1]));
		} else if (cols.length == 3 && cols[0].matches("\\d+") && 
				cols[1].matches("\\d+") && cols[2].matches("\\d+")) {
			t.setHour(Integer.valueOf(cols[0]));
			t.setMinute(Integer.valueOf(cols[1]));
			t.setSecond(Integer.valueOf(cols[2]));
		}
		
		return t;
	}
	
	public String getRelType() {
		return relType;
	}

	public void setRelType(String relType) {
		this.relType = relType;
	}

	private class Date {
		private Integer year;
		private Integer month;
		private Integer day;
		private ArrayList<Integer> monthArr;
		private String era;
		public Date() {
			this.setYear(0); this.setMonth(0); this.setDay(0);
			this.setMonthArr(new ArrayList<Integer>());
			this.era = "";
		}
		public Integer getYear() {
			return year;
		}
		public void setYear(Integer year) {
			this.year = year;
		}
		public Integer getMonth() {
			return month;
		}
		public void setMonth(Integer month) {
			this.month = month;
		}
		public Integer getDay() {
			return day;
		}
		public void setDay(Integer day) {
			this.day = day;
		}
		public ArrayList<Integer> getMonthArr() {
			return monthArr;
		}
		public void setMonthArr(ArrayList<Integer> monthArr) {
			this.monthArr = monthArr;
		}
		public String getEra() {
			return era;
		}
		public void setEra(String era) {
			this.era = era;
		}
	}
	
	private class Time {
		private Integer hour;
		private Integer minute;
		private Integer second;
		private String partDay;
		private ArrayList<Integer> range;
		public Time() {
			this.setHour(0); this.setMinute(0); this.setSecond(0);
			this.setRange(new ArrayList<Integer>());
		}
		public Integer getHour() {
			return hour;
		}
		public void setHour(Integer hour) {
			this.hour = hour;
		}
		public Integer getMinute() {
			return minute;
		}
		public void setMinute(Integer minute) {
			this.minute = minute;
		}
		public Integer getSecond() {
			return second;
		}
		public void setSecond(Integer second) {
			this.second = second;
		}
		public ArrayList<Integer> getRange() {
			return range;
		}
		public void setRange(ArrayList<Integer> range) {
			this.range = range;
		}
		public String getPartDay() {
			return partDay;
		}
		public void setPartDay(String partDay) {
			this.partDay = partDay;
		}
	}

}

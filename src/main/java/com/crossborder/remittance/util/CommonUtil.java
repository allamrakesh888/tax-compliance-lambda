package com.crossborder.remittance.util;

import java.time.LocalDate;
import java.time.ZoneId;

public class CommonUtil {
	
	// format "2025-26"
	public static String getCurrentFinancialYear() {

		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

		int currentYear = today.getYear();
		int currentMonth = today.getMonthValue(); // Jan = 1, April = 4

		int startYear;
		int endYear;

		if (currentMonth < 4) {
			startYear = currentYear - 1;
			endYear = currentYear;
		} else {
			startYear = currentYear;
			endYear = currentYear + 1;
		}

		// 2026 becomes 26
		String endYearShort = String.format("%02d", endYear % 100);

		return startYear + "-" + endYearShort;
	}
}

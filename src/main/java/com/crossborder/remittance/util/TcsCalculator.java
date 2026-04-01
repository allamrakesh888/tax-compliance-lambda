package com.crossborder.remittance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TcsCalculator {

	private static final BigDecimal LRS_THRESHOLD = new BigDecimal("1000000.00");
	private static final BigDecimal RATE_ZERO = BigDecimal.ZERO;
	private static final BigDecimal RATE_5_PERCENT = new BigDecimal("0.05");
	private static final BigDecimal RATE_20_PERCENT = new BigDecimal("0.20");

	public static BigDecimal calculateTCS(BigDecimal currentRemitted, BigDecimal remitAmount, String purpose) {

		BigDecimal amountBelowThreshold = BigDecimal.ZERO;
		BigDecimal amountAboveThreshold = BigDecimal.ZERO;
		BigDecimal newTotal = currentRemitted.add(remitAmount);

		if (currentRemitted.compareTo(LRS_THRESHOLD) >= 0) {
			// Already completely over the limit
			amountAboveThreshold = remitAmount;
		} else if (newTotal.compareTo(LRS_THRESHOLD) <= 0) {
			// Still completely under the limit
			amountBelowThreshold = remitAmount;
		} else {
			// Crossing the threshold mid-transaction
			amountBelowThreshold = LRS_THRESHOLD.subtract(currentRemitted);
			amountAboveThreshold = newTotal.subtract(LRS_THRESHOLD);
		}

		BigDecimal rateBelowThreshold;
		BigDecimal rateAboveThreshold;

		switch (purpose) {
		case "S0305-EDUCATION_LOAN" -> {
			rateBelowThreshold = RATE_ZERO;
			rateAboveThreshold = RATE_ZERO;
		}
		case "S0306-TRAVEL_TOURISM" -> {
			rateBelowThreshold = RATE_5_PERCENT;
			rateAboveThreshold = RATE_20_PERCENT;
		}
		case "S0305-EDUCATION_SELF_FUNDED", "S0304-MEDICAL_TREATMENT" -> {
			rateBelowThreshold = RATE_ZERO;
			rateAboveThreshold = RATE_5_PERCENT;
		}
		default -> {
			// S0001-INVESTMENT_ABROAD, S1301-FAMILY_MAINTENANCE
			rateBelowThreshold = RATE_ZERO;
			rateAboveThreshold = RATE_20_PERCENT;
		}
		}

		// final tax calculation
		BigDecimal taxBelow = amountBelowThreshold.multiply(rateBelowThreshold);
		BigDecimal taxAbove = amountAboveThreshold.multiply(rateAboveThreshold);

		return taxBelow.add(taxAbove).setScale(2, RoundingMode.HALF_UP);
	}
}

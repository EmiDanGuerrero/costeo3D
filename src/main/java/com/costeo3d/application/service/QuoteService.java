package com.costeo3d.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.validation.ValidationUtils;

public class QuoteService {

    private static final int VALIDITY_HOURS = 72;
    private static final int MONEY_SCALE = 2;

    public Quote createQuote(
            Customer customer,
            List<QuoteItem> items,
            LocalDateTime issuedAt)
            throws BusinessValidationException {

        ValidationUtils.requireNotNull(customer, "Customer");
        ValidationUtils.requireNotNull(items, "Quote items");
        ValidationUtils.requireNotNull(issuedAt, "Issue date");

        validateItems(items);

        LocalDateTime expiresAt = calculateExpiration(issuedAt);

        Quote quote = new Quote(customer, issuedAt, expiresAt);

        BigDecimal total = BigDecimal.ZERO;

        for (QuoteItem item : items) {
            quote.addItem(item);

            BigDecimal itemTotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

            total = total.add(itemTotal);
        }

        quote.updateTotal(
                total.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

        return quote;
    }

    public LocalDateTime calculateExpiration(LocalDateTime issuedAt) {
        ValidationUtils.requireNotNull(issuedAt, "Issue date");

        LocalDateTime expiration = issuedAt;
        int remainingHours = VALIDITY_HOURS;

        while (remainingHours > 0) {
            expiration = expiration.plusHours(1);

            DayOfWeek day = expiration.getDayOfWeek();

            if (day != DayOfWeek.SATURDAY
                    && day != DayOfWeek.SUNDAY) {
                remainingHours--;
            }
        }

        return expiration;
    }

    public void acceptQuote(Quote quote, LocalDateTime currentDateTime)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(quote, "Quote");
        ValidationUtils.requireNotNull(
                currentDateTime, "Current date and time");

        if (quote.getStatus() == QuoteStatus.VALID
                && !currentDateTime.isBefore(quote.getExpiresAt())) {

            quote.expire();

            throw new BusinessValidationException(
                    "An expired quote cannot be accepted.");
        }

        quote.accept();
    }

    public boolean expireIfNecessary(
            Quote quote,
            LocalDateTime currentDateTime)
            throws BusinessRuleException {

        ValidationUtils.requireNotNull(quote, "Quote");
        ValidationUtils.requireNotNull(
                currentDateTime, "Current date and time");

        if (quote.getStatus() == QuoteStatus.VALID
                && !currentDateTime.isBefore(quote.getExpiresAt())) {

            quote.expire();
            return true;
        }

        return false;
    }

    private void validateItems(List<QuoteItem> items)
            throws BusinessValidationException {

        if (items.isEmpty()) {
            throw new BusinessValidationException(
                    "A quote must contain at least one item.");
        }

        BigDecimal totalAmortization = BigDecimal.ZERO;

        for (QuoteItem item : items) {
            ValidationUtils.requireNotNull(item, "Quote item");
            ValidationUtils.requirePositive(
                    item.getQuantity(), "Quantity");
            ValidationUtils.requireNonNegative(
                    item.getUnitPrice(), "Unit price");
            ValidationUtils.requireNonNegative(
                    item.getAmortizationCost(), "Amortization cost");

            if (item.getMaterials().isEmpty()) {
                throw new BusinessValidationException(
                        "Each quote item must contain at least one material.");
            }

            totalAmortization = totalAmortization.add(
                    item.getAmortizationCost());
        }

        if (totalAmortization.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessValidationException(
                    "A quote must include an amortization cost.");
        }
    }
}
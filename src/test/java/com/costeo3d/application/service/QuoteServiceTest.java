package com.costeo3d.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.domain.model.QuoteMaterialItem;

class QuoteServiceTest {

    private final QuoteService quoteService = new QuoteService();

    @Test
    void shouldCalculateSeventyTwoBusinessHours() {
        LocalDateTime friday =
                LocalDateTime.of(
                        2026,
                        10,
                        16,
                        10,
                        0);

        LocalDateTime expiration =
                quoteService.calculateExpiration(friday);

        LocalDateTime expected =
                LocalDateTime.of(
                        2026,
                        10,
                        21,
                        10,
                        0);

        assertEquals(expected, expiration);
    }

    @Test
    void shouldCreateQuoteWithCalculatedTotal()
            throws BusinessValidationException {

        Customer customer = new Customer(
                "Juan Perez",
                "3874000001",
                "juan@example.com");

        Material material = new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50"));

        Model3D model = new Model3D(
                "Geometric Planter",
                null,
                "Printables",
                new BigDecimal("125"),
                300);

        QuoteMaterialItem materialItem =
                new QuoteMaterialItem(
                        material,
                        new BigDecimal("125"),
                        new BigDecimal("18.50"));

        QuoteItem item = new QuoteItem(
                model,
                "Geometric Planter",
                2,
                300,
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("30"),
                new BigDecimal("3396.25"));

        item.addMaterial(materialItem);

        Quote quote = quoteService.createQuote(
                customer,
                List.of(item),
                LocalDateTime.of(
                        2026,
                        10,
                        16,
                        10,
                        0));

        assertEquals(
                new BigDecimal("6792.50"),
                quote.getTotal());

        assertEquals(1, quote.getItems().size());
    }

    @Test
    void shouldRejectQuoteWithoutAmortization() {
        Customer customer = new Customer(
                "Juan Perez",
                "3874000001",
                "juan@example.com");

        Material material = new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50"));

        QuoteItem item = new QuoteItem(
                null,
                "Manual item",
                1,
                120,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("20"),
                new BigDecimal("500"));

        item.addMaterial(
                new QuoteMaterialItem(
                        material,
                        new BigDecimal("20"),
                        new BigDecimal("18.50")));

        assertThrows(
                BusinessValidationException.class,
                () -> quoteService.createQuote(
                        customer,
                        List.of(item),
                        LocalDateTime.now()));
    }
}
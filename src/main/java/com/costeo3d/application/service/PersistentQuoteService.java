package com.costeo3d.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.costeo3d.domain.enums.QuoteStatus;
import com.costeo3d.domain.exception.BusinessRuleException;
import com.costeo3d.domain.exception.BusinessValidationException;
import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Quote;
import com.costeo3d.domain.model.QuoteItem;
import com.costeo3d.infrastructure.persistence.dao.QuoteDao;

/**
 * Connects the existing quote rules with persistent storage.
 * Business rules remain in QuoteService and Quote; SQL remains in the DAO.
 */
public final class PersistentQuoteService {

    private final QuoteService quoteService;
    private final QuoteDao quoteDao;

    public PersistentQuoteService(QuoteService quoteService, QuoteDao quoteDao) {
        this.quoteService = Objects.requireNonNull(
                quoteService, "QuoteService is required.");
        this.quoteDao = Objects.requireNonNull(quoteDao, "QuoteDao is required.");
    }

    /** Creates and persists a complete quote with its historical details. */
    public Quote createQuote(Customer customer, List<QuoteItem> items,
            LocalDateTime issuedAt) throws BusinessValidationException {
        Quote draft = quoteService.createQuote(customer, items, issuedAt);
        if (customer.getId() == null) {
            throw new BusinessValidationException(
                    "The customer must be saved before creating a quote.");
        }
        return quoteDao.insert(draft);
    }

    public Optional<Quote> findById(int id) {
        return quoteDao.findById(id);
    }

    public List<Quote> findAll() {
        return quoteDao.findAll();
    }

    /** Accepts a valid quote. Expired quotes are recorded as expired even
     * when QuoteService rejects their acceptance. */
    public Quote acceptQuote(int id, LocalDateTime currentDateTime)
            throws BusinessRuleException {
        Quote quote = requireQuote(id);
        try {
            quoteService.acceptQuote(quote, currentDateTime);
        } catch (BusinessValidationException rejected) {
            // QuoteService.expire() changes the freshly loaded quote before
            // throwing when the acceptance deadline has already passed.
            if (quote.getStatus() == QuoteStatus.EXPIRED) {
                saveStatus(quote);
            }
            throw rejected;
        }
        return saveStatus(quote);
    }

    /** Preserves the state transition defined by Quote.cancel(). */
    public Quote cancelQuote(int id) throws BusinessRuleException {
        Quote quote = requireQuote(id);
        quote.cancel();
        return saveStatus(quote);
    }

    /** Updates the database only when the existing QuoteService expires it. */
    public Quote expireIfNecessary(int id, LocalDateTime currentDateTime)
            throws BusinessRuleException {
        Quote quote = requireQuote(id);
        if (quoteService.expireIfNecessary(quote, currentDateTime)) {
            return saveStatus(quote);
        }
        return quote;
    }

    private Quote requireQuote(int id) throws BusinessValidationException {
        return quoteDao.findById(id).orElseThrow(() ->
                new BusinessValidationException("The quote does not exist."));
    }

    private Quote saveStatus(Quote quote) {
        if (!quoteDao.updateStatus(quote)) {
            throw new IllegalStateException(
                    "The quote disappeared before its status could be saved.");
        }
        return quoteDao.findById(quote.getId()).orElseThrow(() ->
                new IllegalStateException("The saved quote could not be retrieved."));
    }
}

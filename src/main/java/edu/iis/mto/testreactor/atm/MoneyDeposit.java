package edu.iis.mto.testreactor.atm;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MoneyDeposit {

    private final String currencyCode;
    private final Map<Banknote, BanknotesPack> deposit;

    public static MoneyDeposit of(Currency currency, List<BanknotesPack> deposit) {
        return new MoneyDeposit(requireNonNull(currency.getCurrencyCode()), requireNonNull(deposit));
    }

    public static MoneyDeposit of(MoneyDeposit other) {
        return of(other.getCurrency(), new ArrayList<>(other.getBanknotes()));
    }

    private MoneyDeposit(String currency, List<BanknotesPack> deposit) {
        this.currencyCode = currency;
        this.deposit = deposit.stream()
                              .collect(Collectors.toMap(BanknotesPack::getDenomination, Function.identity(), (pack1,
                                      pack2) -> BanknotesPack.create(pack1.getCount() + pack2.getCount(), pack1.getDenomination())));
    }

    public Currency getCurrency() {
        return Currency.getInstance(currencyCode);
    }

    public boolean isAvailable(Banknote note, int amount) {
        return getFor(note).getCount() > amount;
    }

    public boolean isAvailable(BanknotesPack banknotes) {
        return isAvailable(banknotes.getDenomination(), banknotes.getCount());
    }

    int getAvailableCountOf(Banknote banknote) {
        return getFor(banknote).getCount();
    }

    private BanknotesPack getFor(Banknote note) {
        return deposit.getOrDefault(note, BanknotesPack.create(0, note));
    }

    // this method has package scope because only ATM can release money from deposit
    void release(BanknotesPack banknotesPack) {
        Banknote denomination = banknotesPack.getDenomination();
        BanknotesPack existing = getFor(denomination);
        int newCount = existing.getCount() - banknotesPack.getCount();
        if (newCount < 0) {
            throw new IllegalArgumentException("unavailable banknotes count " + banknotesPack.getCount());
        }
        this.deposit.put(denomination, BanknotesPack.create(newCount, existing.getDenomination()));
    }

    public List<BanknotesPack> getBanknotes() {
        return deposit.values()
                      .stream()
                      .collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return Objects.hash(currencyCode, deposit);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MoneyDeposit other = (MoneyDeposit) obj;
        return Objects.equals(currencyCode, other.currencyCode) && Objects.equals(deposit, other.deposit);
    }

    @Override
    public String toString() {
        return "MoneyDeposit [currencyCode=" + currencyCode + ", deposit=" + deposit + "]";
    }

}

package edu.iis.mto.testreactor.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import edu.iis.mto.testreactor.atm.bank.AuthorizationToken;
import edu.iis.mto.testreactor.atm.bank.AccountException;
import edu.iis.mto.testreactor.atm.bank.AuthorizationException;
import edu.iis.mto.testreactor.atm.bank.Bank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ATMachineTest {
    @Mock
    Bank bank;
    
    Card card;
    PinCode pin;
    ATMachine atm;
    List<BanknotesPack> banknotes;

    @BeforeEach
    void setUp(){
        atm = ATMachine.of(bank, Currency.getInstance("PLN"));
        banknotes = new ArrayList<>();
        pin = PinCode.createPIN(1, 2, 3, 4);
        card = Card.create("123456789");
    }

    @AfterEach
    void cleanUp(){
        atm = null;
        banknotes = null;
    }

    @Test
    void successfulWithdrawal() throws ATMOperationException {
        BanknotesPack tens = BanknotesPack.create(100, Banknote.PL_20);
        banknotes.add(tens);
        MoneyDeposit deposit = MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes);
        atm.setDeposit(deposit);

        List<BanknotesPack> correctWithdraw = new ArrayList<>();
        correctWithdraw.add(BanknotesPack.create(3, Banknote.PL_20));

        assertEquals(atm.withdraw(pin, card, new Money(60)), Withdrawal.create(correctWithdraw));
    }

    @Test
    void successfulWithdrawalAndCorrectAmountOfDepositLeft() throws ATMOperationException {
        banknotes = List.of(
                BanknotesPack.create(0, Banknote.PL_10),
                BanknotesPack.create(0, Banknote.PL_20),
                BanknotesPack.create(50, Banknote.PL_50),
                BanknotesPack.create(0, Banknote.PL_100),
                BanknotesPack.create(0, Banknote.PL_200),
                BanknotesPack.create(0, Banknote.PL_500)
        );
        atm.setDeposit(MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes));

        List<BanknotesPack> correctWithdraw = new ArrayList<>();
        correctWithdraw.add(BanknotesPack.create(1, Banknote.PL_50));

        MoneyDeposit afterDeposit = MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), List.of(
                BanknotesPack.create(0, Banknote.PL_10),
                BanknotesPack.create(0, Banknote.PL_20),
                BanknotesPack.create(49, Banknote.PL_50),
                BanknotesPack.create(0, Banknote.PL_100),
                BanknotesPack.create(0, Banknote.PL_200),
                BanknotesPack.create(0, Banknote.PL_500)));

        assertEquals(afterDeposit, atm.getCurrentDeposit());
    }

    @Test
    void failedWithdrawalBecauseOfFailedAuthorization() throws AuthorizationException {
        doThrow(AuthorizationException.class).when(bank).autorize(any(), any());
        ATMOperationException errCode = assertThrows(ATMOperationException.class, () -> atm.withdraw(pin, card, new Money(100)));
        assertEquals(errCode.getErrorCode(), ErrorCode.AUTHORIZATION);
    }

    @Test
    void failedWithdrawalBecauseOfWrongCurrency(){
        BanknotesPack hundreds = BanknotesPack.create(100, Banknote.PL_200);
        banknotes.add(hundreds);
        MoneyDeposit deposit = MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes);
        atm.setDeposit(deposit);

        ATMOperationException errCode = assertThrows(ATMOperationException.class, () -> atm.withdraw(pin, card, new Money(200, "USD")));
        assertEquals(errCode.getErrorCode(), ErrorCode.WRONG_CURRENCY);
    }

    @Test
    void failedWithdrawalBecauseOfWrongAmount(){
        BanknotesPack hundreds = BanknotesPack.create(100, Banknote.PL_500);
        banknotes.add(hundreds);
        MoneyDeposit deposit = MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes);
        atm.setDeposit(deposit);

        ATMOperationException errCode = assertThrows(ATMOperationException.class, () -> atm.withdraw(pin, card, new Money(250)));
        assertEquals(errCode.getErrorCode(), ErrorCode.WRONG_AMOUNT);
    }

    @Test
    void failedWithdrawalBecauseOfAccountExceptionThrownByBank() throws AccountException {
        BanknotesPack hundreds = BanknotesPack.create(100, Banknote.PL_500);
        banknotes.add(hundreds);
        MoneyDeposit deposit = MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes);
        atm.setDeposit(deposit);

        doThrow(AccountException.class).when(bank).charge(any(), any());
        ATMOperationException errCode = assertThrows(ATMOperationException.class, () -> atm.withdraw(pin, card, new Money(2000)));
        assertEquals(errCode.getErrorCode(), ErrorCode.NO_FUNDS_ON_ACCOUNT);
    }

    @Test
    void failedWithdrawalBecauseOfLowAmount() {
        BanknotesPack hundreds = BanknotesPack.create(100, Banknote.PL_500);
        banknotes.add(hundreds);
        MoneyDeposit deposit = MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes);
        atm.setDeposit(deposit);

        ATMOperationException errCode = assertThrows(ATMOperationException.class, () -> atm.withdraw(pin, card, new Money(0.1)));
        assertEquals(errCode.getErrorCode(), ErrorCode.WRONG_AMOUNT);
    }

    @Test
    void orderOfCallCheck() throws ATMOperationException, AuthorizationException, AccountException {
        banknotes = List.of(BanknotesPack.create(10, Banknote.PL_50));
        atm.setDeposit(MoneyDeposit.of(atm.getCurrentDeposit().getCurrency(), banknotes));
        AuthorizationToken dummyToken = AuthorizationToken.create("mocky token");
        Money withdrawMoney = new Money(50);
        when(bank.autorize(pin.getPIN(), card.getNumber())).thenReturn(dummyToken);
        atm.withdraw(pin, card, withdrawMoney);

        InOrder callOrder = inOrder(bank);
        callOrder.verify(bank).autorize(pin.getPIN(), card.getNumber());
        callOrder.verify(bank).charge(dummyToken, withdrawMoney);
    }
}
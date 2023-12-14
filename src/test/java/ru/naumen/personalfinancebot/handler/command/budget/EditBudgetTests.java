package ru.naumen.personalfinancebot.handler.command.budget;

import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.naumen.personalfinancebot.bot.MockBot;
import ru.naumen.personalfinancebot.bot.MockMessage;
import ru.naumen.personalfinancebot.configuration.HibernateConfiguration;
import ru.naumen.personalfinancebot.handler.FinanceBotHandler;
import ru.naumen.personalfinancebot.handler.commandData.CommandData;
import ru.naumen.personalfinancebot.model.Budget;
import ru.naumen.personalfinancebot.model.CategoryType;
import ru.naumen.personalfinancebot.model.User;
import ru.naumen.personalfinancebot.repository.TransactionManager;
import ru.naumen.personalfinancebot.repository.budget.BudgetRepository;
import ru.naumen.personalfinancebot.repository.empty.EmptyCategoryRepository;
import ru.naumen.personalfinancebot.repository.empty.EmptyOperationRepository;
import ru.naumen.personalfinancebot.repository.empty.EmptyUserRepository;
import ru.naumen.personalfinancebot.repository.fake.FakeBudgetRepository;

import java.util.List;

/**
 * Тесты для команд "/budget_set_{"expenses" / "income"}"
 */
public class EditBudgetTests {
    /**
     * Фабрика сессий
     */
    private final SessionFactory sessionFactory;

    /**
     * Менеджер транзакций
     */
    private final TransactionManager transactionManager;

    /**
     * Экземпляр класс фейковой реализации бота
     */
    private MockBot mockBot;

    /**
     * Обработчик операций бота
     */
    private FinanceBotHandler botHandler;

    /**
     * Хранилище бюджетов
     */
    private BudgetRepository budgetRepository;

    /**
     * Пользователь
     */
    private User user;

    public EditBudgetTests() {
        this.sessionFactory = new HibernateConfiguration().getSessionFactory();
        this.transactionManager = new TransactionManager(this.sessionFactory);
    }

    /**
     * Переинциализация объектов перед каждым тестом
     */
    @Before
    public void initVariables() {
        this.budgetRepository = new FakeBudgetRepository();
        this.mockBot = new MockBot();
        this.botHandler = new FinanceBotHandler(
                new EmptyUserRepository(),
                new EmptyOperationRepository(),
                new EmptyCategoryRepository(),
                this.budgetRepository,
                this.sessionFactory
        );
        this.user = new User(1, 100);
    }

    /**
     * Тест на редактирование планируемых трат бюджета в текущем месяце
     */
    @Test
    public void editExpensesCurrentBudget() {
        assertEditCurrentBudget("expenses", CategoryType.EXPENSE, "120000", "120 000");
    }

    /**
     * Тест на редактирование планируемых доходов бюджета в текущем месяце
     */
    @Test
    public void editIncomeCurrentBudget() {
        assertEditCurrentBudget("income", CategoryType.INCOME, "150000", "150 000");
    }

    /**
     * Вызов теста на редактирование планируемых трат/доходов бюджета в текущем месяце
     */
    private void assertEditCurrentBudget(String commandPrefix, CategoryType categoryType, String argument,
                                         String expectedValue) {
        transactionManager.produceTransaction(session -> {

            TestYearMonth currentYM = TestYearMonth.current();
            this.budgetRepository.saveBudget(session, new Budget(this.user, 100_000, 90_000, currentYM.getYearMonth()));
            CommandData command = new CommandData(this.mockBot, this.user,
                    "budget_set_" + commandPrefix, List.of(currentYM.getDotFormat(), argument));
            this.botHandler.handleCommand(command, session);

            Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
            MockMessage message = this.mockBot.poolMessageQueue();
            String expectedIncome = "100 000";
            String expectedExpenses = "90 000";
            switch (categoryType) {
                case INCOME:
                    expectedIncome = expectedValue;
                    break;
                case EXPENSE:
                    expectedExpenses = expectedValue;
                    break;
            }
            Assert.assertEquals("""
                    Бюджет на %s %d изменен:
                    Ожидаемые доходы: %s
                    Ожидаемые расходы: %s""".formatted(
                    currentYM.getMonthName(),
                    currentYM.getYear(),
                    expectedIncome,
                    expectedExpenses), message.text());
            Assert.assertEquals(this.user, message.receiver());
        });
    }

    /**
     * Тест на редактирование бюджета в одном из будущих месяцев
     */
    @Test
    public void editFutureBudget() {
        transactionManager.produceTransaction(session -> {
            TestYearMonth futureYM = TestYearMonth.current().plusMonths(1);
            this.budgetRepository.saveBudget(session, new Budget(this.user, 100_000, 90_000, futureYM.getYearMonth()));
            CommandData command = new CommandData(this.mockBot, this.user,
                    "budget_set_income", List.of(futureYM.getDotFormat(), "200000"));
            this.botHandler.handleCommand(command, session);

            Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
            MockMessage message = this.mockBot.poolMessageQueue();
            Assert.assertEquals("""
                    Бюджет на %s %d изменен:
                    Ожидаемые доходы: 200 000
                    Ожидаемые расходы: 90 000""".formatted(
                    futureYM.getMonthName(),
                    futureYM.getYear()), message.text());
            Assert.assertEquals(this.user, message.receiver());
        });
    }

    /**
     * Тест на редактирование бюджета в одном из прошлых месяцев (должна выйти ошибка)
     */
    @Test
    public void editOldBudget() {
        transactionManager.produceTransaction(session -> {
            TestYearMonth futureYM = TestYearMonth.current().minusMonths(1);
            this.budgetRepository.saveBudget(session, new Budget(this.user, 100_000, 90_000, futureYM.getYearMonth()));
            CommandData command = new CommandData(this.mockBot, this.user,
                    "budget_set_income", List.of(futureYM.getDotFormat(), "200000"));
            this.botHandler.handleCommand(command, session);

            Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
            MockMessage message = this.mockBot.poolMessageQueue();
            Assert.assertEquals("Вы не можете изменять бюджеты за прошедшие месяцы!", message.text());
            Assert.assertEquals(this.user, message.receiver());
        });
    }

    /**
     * Тест на редактирование несуществующего бюджета (должна выйти ошибка)
     */
    @Test
    public void editNonExistentBudget() {
        transactionManager.produceTransaction(session -> {
            TestYearMonth currentYM = TestYearMonth.current();
            CommandData command = new CommandData(this.mockBot, this.user,
                    "budget_set_expenses", List.of(currentYM.getDotFormat(), "1000"));
            this.botHandler.handleCommand(command, session);

            Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
            MockMessage message = this.mockBot.poolMessageQueue();
            Assert.assertEquals("Бюджет на этот период не найден! Создайте его командой " +
                    "/budget_create [mm.yyyy - месяц.год] [ожидаемый доход] [ожидаемый расходы]", message.text());
            Assert.assertEquals(this.user, message.receiver());
        });
    }

    /**
     * Тест на вывоз команды с неправильной датой
     */
    @Test
    public void wrongDateArg() {
        String[] wrongDates = new String[]{"1.2023", "01.23", ".2023", ".23", "0.23", "2023", "01", "1", "_"};
        transactionManager.produceTransaction(session -> {
            for (String wrongDate : wrongDates) {
                CommandData command = new CommandData(this.mockBot, this.user,
                        "budget_set_expenses", List.of(wrongDate, "100000"));
                this.botHandler.handleCommand(command, session);

                Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
                MockMessage message = this.mockBot.poolMessageQueue();
                Assert.assertEquals("Дата введена неверно! Введите ее в формате [mm.yyyy - месяц.год]",
                        message.text());
                Assert.assertEquals(this.user, message.receiver());
            }
        });
    }

    /**
     * Тест на вывоз команды с неправильным числом
     */
    @Test
    public void wrongAmountArgs() {
        String[] wrongAmountArgs = new String[]{"0", "-100"};
        TestYearMonth currentYM = TestYearMonth.current();
        transactionManager.produceTransaction(session -> {
            for (String wrongAmount : wrongAmountArgs) {
                CommandData command = new CommandData(this.mockBot, this.user,
                        "budget_set_expenses", List.of(currentYM.getDotFormat(), wrongAmount));
                this.botHandler.handleCommand(command, session);

                Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
                MockMessage message = this.mockBot.poolMessageQueue();
                Assert.assertEquals("Все суммы должны быть больше нуля!", message.text());
                Assert.assertEquals(this.user, message.receiver());
            }
        });
    }

    /**
     * Тест на вывоз команды с полностью неправильными аргументами
     */
    @Test
    public void wrongEntireArgs() {
        String currentDateArg = TestYearMonth.current().getDotFormat();
        List<List<String>> wrongArgsCases = List.of(
                List.of(),
                List.of(currentDateArg, "1", "1", "1")
        );

        transactionManager.produceTransaction(session -> {
            for (List<String> wrongArgs : wrongArgsCases) {
                CommandData command = new CommandData(this.mockBot, this.user,
                        "budget_set_expenses", wrongArgs);
                this.botHandler.handleCommand(command, session);

                Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
                MockMessage message = this.mockBot.poolMessageQueue();
                Assert.assertEquals("Неверно введена команда! Введите " +
                        "/budget_set_[income/expenses] [mm.yyyy - месяц.год] [ожидаемый доход/расход]", message.text());
                Assert.assertEquals(this.user, message.receiver());
            }
        });
    }
}
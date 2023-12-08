package ru.naumen.personalfinancebot.handler.command;

import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.naumen.personalfinancebot.bot.MockBot;
import ru.naumen.personalfinancebot.bot.MockMessage;
import ru.naumen.personalfinancebot.configuration.HibernateConfiguration;
import ru.naumen.personalfinancebot.handler.event.HandleCommandEvent;
import ru.naumen.personalfinancebot.model.Category;
import ru.naumen.personalfinancebot.model.CategoryType;
import ru.naumen.personalfinancebot.model.User;
import ru.naumen.personalfinancebot.repository.TestHibernateUserRepository;
import ru.naumen.personalfinancebot.repository.TransactionManager;
import ru.naumen.personalfinancebot.repository.category.CategoryRepository;
import ru.naumen.personalfinancebot.repository.category.HibernateCategoryRepository;
import ru.naumen.personalfinancebot.repository.operation.HibernateOperationRepository;
import ru.naumen.personalfinancebot.repository.operation.OperationRepository;
import ru.naumen.personalfinancebot.service.ReportService;

import java.time.YearMonth;
import java.util.List;


/**
 * Класс для тестирования команды "/report_expense"
 */
public class ReportExpensesHandlerTest {
    /**
     * Фабрика сессии к БД
     */
    private static final SessionFactory sessionFactory;

    /**
     * Репозиторий для работы с операциями
     */
    private static final OperationRepository operationRepository;

    /**
     * Сервис для подготовки отчётов
     */
    private static final ReportService reportService;

    /**
     * Обработчик команды "/report_expense"
     */
    private static final CommandHandler reportExpenseHandler;

    /**
     * Репозиторий для работы с пользователем
     */
    private static final TestHibernateUserRepository userRepository;

    /**
     * Репозиторий для работы с катгеориями
     */
    private static final CategoryRepository categoryRepository;

    private final static TransactionManager transactionManager;

    static {
        sessionFactory = new HibernateConfiguration().getSessionFactory();
        transactionManager = new TransactionManager(sessionFactory);
        operationRepository = new HibernateOperationRepository();
        userRepository = new TestHibernateUserRepository(sessionFactory);
        reportService = new ReportService(operationRepository);
        reportExpenseHandler = new ReportExpensesHandler(reportService);
        categoryRepository = new HibernateCategoryRepository();
    }

    /**
     * Метод инициализирует пользователя, его категории и операции для этих категорий
     */
    @BeforeClass
    public static void init() {
        transactionManager.produceTransaction(session -> {
            User user = new User(1L, 100_000);
            userRepository.saveUser(session, user);
            Category taxiCategory, cleanCategory;
            try {
                taxiCategory = categoryRepository.createUserCategory(session, user, CategoryType.EXPENSE, "Такси");
                cleanCategory = categoryRepository.createUserCategory(session, user, CategoryType.EXPENSE, "Химчистка");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            operationRepository.addOperation(session, user, taxiCategory, 100);
            operationRepository.addOperation(session, user, taxiCategory, 200);
            operationRepository.addOperation(session, user, taxiCategory, 300);
            operationRepository.addOperation(session, user, taxiCategory, 400);
            operationRepository.addOperation(session, user, taxiCategory, 500);
            operationRepository.addOperation(session, user, cleanCategory, 300);
            operationRepository.addOperation(session, user, cleanCategory, 500);
            operationRepository.addOperation(session, user, cleanCategory, 700);
            operationRepository.addOperation(session, user, cleanCategory, 900);
        });
    }

    /**
     * Метод проверяет, что отчёт будет корректно подготовлен, при правильной передаче аргументов
     */
    @Test
    public void handleWithCorrectArguments() {
        transactionManager.produceTransaction(session -> {
            User user = userRepository.getUserByTelegramChatId(session, 1L).get();
            MockBot bot = new MockBot();

            YearMonth yearMonth = YearMonth.now();

            List<String> args = List.of("{month}.{year}"
                    .replace("{month}", String.valueOf(yearMonth.getMonth().getValue()))
                    .replace("{year}", String.valueOf(yearMonth.getYear()))
            );
            HandleCommandEvent commandEvent = new HandleCommandEvent(bot, user, "report_expense", args, session);

            reportExpenseHandler.handleCommand(commandEvent);

            MockMessage message = bot.poolMessageQueue();

            Assert.assertEquals(
                    "Подготовил отчёт по вашим расходам за указанный месяц:\nТакси: 1500.0 руб.\nХимчистка: 2400.0 руб.\n",
                    message.text()
            );
        });

    }

    /**
     * Метод проверяет, что будет выведена ошибка в текстовом виде, если не получится спарсить год и месяц
     */
    @Test
    public void handleWithIncorrectArguments() {
        transactionManager.produceTransaction(session -> {
            User user = userRepository.getUserByTelegramChatId(session, 1L).get();
            MockBot bot = new MockBot();
            List<String> args = List.of("11.pp", "pp.2023", "pp.pp", "-11.-2023", "-11.2023", "11.-2023");
            for (String arg : args) {
                HandleCommandEvent commandEvent = new HandleCommandEvent(
                        bot, user, "report_expense", List.of(arg), session
                );
                reportExpenseHandler.handleCommand(commandEvent);
                MockMessage message = bot.poolMessageQueue();
                Assert.assertEquals(
                        """
                                Переданы неверные данные месяца и года.
                                Дата должна быть передана в виде "MM.YYYY", например, "11.2023".""",
                        message.text()
                );
            }
        });
    }

    /**
     * Метод проверяет, что будет выведена ошибка, если передано неверно количество аргументов.
     */
    @Test
    public void handleWithIncorrectArgumentCount() {
        transactionManager.produceTransaction(session -> {
            User user = userRepository.getUserByTelegramChatId(session, 1L).get();
            MockBot bot = new MockBot();
            List<List<String>> argsList = List.of(
                    List.of("11", "2023"),
                    List.of("Ноябрь", "2023"),
                    List.of("-1", "-1"),
                    List.of()
            );
            for (List<String> args : argsList) {
                HandleCommandEvent commandEvent =
                        new HandleCommandEvent(bot, user, "report_expense", args, session);
                reportExpenseHandler.handleCommand(commandEvent);
                MockMessage message = bot.poolMessageQueue();
                Assert.assertEquals(
                        "Команда /report_expense принимает 1 аргумент [mm.yyyy], например \"/report_expense 11.2023\"",
                        message.text()
                );
            }
        });
    }
}
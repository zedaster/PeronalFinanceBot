package ru.naumen.personalfinancebot.handler;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.*;
import ru.naumen.personalfinancebot.bot.MockBot;
import ru.naumen.personalfinancebot.configuration.HibernateConfiguration;
import ru.naumen.personalfinancebot.handler.event.HandleCommandEvent;
import ru.naumen.personalfinancebot.model.Category;
import ru.naumen.personalfinancebot.model.CategoryType;
import ru.naumen.personalfinancebot.model.User;
import ru.naumen.personalfinancebot.repository.TestHibernateCategoryRepository;
import ru.naumen.personalfinancebot.repository.TestHibernateUserRepository;
import ru.naumen.personalfinancebot.repository.TransactionManager;
import ru.naumen.personalfinancebot.repository.category.CategoryRepository;
import ru.naumen.personalfinancebot.repository.operation.HibernateOperationRepository;
import ru.naumen.personalfinancebot.repository.operation.OperationRepository;

import java.util.List;
import java.util.Optional;

/**
 * Тесты для команды добавления категории
 */
public class AddCategoryTest {
    /**
     * Команда для добавления дохода
     */
    private static final String ADD_INCOME_COMMAND = "add_income_category";

    /**
     * Команда для добавления расхода
     */
    private static final String ADD_EXPENSE_COMMAND = "add_expense_category";

    /**
     * Session factory для работы с сессиями в хранилищах
     */
    private static final SessionFactory sessionFactory;

    static {
        sessionFactory = new HibernateConfiguration().getSessionFactory();
    }

    /**
     * Хранилище пользователей
     */
    private final TestHibernateUserRepository userRepository;
    /**
     * Хранилище категорий
     * Данная реализация позволяет сделать полную очистку категорий после тестов
     */
    private final TestHibernateCategoryRepository categoryRepository;
    /**
     * Хранилище операций
     */
    private final OperationRepository operationRepository;
    /**
     * Обработчик операций для бота
     */
    private final FinanceBotHandler botHandler;
    private final TransactionManager transactionManager;
    /**
     * Моковый бот
     */
    private MockBot mockBot;
    /**
     * Тестируемый пользователь
     */
    private User testUser;

    public AddCategoryTest() {
        this.userRepository = new TestHibernateUserRepository(sessionFactory);
        this.categoryRepository = new TestHibernateCategoryRepository(sessionFactory);
        this.operationRepository = new HibernateOperationRepository();
        this.botHandler = new FinanceBotHandler(userRepository, operationRepository, categoryRepository, sessionFactory);
        this.transactionManager = new TransactionManager(sessionFactory);
    }

    /**
     * Очистка стандартных значений и закрытие sessionFactory после выполнения всех тестов в этом классе
     */
    @AfterClass
    public static void finishTests() {
        sessionFactory.close();
    }

    @Before
    public void beforeEachTest() {
        this.mockBot = new MockBot();
        transactionManager.produceTransaction(session -> this.testUser = createTestUser(session,1));
    }

    @After
    public void afterEachTest() {
        categoryRepository.removeAll();
        userRepository.removeAll();
    }

    /**
     * Добавление категории, которая содержит несколько слов
     */
    @Test
    public void addFewWordsAndDbSaving() {
        transactionManager.produceTransaction(session -> {
            final List<String> args = List.of("Коммунальные", "платежи");
            final String categoryName = "Коммунальные платежи";
            final String expectMessage = "Категория расходов 'Коммунальные платежи' успешно добавлена";

            HandleCommandEvent commandEvent = new HandleCommandEvent(
                    this.mockBot, this.testUser, ADD_EXPENSE_COMMAND, args, session);
            this.botHandler.handleCommand(commandEvent);
            Optional<Category> addedCategory = categoryRepository.getCategoryByName(session, this.testUser, CategoryType.EXPENSE,
                    categoryName);
            Assert.assertTrue(addedCategory.isPresent());
            categoryRepository.removeAll();
            Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
            Assert.assertEquals(expectMessage, this.mockBot.poolMessageQueue().text());
        });
    }

    /**
     * Добавление корректных категорий
     */
    @Test
    public void addCorrectCategory() {
        transactionManager.produceTransaction(session -> {
            final List<String> testCases = List.of("Такси", "такси", "тАкСи");
            final String incomeExpectMessage = "Категория доходов 'Такси' успешно добавлена";
            final String expenseExpectMessage = "Категория расходов 'Такси' успешно добавлена";
            final List<String> commands = List.of(ADD_INCOME_COMMAND, ADD_EXPENSE_COMMAND);

            for (int i = 0; i < 2; i++) {
                for (String testCase : testCases) {
                    HandleCommandEvent commandEvent = new HandleCommandEvent(
                            this.mockBot, this.testUser, commands.get(i), List.of(testCase), session);
                    this.botHandler.handleCommand(commandEvent);
                    categoryRepository.removeAll();
                }
            }

            Assert.assertEquals(6, this.mockBot.getMessageQueueSize());
            for (int i = 0; i < 3; i++) {
                Assert.assertEquals(incomeExpectMessage, this.mockBot.poolMessageQueue().text());
            }
            for (int i = 0; i < 3; i++) {
                Assert.assertEquals(expenseExpectMessage, this.mockBot.poolMessageQueue().text());
            }
        });
    }

    /**
     * Добавление слишком большой категории и проверка на то, что она не добавилась
     */
    @Test
    public void addingTooBigCategoryAndDbNotTouched() {
        transactionManager.produceTransaction(session -> {
            final String some65chars = "fdafaresdakbmgernadsvckmbqteafvjickmblearfdsvmxcklrefbeafvdzxcmkf";
            final String expectMessage = "Название категории введено неверно. Оно может содержать от 1 до 64 символов " +
                    "латиницы, кириллицы, цифр, тире и пробелов";

            HandleCommandEvent commandEvent = new HandleCommandEvent(
                    this.mockBot, this.testUser, ADD_INCOME_COMMAND, List.of(some65chars), session);
            this.botHandler.handleCommand(commandEvent);
            Assert.assertTrue(categoryRepository.getCategoryByName(session, this.testUser, CategoryType.INCOME, some65chars).isEmpty());
            categoryRepository.removeAll();
            Assert.assertEquals(1, this.mockBot.getMessageQueueSize());
            Assert.assertEquals(expectMessage, this.mockBot.poolMessageQueue().text());
        });
    }

    /**
     * Добавление некорректных категорий
     */
    @Test
    public void addIncorrectCategory() {
        transactionManager.produceTransaction(session -> {
            final List<String> testCases = List.of(".", "_", "так_неверно", "так,неправильно", "中文");
            final String expectMessage = "Название категории введено неверно. Оно может содержать от 1 до 64 символов " +
                    "латиницы, кириллицы, цифр, тире и пробелов";

            for (String testCase : testCases) {
                HandleCommandEvent commandEvent = new HandleCommandEvent(
                        this.mockBot, this.testUser, ADD_INCOME_COMMAND, List.of(testCase), session);
                this.botHandler.handleCommand(commandEvent);
                categoryRepository.removeAll();
            }
            Assert.assertEquals(5, this.mockBot.getMessageQueueSize());
            for (int i = 0; i < 5; i++) {
                Assert.assertEquals(expectMessage, this.mockBot.poolMessageQueue().text());
            }
        });
    }

    /**
     * Добавление категории на расход и на доход с одним и тем же названием
     */
    @Test
    public void addSameIncomeAndExpense() {
        transactionManager.produceTransaction(session -> {
            final String categoryName = "Такси";
            final String addedIncomeMessage = "Категория доходов 'Такси' успешно добавлена";
            final String addedExpenseMessage = "Категория расходов 'Такси' успешно добавлена";

            final List<String> commandNames = List.of(ADD_INCOME_COMMAND, ADD_EXPENSE_COMMAND);
            final List<CategoryType> types = List.of(CategoryType.INCOME, CategoryType.EXPENSE);
            for (int i = 0; i < 2; i++) {
                HandleCommandEvent command = new HandleCommandEvent(this.mockBot, this.testUser, commandNames.get(i),
                        List.of(categoryName), session);
                botHandler.handleCommand(command);
                Optional<Category> addedCategory = categoryRepository.getCategoryByName(session, this.testUser, types.get(i),
                        categoryName);
                Assert.assertTrue(addedCategory.isPresent());
            }

            Assert.assertEquals(2, this.mockBot.getMessageQueueSize());
            Assert.assertEquals(addedIncomeMessage, this.mockBot.poolMessageQueue().text());
            Assert.assertEquals(addedExpenseMessage, this.mockBot.poolMessageQueue().text());
        });
    }

    /**
     * Проверка неверного количества аргументов для команды
     */
    @Test
    public void spacesOrIncorrectCountOfAddArguments() {
        transactionManager.produceTransaction(session -> {
            List<List<String>> cases = List.of(
                    List.of(),
                    List.of(" "),
                    List.of(" ", " ")
            );
            final String expectMessage = "Данная команда принимает [название категории] в одно или несколько слов.";
            for (List<String> args : cases) {
                HandleCommandEvent command = new HandleCommandEvent(this.mockBot, this.testUser, ADD_INCOME_COMMAND, args, session);
                botHandler.handleCommand(command);
            }
            Assert.assertEquals(3, this.mockBot.getMessageQueueSize());
            for (int i = 0; i < 3; i++) {
                Assert.assertEquals(expectMessage, this.mockBot.poolMessageQueue().text());
            }
        });
    }

    /**
     * Тестирует, что категория добавиться только одному пользователю, а не двум
     */
    @Test
    public void twoUsers() {
        transactionManager.produceTransaction(session -> {
            final String categoryName = "Зарплата";
            User secondUser = createTestUser(session, 2);

            HandleCommandEvent command = new HandleCommandEvent(this.mockBot, this.testUser, ADD_INCOME_COMMAND,
                    List.of(categoryName), session);
            botHandler.handleCommand(command);

            categoryRepository.getCategoryByName(session, this.testUser, CategoryType.INCOME, categoryName); // проверено ранее
            Optional<Category> shouldBeEmptyCategory = categoryRepository
                    .getCategoryByName(session, secondUser, CategoryType.INCOME, categoryName);
            Assert.assertTrue(shouldBeEmptyCategory.isEmpty());
            userRepository.removeUserById(session, testUser.getId());
            userRepository.removeUserById(session, secondUser.getId());
        });
    }

    /**
     * Тестирует, что пользовательская категория, которая существует как стандартная, не будет добавлена.
     */
    @Test
    public void userAndStandardCategorySuppression() throws CategoryRepository.CreatingExistingCategoryException {
        transactionManager.produceTransaction(session -> {
            final CategoryType categoryType = CategoryType.INCOME;
            final String categoryName = "Зарплата";
            final String expectMessage = "Категория 'Зарплата' не должна быть добавлена как пользовательская";

            try {
                categoryRepository.createStandardCategory(session, categoryType, categoryName);
            } catch (CategoryRepository.CreatingExistingStandardCategoryException e) {
                throw new RuntimeException(e);
            }
            HandleCommandEvent command = new HandleCommandEvent(this.mockBot, testUser, ADD_INCOME_COMMAND,
                    List.of(categoryName), session);
            botHandler.handleCommand(command);

            Optional<Category> addedCategory = categoryRepository.getCategoryByName(session, this.testUser, CategoryType.INCOME,
                    categoryName);
            Assert.assertTrue(expectMessage, addedCategory.isPresent());
            Assert.assertTrue(expectMessage, addedCategory.get().isStandard());

        });

    }

    /**
     * Создает пользователя для тестов
     * У него chatId = number, А баланс = number * 100
     */
    private User createTestUser(Session session, int number) {
        User user = new User(number, number * 100);
        userRepository.saveUser(session, user);
        return user;
    }
}

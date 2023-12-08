package ru.naumen.personalfinancebot.handler.command;

import org.hibernate.Session;
import ru.naumen.personalfinancebot.handler.event.HandleCommandEvent;
import ru.naumen.personalfinancebot.message.Message;
import ru.naumen.personalfinancebot.repository.user.UserRepository;
import ru.naumen.personalfinancebot.service.ArgumentParseService;

/**
 * Обработчик команды для установки баланса
 *
 * @author Sergey Kazantsev
 */
public class SetBalanceHandler implements CommandHandler {
    /**
     * Сервис, который парсит аргументы
     */
    private final ArgumentParseService argumentParser;

    /**
     * Хранилище пользователей
     */
    private final UserRepository userRepository;

    public SetBalanceHandler(ArgumentParseService argumentParser, UserRepository userRepository) {
        this.argumentParser = argumentParser;
        this.userRepository = userRepository;
    }

    /**
     * Метод, вызываемый при получении команды
     */
    @Override
    public void handleCommand(HandleCommandEvent event) {
        Session session = event.getSession();
        double amount;
        try {
            amount = argumentParser.parseBalance(event.getArgs());
        } catch (IllegalArgumentException e) {
            event.getBot().sendMessage(event.getUser(),
                    "Команда введена неверно! Введите /set_balance <новый баланс>");
            return;
        }

        event.getUser().setBalance(amount);
        userRepository.saveUser(session, event.getUser());
        event.getBot().sendMessage(event.getUser(), Message.SET_BALANCE_SUCCESSFULLY
                .replace("{balance}", beautifyDouble(amount)));

    }

    /**
     * Форматирует double в красивую строку.
     * Если число целое, то вернет его без дробной части.
     * Т.е. 1000.0 будет выведено как 1000,
     * а 1000.99 будет выведено как 1000.99
     */
    private String beautifyDouble(double d) {
        if ((int) d == d) {
            return String.valueOf((int) d);
        }
        return String.valueOf(d);
    }
}

package ru.naumen.personalfinancebot.service;

import org.hibernate.Session;
import ru.naumen.personalfinancebot.message.Message;
import ru.naumen.personalfinancebot.model.CategoryType;
import ru.naumen.personalfinancebot.model.User;
import ru.naumen.personalfinancebot.repository.operation.OperationRepository;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Класс для подготовки отчётов по расходам
 */
public class ReportService {

    private final OperationRepository operationRepository;

    public ReportService(OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    /**
     * Возвращает Отчёт в виде строки, где содержится сумма затрат на каждую категорию пользователя.
     *
     * @param user Пользователь, для которого надо вернуть отчёт
     * @param args Аргументы, переданные вместе с командой
     * @return "Словарь" с категориями и затратоми
     */
    public String getExpenseReport(Session session, User user, String args) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(args, DateTimeFormatter.ofPattern("MM.yyyy"));
        } catch (Exception exception) {
            return Message.INCORRECT_SELF_REPORT_VALUES;
        }
        Map<String, Double> categoryPaymentMap = this
                .operationRepository
                .getOperationsSumByType(
                        session,
                        user, yearMonth.getMonth().getValue(), yearMonth.getYear(), CategoryType.EXPENSE
                );
        if (categoryPaymentMap == null) {
            return Message.EXPENSES_NOT_EXIST;
        }
        StringBuilder report = new StringBuilder();
        report.append(Message.SELF_REPORT_MESSAGE);

        for (Map.Entry<String, Double> entry : categoryPaymentMap.entrySet()) {
             report.append(Message.EXPENSE_REPORT_PATTERN
                     .replace("{category}", entry.getKey())
                     .replace("{payment}", entry.getValue().toString())
             );
        }
        return report.toString();
    }
}
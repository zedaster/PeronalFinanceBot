package ru.naumen.personalfinancebot.model;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

/**
 * Модель данных "Категория расхода/дохода"
 */
@Entity
@Table(name = "categories")
public class Category {
    /**
     * ID категории
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private long id;

    /**
     * Отношение: Пользователь, который добавил категорию.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    /**
     * Отношение: Операции, связанные с этой категорией
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE)
    private List<Operation> operations;

    /**
     * Название категории.
     */
    @Column(name = "category_name", nullable = false)
    private String categoryName;

    /**
     * Тип категории: Расход / Доход.
     * Enum-значение преобразовывается в число для хранения в БД
     */
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "type", nullable = false)
    private CategoryType type;

    public Category() {

    }

    public Category(User user, String categoryName, CategoryType type) {
        this.user = user;
        this.categoryName = categoryName;
        this.type = type;
    }

    /**
     * @return ID категории
     */
    public long getId() {
        return id;
    }

    /**
     * @return ID пользователя
     */
    public User getUser() {
        return user;
    }

    /**
     * @param user ID пользователя
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * @return Название категории
     */
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * @param categoryName Название категории
     */
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    /**
     * @return Тип категории
     */
    public CategoryType getType() {
        return type;
    }

    /**
     * @param type Тип категории
     */
    public void setType(CategoryType type) {
        this.type = type;
    }

    /**
     * Является ли категория стандартной или нет
     */
    public boolean isStandard() {
        return this.getUser() == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return id == category.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

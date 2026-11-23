package com.asalkartechworks.himmat.expense.repository;

import com.asalkartechworks.himmat.expense.model.ExpenseCategory;
import com.asalkartechworks.himmat.expense.model.ExpenseStatus;
import com.asalkartechworks.himmat.expense.model.OperationalExpense;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OperationalExpenseRepository extends JpaRepository<OperationalExpense, Long>, JpaSpecificationExecutor<OperationalExpense> {

    Optional<OperationalExpense> findByExpenseNumber(String expenseNumber);

    @Query(value = "SELECT nextval('seq_expense_number')", nativeQuery = true)
    Long getNextExpenseSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OperationalExpense e WHERE e.id = :id")
    Optional<OperationalExpense> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM OperationalExpense e " +
           "WHERE e.status = :status AND e.expenseDate >= :startDate AND e.expenseDate <= :endDate")
    BigDecimal sumAmountByStatusAndDateBetween(@Param("status") ExpenseStatus status,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM OperationalExpense e " +
           "WHERE e.status = 'POSTED' AND e.expenseDate >= :startDate AND e.expenseDate <= :endDate " +
           "GROUP BY e.category")
    List<Object[]> sumAmountByCategoryAndDateBetween(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    List<OperationalExpense> findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(
            ExpenseStatus status, LocalDate startDate, LocalDate endDate);
}

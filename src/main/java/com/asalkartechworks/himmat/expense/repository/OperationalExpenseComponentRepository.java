package com.asalkartechworks.himmat.expense.repository;

import com.asalkartechworks.himmat.expense.model.OperationalExpenseComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OperationalExpenseComponentRepository extends JpaRepository<OperationalExpenseComponent, Long> {

    List<OperationalExpenseComponent> findByExpenseId(Long expenseId);

    @Query("SELECT c.method, COALESCE(SUM(c.amount), 0) FROM OperationalExpenseComponent c " +
           "WHERE c.expense.status = 'POSTED' AND c.expense.expenseDate >= :startDate AND c.expense.expenseDate <= :endDate " +
           "GROUP BY c.method")
    List<Object[]> sumAmountByMethodAndDateBetween(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
}

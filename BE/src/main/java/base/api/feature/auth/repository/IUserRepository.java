package base.api.feature.auth.repository;

import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<UserModel, Long>, JpaSpecificationExecutor<UserModel> {

    @Query("SELECT u FROM UserModel u WHERE u.email = :email")
    UserModel findByUserNameAndEmail(String userName, @Param("email") String email);

    Optional<UserModel> findByEmail(String email);

    Optional<UserModel> findByPhone(String phone);

    default Optional<UserModel> findByEmailOrPhone(String identifier) {
        Optional<UserModel> byEmail = findByEmail(identifier);
        return byEmail.isPresent() ? byEmail : findByPhone(identifier);
    }

    @Query("SELECT u FROM UserModel u WHERE LOWER(u.email) = LOWER(:login)")
    Optional<UserModel> findByUserName(@Param("login") String login);

    default boolean existsByUserName(String userName) {
        return userName != null && existsByEmail(userName);
    }

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    default Page<UserModel> findAllByRole(UserRole role, Pageable pageable) {
        return findAllByRoleName(role.name(), pageable);
    }

    @Query("SELECT u FROM UserModel u WHERE u.roleEntity.name = :roleName")
    Page<UserModel> findAllByRoleName(@Param("roleName") String roleName, Pageable pageable);

    default Page<UserModel> findAllByUserNameAndRole(String userName, UserRole role, Pageable pageable) {
        return findAllByEmailContainingIgnoreCaseAndRoleName(userName, role.name(), pageable);
    }

    @Query("SELECT u FROM UserModel u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')) AND u.roleEntity.name = :roleName")
    Page<UserModel> findAllByEmailContainingIgnoreCaseAndRoleName(
            @Param("email") String email,
            @Param("roleName") String roleName,
            Pageable pageable
    );

    @Query("SELECT u FROM UserModel u WHERE LOWER(u.email) = LOWER(:login)")
    Optional<UserModel> findByUserNameOrEmail(@Param("login") String login);

    @Query("SELECT u FROM UserModel u WHERE LOWER(u.email) LIKE LOWER(CONCAT(:userName, '%'))")
    List<UserModel> findByUserNameStartingWith(@Param("userName") String userName);

    @Query("SELECT u FROM UserModel u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :userName, '%'))")
    List<UserModel> findByUserNameContaining(@Param("userName") String userName);

    @Query("SELECT u FROM UserModel u WHERE u.email = :email")
    List<UserModel> getUserEntityBy(@Param("userName") String userName, @Param("email") String email);

    int countByCreatedAtAfter(LocalDateTime after);

    default List<UserModel> findByRole(UserRole role) {
        return findByRoleName(role.name());
    }

    @Query("SELECT u FROM UserModel u WHERE u.roleEntity.name = :roleName")
    List<UserModel> findByRoleName(@Param("roleName") String roleName);

    /**
     * POS quick search: partial match on phone or name only (not email).
     * Cashier thường chỉ nhớ vài số cuối nên không thể bắt gõ đủ SĐT.
     */
    @Query("""
            SELECT u FROM UserModel u
            WHERE u.roleEntity.name = 'CUSTOMER'
              AND (u.phone LIKE CONCAT('%', :keyword, '%')
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY u.fullName ASC
            """)
    List<UserModel> searchCustomers(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT u FROM UserModel u WHERE u.branchId = :branchId AND u.roleEntity.name = :roleName AND LOWER(u.status) = 'active'")
    List<UserModel> findByBranchIdAndRoleName(
            @Param("branchId") Long branchId,
            @Param("roleName") String roleName
    );

    @Query("SELECT u FROM UserModel u WHERE u.branchId = :branchId AND u.roleEntity.name IN :roleNames AND LOWER(u.status) = 'active'")
    List<UserModel> findByBranchIdAndRoleNames(
            @Param("branchId") Long branchId,
            @Param("roleNames") Collection<String> roleNames
    );

    @Query("""
            SELECT COUNT(u) FROM UserModel u
            WHERE u.roleEntity.name = :roleName AND LOWER(u.status) = 'active'
            """)
    long countActiveByRoleName(@Param("roleName") String roleName);

    @Query("""
            SELECT COUNT(u) FROM UserModel u
            WHERE u.roleEntity.name = :roleName AND LOWER(u.status) = 'active' AND u.id <> :excludeUserId
            """)
    long countActiveByRoleNameExcluding(
            @Param("roleName") String roleName,
            @Param("excludeUserId") Long excludeUserId
    );

    @Query("""
            SELECT COUNT(u) FROM UserModel u
            WHERE LOWER(u.status) = LOWER(:status) AND u.roleEntity.name <> 'CUSTOMER'
            """)
    long countWebUsersByStatus(@Param("status") String status);

    @Query("""
            SELECT u.roleEntity.name, COUNT(u) FROM UserModel u
            WHERE u.roleEntity.name <> 'CUSTOMER'
            GROUP BY u.roleEntity.name
            ORDER BY u.roleEntity.name
            """)
    List<Object[]> countGroupedByRoleExcludingCustomer();

    @Query("""
            SELECT u FROM UserModel u
            WHERE u.branchId IS NULL
              AND u.roleEntity.name IN :roleNames
            ORDER BY u.fullName ASC
            """)
    List<UserModel> findMissingBranch(@Param("roleNames") Collection<String> roleNames);

    @Query("""
            SELECT COUNT(u) FROM UserModel u
            WHERE u.branchId = :branchId AND u.roleEntity.name <> 'CUSTOMER'
            """)
    long countStaffByBranchId(@Param("branchId") Long branchId);

    @Query("""
            SELECT COUNT(u) FROM UserModel u
            WHERE u.branchId = :branchId
              AND u.roleEntity.name = 'BRANCH_MANAGER'
              AND LOWER(u.status) = 'active'
            """)
    long countActiveBranchManagers(@Param("branchId") Long branchId);

    @Query("""
            SELECT u FROM UserModel u
            WHERE u.branchId = :branchId
              AND u.roleEntity.name = 'BRANCH_MANAGER'
              AND LOWER(u.status) = 'active'
            ORDER BY u.fullName ASC
            """)
    List<UserModel> findActiveBranchManagers(@Param("branchId") Long branchId);

    /**
     * Trừ điểm atomic — chỉ thành công nếu user còn đủ điểm.
     * Trả về số row được update (0 = không đủ điểm).
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserModel u SET u.points = u.points - :points WHERE u.id = :userId AND u.points >= :points")
    int deductPointsAtomic(@Param("userId") Long userId, @Param("points") Long points);

    /**
     * Hoàn điểm (cộng) — dùng khi tích điểm từ hóa đơn hoặc hoàn điểm khi hủy.
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserModel u SET u.points = u.points + :points WHERE u.id = :userId")
    int refundPointsAtomic(@Param("userId") Long userId, @Param("points") Long points);

}

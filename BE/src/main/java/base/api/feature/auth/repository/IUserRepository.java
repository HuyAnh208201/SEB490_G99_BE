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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<UserModel, Long>, JpaSpecificationExecutor<UserModel> {

    UserModel findByUserNameAndEmail(String userName, String Email);

    Optional<UserModel> findByEmail(String email);

    Optional<UserModel> findByPhone(String phone);

    @Query("SELECT u FROM UserModel u WHERE u.userName = :login OR u.email = :login")
    Optional<UserModel> findByUserName(@Param("login") String login);
    boolean existsByUserName(String userName);
    boolean existsByEmail(String email);
    Page<UserModel> findAllByRole(UserRole role, Pageable pageable);
    Page<UserModel> findAllByUserNameAndRole(String userName, UserRole role, Pageable pageable);

    @Query("SELECT u FROM UserModel u WHERE u.userName = :login OR u.email = :login")
    Optional<UserModel> findByUserNameOrEmail(@Param("login") String login);

    List<UserModel> findByUserNameStartingWith(String userName);

    List<UserModel> findByUserNameContaining(String userName);

    @Query("SELECT u FROM UserModel u WHERE u.userName = ?1 AND u.email = ?2")
    List<UserModel> getUserEntityBy(String userName, String email);

    int countByCreatedAtAfter(LocalDateTime after);

    List<UserModel> findByRole(UserRole role);

    /**
     * Trừ điểm atomic — chỉ thành công nếu user còn đủ điểm. Tránh race giữa các
     * request đồng thời cùng chi tiêu điểm. Trả về số row được update (0 = không đủ điểm).
     */
    @Modifying
    @Query("UPDATE UserModel u SET u.points = u.points - :points WHERE u.id = :userId AND u.points >= :points")
    int deductPointsAtomic(@Param("userId") Long userId, @Param("points") Long points);

    /**
     * Hoàn điểm (cộng) khi hủy booking trước khi thanh toán thành công.
     */
    @Modifying
    @Query("UPDATE UserModel u SET u.points = u.points + :points WHERE u.id = :userId")
    int refundPointsAtomic(@Param("userId") Long userId, @Param("points") Long points);

}
